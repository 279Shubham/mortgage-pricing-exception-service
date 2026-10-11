package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.response.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SubmitRequestE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String VALID_BODY = body(25, VALID_REASON);

	@Test
	@DisplayName("US-02 AC-1: an RM submits a discount and gets 201 with a Location to the pending request")
	void rmSubmitsAndAnyInternalUserReadsItBack() {
		submit("rm-anna", "MA-1001", newIdempotencyKey(), VALID_BODY)
			.then().statusCode(201)
			.header("Location", matchesPattern("^/pricing-exceptions/[0-9a-f-]{36}$"))
			.body("status", equalTo("PENDING"))
			.body("currentRevision.number", equalTo(1))
			.body("currentRevision.discountBps", equalTo(25))
			.body("currentRevision.reason", equalTo(VALID_REASON))
			.body("requestedBy", equalTo("rm-anna"))
			.body("applicationId", equalTo("MA-1001"));
	}

	@ParameterizedTest(name = "US-02 AC-1: {0} reads the request back")
	@ValueSource(strings = { "rm-anna", "rm-carl", "reviewer-ben", "dual-erin", "application-process" })
	@DisplayName("US-02 AC-1: any known caller reads the request back")
	void anyKnownCallerReadsRequest(String userId) {
		Response created = submit("rm-anna", "MA-1001", newIdempotencyKey(), VALID_BODY);
		String id = created.path("id");

		given()
			.header("X-User-Id", userId)
			.when().get(created.header("Location"))
			.then().statusCode(200)
			.body("id", equalTo(id))
			.body("applicationId", equalTo("MA-1001"))
			.body("status", equalTo("PENDING"))
			.body("requestedBy", equalTo("rm-anna"))
			.body("submittedAt", equalTo("2026-01-15T09:00:00Z"))
			.body("currentRevision.number", equalTo(1))
			.body("currentRevision.discountBps", equalTo(25))
			.body("currentRevision.reason", equalTo(VALID_REASON));
	}

	@Test
	@DisplayName("US-02 AC-1: the submission time comes from the injected clock")
	void submissionTimeComesFromTheClock() {
		submit("rm-anna", "MA-1001", newIdempotencyKey(), VALID_BODY)
			.then().statusCode(201)
			.body("submittedAt", equalTo("2026-01-15T09:00:00Z"))
			.body("currentRevision.createdAt", equalTo("2026-01-15T09:00:00Z"));
	}

	@Test
	@DisplayName("US-02 AC-1: the submission time follows the injected clock when it moves on")
	void submissionTimeFollowsAdvancedClock() {
		CLOCK.advance(Duration.ofHours(1));

		submit("rm-anna", "MA-1001", newIdempotencyKey(), VALID_BODY)
			.then().statusCode(201)
			.body("submittedAt", equalTo("2026-01-15T10:00:00Z"))
			.body("currentRevision.createdAt", equalTo("2026-01-15T10:00:00Z"));
	}

	@Test
	@DisplayName("US-02 AC-1: an unknown request id is a 404 Problem Details response")
	void unknownRequestIsNotFound() {
		given()
			.header("X-User-Id", "rm-anna")
			.when().get("/pricing-exceptions/" + UUID.randomUUID())
			.then().statusCode(404)
			.contentType("application/problem+json")
			.body("status", equalTo(404));
	}

	@Test
	@DisplayName("US-02 AC-1: a malformed request id is a 400 Problem Details response")
	void malformedRequestIdIsBadRequest() {
		given()
			.header("X-User-Id", "rm-anna")
			.when().get("/pricing-exceptions/not-a-uuid")
			.then().statusCode(400)
			.contentType("application/problem+json")
			.body("status", equalTo(400));
	}

	@ParameterizedTest(name = "US-02 AC-3: {0} gets 403")
	@ValueSource(strings = { "reviewer-ben", "reviewer-dana", "application-process" })
	@DisplayName("US-02 AC-3: a caller without the RM role cannot submit")
	void onlyAnRmMaySubmit(String userId) {
		submit(userId, "MA-1001", newIdempotencyKey(), VALID_BODY)
			.then().statusCode(403)
			.contentType("application/problem+json")
			.body("status", equalTo(403));
	}

	@Test
	@DisplayName("US-02 AC-2: an unknown application is a 404 Problem Details response")
	void unknownApplicationIsNotFound() {
		submit("rm-anna", "NOPE", newIdempotencyKey(), VALID_BODY)
			.then().statusCode(404)
			.contentType("application/problem+json")
			.body("status", equalTo(404))
			.body("detail", equalTo("Unknown mortgage application NOPE"));
		submit("rm-anna", "X".repeat(20), newIdempotencyKey(), VALID_BODY)
			.then().statusCode(404)
			.contentType("application/problem+json");
	}

	@Test
	@DisplayName("US-02 AC-2: an over-long application id is rejected with 400 before the caller's role is checked")
	void overLongApplicationIdIsRejectedBeforeTheRoleCheck() {
		submit("reviewer-ben", "X".repeat(200), newIdempotencyKey(), VALID_BODY)
			.then().statusCode(400)
			.contentType("application/problem+json")
			.body("status", equalTo(400));
	}

	@ParameterizedTest(name = "US-02 AC-2: {0} is rejected with 400")
	@MethodSource("invalidSubmissions")
	@DisplayName("US-02 AC-2: an invalid submission is rejected with 400")
	void invalidSubmissionsAreRejected(String label, String applicationId, String body) {
		submit("rm-anna", applicationId, newIdempotencyKey(), body)
			.then().statusCode(400)
			.contentType("application/problem+json")
			.body("status", equalTo(400));
	}

	static Stream<Arguments> invalidSubmissions() {
		String reason = "\"reason\":\"" + VALID_REASON + "\"";
		return Stream.of(
				Arguments.of("discountBps 25.9", "MA-1001", "{\"discountBps\":25.9," + reason + "}"),
				Arguments.of("discountBps 25.0", "MA-1001", "{\"discountBps\":25.0," + reason + "}"),
				Arguments.of("discountBps \"25\" as a string", "MA-1001", "{\"discountBps\":\"25\"," + reason + "}"),
				Arguments.of("discountBps 99999999999", "MA-1001", "{\"discountBps\":99999999999," + reason + "}"),
				Arguments.of("discountBps 0", "MA-1001", body(0, VALID_REASON)),
				Arguments.of("discountBps -5", "MA-1001", body(-5, VALID_REASON)),
				Arguments.of("discountBps 201", "MA-1001", body(201, VALID_REASON)),
				Arguments.of("discountBps 150 on MA-1006 (its standard rate)", "MA-1006", body(150, VALID_REASON)),
				Arguments.of("discountBps 151 on MA-1006", "MA-1006", body(151, VALID_REASON)),
				Arguments.of("a missing discountBps", "MA-1001", "{" + reason + "}"),
				Arguments.of("a null discountBps", "MA-1001", "{\"discountBps\":null," + reason + "}"),
				Arguments.of("a missing reason", "MA-1001", "{\"discountBps\":25}"),
				Arguments.of("a reason of ten spaces", "MA-1001", body(25, " ".repeat(10))),
				Arguments.of("a 9-character reason", "MA-1001", body(25, "x".repeat(9))),
				Arguments.of("a 1001-character reason", "MA-1001", body(25, "x".repeat(1001))),
				Arguments.of("an unknown extra field", "MA-1001", "{\"discountBps\":25," + reason + ",\"unexpected\":1}"),
				Arguments.of("a client-supplied requestedBy", "MA-1001",
						"{\"discountBps\":25," + reason + ",\"requestedBy\":\"rm-carl\"}"),
				Arguments.of("a client-supplied status", "MA-1001",
						"{\"discountBps\":25," + reason + ",\"status\":\"APPROVED\"}"),
				Arguments.of("a 21-character application id", "X".repeat(21), VALID_BODY),
				Arguments.of("a 200-character application id", "X".repeat(200), VALID_BODY),
				Arguments.of("an empty body", "MA-1001", ""),
				Arguments.of("malformed JSON", "MA-1001", "{"));
	}

	@ParameterizedTest(name = "US-02 AC-2: {0} is accepted")
	@MethodSource("boundaryValues")
	@DisplayName("US-02 AC-2: values on the exact boundaries are accepted")
	void boundaryValuesAreAccepted(String label, String applicationId, int discountBps, String reason) {
		Response created = submit("rm-anna", applicationId, newIdempotencyKey(), body(discountBps, reason));
		created.then().statusCode(201);

		given()
			.header("X-User-Id", "rm-anna")
			.when().get(created.header("Location"))
			.then().statusCode(200)
			.body("currentRevision.discountBps", equalTo(discountBps))
			.body("currentRevision.reason", equalTo(reason));
	}

	static Stream<Arguments> boundaryValues() {
		return Stream.of(
				Arguments.of("1 bps with a 10-character reason", "MA-1001", 1, "x".repeat(10)),
				Arguments.of("200 bps with a 1000-character reason", "MA-1002", 200, "x".repeat(1000)),
				Arguments.of("149 bps on MA-1006", "MA-1006", 149, VALID_REASON),
				Arguments.of("a reason of ten euro signs", "MA-1003", 25, "\u20ac".repeat(10)));
	}

	@Test
	@DisplayName("US-02 AC-2: a rejected submission creates nothing")
	void rejectedSubmissionCreatesNothing() {
		submit("rm-anna", "MA-1006", newIdempotencyKey(), body(150, VALID_REASON))
			.then().statusCode(400);
		submit("reviewer-ben", "MA-1006", newIdempotencyKey(), body(100, VALID_REASON))
			.then().statusCode(403);

		submit("rm-anna", "MA-1006", newIdempotencyKey(), body(100, VALID_REASON))
			.then().statusCode(201);
	}

	@Test
	@DisplayName("US-02 AC-4: a second pending request for the same application gets 409")
	void secondPendingRequestIsConflict() {
		submit("rm-anna", "MA-1001", newIdempotencyKey(), VALID_BODY)
			.then().statusCode(201);

		submit("rm-carl", "MA-1001", newIdempotencyKey(), VALID_BODY)
			.then().statusCode(409)
			.contentType("application/problem+json")
			.body("status", equalTo(409))
			.body("detail", equalTo("Application MA-1001 already has a pending request"))
			.body("trace", nullValue())
			.body("exception", nullValue());
		submit("rm-carl", "MA-1002", newIdempotencyKey(), VALID_BODY)
			.then().statusCode(201);
	}

	@Test
	@DisplayName("US-02 AC-4: five racing submits leave exactly one pending request")
	void racingSubmitsLeaveExactlyOnePending() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(5);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Integer>> calls = IntStream.range(0, 5)
				.mapToObj(i -> pool.submit(() -> {
					start.await();
					return submit("rm-anna", "MA-1001", "race-" + i, VALID_BODY).statusCode();
				}))
				.toList();
			start.countDown();

			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> call : calls) {
				statuses.add(call.get());
			}
			assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409, 409, 409);
		}
		finally {
			pool.shutdown();
		}
	}

	private static String body(int discountBps, String reason) {
		return "{\"discountBps\":" + discountBps + ",\"reason\":\"" + reason + "\"}";
	}

}
