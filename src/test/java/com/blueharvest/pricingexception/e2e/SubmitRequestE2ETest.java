package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

import io.restassured.response.Response;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

	private static String body(int discountBps, String reason) {
		return "{\"discountBps\":" + discountBps + ",\"reason\":\"" + reason + "\"}";
	}

}
