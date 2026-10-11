package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.response.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RetrySafelyE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String VALID_BODY = body(25, VALID_REASON);

	@Autowired
	DataSource dataSource;

	@Test
	@DisplayName("US-03 AC-1: a retry with the same key and payload replays the original status, body and Location")
	void retryReplaysOriginalResponse() {
		String key = newIdempotencyKey();
		Response original = submit("rm-anna", "MA-1001", key, VALID_BODY);
		original.then().statusCode(201);

		String reorderedAndSpaced = "{ \"reason\" : \"" + VALID_REASON + "\",  \"discountBps\" : 25 }";
		Response retry = submit("rm-anna", "MA-1001", key, reorderedAndSpaced);

		retry.then().statusCode(201).header("Location", equalTo(original.header("Location")));
		assertThat(retry.asString()).isEqualTo(original.asString());
		given().header("X-User-Id", "rm-anna").when().get(original.header("Location")).then().statusCode(200);
		submit("rm-anna", "MA-1001", newIdempotencyKey(), VALID_BODY).then().statusCode(409);
	}

	@Test
	@DisplayName("US-03 AC-2: the same key with a different payload gets 422")
	void sameKeyDifferentPayloadIsUnprocessable() {
		String key = newIdempotencyKey();
		Response original = submit("rm-anna", "MA-1001", key, VALID_BODY);
		original.then().statusCode(201);

		submit("rm-anna", "MA-1001", key, body(30, VALID_REASON))
			.then().statusCode(422)
			.contentType("application/problem+json")
			.body("title", equalTo("Unprocessable Content"));
		submit("rm-anna", "MA-1001", key, body(25, "A different reason for the same discount"))
			.then().statusCode(422)
			.contentType("application/problem+json");
		submit("rm-anna", "MA-1001", key, VALID_BODY)
			.then().statusCode(201)
			.header("Location", equalTo(original.header("Location")));
	}

	@Test
	@DisplayName("US-03 AC-4: a retry while the original is still in flight gets 409")
	void retryWhileInFlightIsConflict() throws Exception {
		String key = newIdempotencyKey();
		try (Connection original = dataSource.getConnection()) {
			original.setAutoCommit(false);
			try {
				holdUncommittedClaim(original, key);
				submit("rm-anna", "MA-1001", key, VALID_BODY)
					.then().statusCode(409)
					.contentType("application/problem+json");
			}
			finally {
				original.rollback();
			}
		}
		submit("rm-anna", "MA-1001", key, VALID_BODY).then().statusCode(201);
	}

	@Test
	@DisplayName("US-03 AC-4: five concurrent identical retries create one request")
	void concurrentRetriesCreateOneRequest() throws Exception {
		String key = newIdempotencyKey();
		ExecutorService pool = Executors.newFixedThreadPool(5);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Response>> calls = IntStream.range(0, 5)
				.mapToObj(i -> pool.submit(() -> {
					start.await();
					return submit("rm-anna", "MA-1001", key, VALID_BODY);
				}))
				.toList();
			start.countDown();

			Set<String> locations = new HashSet<>();
			for (Future<Response> call : calls) {
				Response response = call.get();
				assertThat(response.statusCode()).isEqualTo(201);
				locations.add(response.header("Location"));
			}
			assertThat(locations).hasSize(1);
			given().header("X-User-Id", "rm-anna").when().get(locations.iterator().next()).then().statusCode(200);
			submit("rm-anna", "MA-1001", newIdempotencyKey(), VALID_BODY).then().statusCode(409);
		}
		finally {
			pool.shutdown();
		}
	}

	private static void holdUncommittedClaim(Connection connection, String key) throws Exception {
		try (PreparedStatement claim = connection.prepareStatement(
				"INSERT INTO idempotency_record (id, user_id, route, idempotency_key, fingerprint, expires_at)"
						+ " VALUES (RANDOM_UUID(), 'rm-anna', 'POST /applications/MA-1001/pricing-exceptions', ?,"
						+ " 'in-flight', TIMESTAMP WITH TIME ZONE '2099-01-01 00:00:00+00')")) {
			claim.setString(1, key);
			claim.executeUpdate();
		}
	}

	private static String body(int discountBps, String reason) {
		return "{\"discountBps\":" + discountBps + ",\"reason\":\"" + reason + "\"}";
	}

}
