package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class RetrySafelyE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String VALID_BODY = body(25, VALID_REASON);

	private static final String ONE_PENDING_DETAIL = "Application MA-1001 already has a pending request";

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
		assertThat(historyEvents()).isEqualTo(1);
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
	@DisplayName("US-03 AC-4: a key can be retried after a failed attempt")
	void keyCanBeRetriedAfterFailedAttempt() {
		String key = newIdempotencyKey();
		submit("rm-anna", "MA-1001", key, body(500, VALID_REASON)).then().statusCode(400);
		submit("rm-anna", "MA-1001", key, VALID_BODY).then().statusCode(201);

		String refusedKey = newIdempotencyKey();
		submit("reviewer-ben", "MA-1002", refusedKey, VALID_BODY).then().statusCode(403);
		submit("reviewer-ben", "MA-1002", refusedKey, VALID_BODY).then().statusCode(403);
	}

	@Test
	@DisplayName("US-03 AC-4: a key claim that fails for any reason but a duplicate is a 500, not an in-flight 409")
	void failedClaimIsNotReportedAsInFlight() {
		JdbcClient.create(dataSource)
			.sql("ALTER TABLE idempotency_record ALTER COLUMN route SET DATA TYPE VARCHAR(10)")
			.update();

		submit("rm-anna", "MA-1001", newIdempotencyKey(), VALID_BODY).then().statusCode(500);
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

	@ParameterizedTest(name = "US-03 AC-3: a {0} Idempotency-Key gets 400")
	@MethodSource("unusableKeys")
	@DisplayName("US-03 AC-3: a submit without a usable Idempotency-Key gets 400")
	void postWithoutUsableKeyIsBadRequest(String label, String key) {
		RequestSpecification request = given()
			.header("X-User-Id", "rm-anna")
			.contentType("application/json; charset=UTF-8")
			.body(VALID_BODY);
		if (key != null) {
			request.header("Idempotency-Key", key);
		}
		request.when().post("/applications/{applicationId}/pricing-exceptions", "MA-1001")
			.then().statusCode(400)
			.contentType("application/problem+json");
	}

	@Test
	@DisplayName("US-03 AC-3: a 255-character Idempotency-Key is accepted")
	void keyAtMaximumLengthIsAccepted() {
		submit("rm-anna", "MA-1001", "k".repeat(255), VALID_BODY).then().statusCode(201);
	}

	@Test
	@DisplayName("US-03 AC-3: keys are used verbatim, so a key that differs only by case is a different key")
	void keysAreUsedVerbatim() {
		submit("rm-anna", "MA-1001", "Key-A", VALID_BODY).then().statusCode(201);

		submit("rm-anna", "MA-1001", "key-a", body(30, VALID_REASON))
			.then().statusCode(409)
			.contentType("application/problem+json")
			.body("detail", equalTo(ONE_PENDING_DETAIL));
	}

	@Test
	@DisplayName("US-03 AC-5: the same key from another user or on another route is independent")
	void keyIsScopedToUserAndRoute() {
		String key = newIdempotencyKey();
		Response anna = submit("rm-anna", "MA-1001", key, VALID_BODY);
		anna.then().statusCode(201);
		String annasId = anna.path("id");

		Response carl = submit("rm-carl", "MA-1001", key, VALID_BODY);
		carl.then()
			.statusCode(409)
			.contentType("application/problem+json")
			.body("detail", equalTo(ONE_PENDING_DETAIL));
		assertThat(carl.asString()).doesNotContain(annasId);

		submit("rm-anna", "MA-1002", key, VALID_BODY)
			.then().statusCode(201)
			.header("Location", not(equalTo(anna.header("Location"))));
	}

	@Test
	@DisplayName("US-03 AC-5: a key can be reused once the 24h TTL has passed")
	void keyExpiresAfterTtl() {
		String key = newIdempotencyKey();
		submit("rm-anna", "MA-1001", key, VALID_BODY).then().statusCode(201);

		CLOCK.advance(Duration.ofHours(24).minusSeconds(1));
		submit("rm-anna", "MA-1001", key, body(30, VALID_REASON)).then().statusCode(422);

		CLOCK.advance(Duration.ofSeconds(1));
		submit("rm-anna", "MA-1001", key, VALID_BODY)
			.then().statusCode(409)
			.contentType("application/problem+json")
			.body("detail", equalTo(ONE_PENDING_DETAIL));
	}

	@Test
	@DisplayName("US-03 AC-5: after the 24h TTL a reused key creates a new request and replays that one")
	void reusedKeyAfterTtlCreatesNewRequest() {
		String key = newIdempotencyKey();
		Response original = submit("rm-anna", "MA-1001", key, VALID_BODY);
		original.then().statusCode(201);
		JdbcClient.create(dataSource).sql("UPDATE pricing_exception_request SET status = 'WITHDRAWN'").update();

		CLOCK.advance(Duration.ofHours(24));
		Response reused = submit("rm-anna", "MA-1001", key, body(30, VALID_REASON));

		reused.then().statusCode(201).header("Location", not(equalTo(original.header("Location"))));
		submit("rm-anna", "MA-1001", key, body(30, VALID_REASON))
			.then().statusCode(201)
			.header("Location", equalTo(reused.header("Location")));
	}

	static Stream<Arguments> unusableKeys() {
		return Stream.of(
				Arguments.of("missing", null),
				Arguments.of("empty", ""),
				Arguments.of("blank", "   "),
				Arguments.of("256-character", "k".repeat(256)));
	}

	private long historyEvents() {
		return JdbcClient.create(dataSource).sql("SELECT COUNT(*) FROM request_event").query(Long.class).single();
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
