package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.response.ValidatableResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovedDiscountE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String REVISION_ONE = "{\"revisionNumber\":1}";

	private static final String DECLINE_NOTE = "Discount not justified by the quote";

	private static final String DECLINE_BODY = "{\"revisionNumber\":1,\"note\":\"" + DECLINE_NOTE + "\"}";

	@Test
	@DisplayName("US-05 AC-1: the application process reads the latest approval for an application")
	void applicationProcessReadsLatestApproval() {
		String id = submitted("rm-anna", "MA-1001", 25);
		CLOCK.advance(Duration.ofHours(1));
		approve("reviewer-ben", id);

		approvedDiscounts("application-process", "MA-1001")
			.statusCode(200)
			.body("$", hasSize(1))
			.body("[0].requestId", equalTo(id))
			.body("[0].revision", equalTo(1))
			.body("[0].discountBps", equalTo(25))
			.body("[0].approvedBy", equalTo("reviewer-ben"))
			.body("[0].decidedAt", equalTo("2026-01-15T10:00:00Z"));
	}

	@Test
	@DisplayName("US-05 AC-1: any internal user reads the same approval")
	void anyInternalUserReadsSameApproval() {
		String id = submitted("rm-anna", "MA-1001", 25);
		approve("reviewer-ben", id);
		String asApplicationProcess = approvedDiscounts("application-process", "MA-1001")
			.statusCode(200)
			.body("$", hasSize(1))
			.body("[0].requestId", equalTo(id))
			.extract().asString();

		approvedDiscounts("rm-anna", "MA-1001").statusCode(200).body(equalTo(asApplicationProcess));
		approvedDiscounts("reviewer-dana", "MA-1001").statusCode(200).body(equalTo(asApplicationProcess));
	}

	@Test
	@DisplayName("US-05 AC-1: the discount comes from the approved revision, not from a later revision")
	void discountComesFromApprovedRevision() {
		String id = submitted("rm-anna", "MA-1001", 25);
		approve("reviewer-ben", id);
		reviseInDatabase(id, 2, 60);
		read(id)
			.body("currentRevision.number", equalTo(2))
			.body("currentRevision.discountBps", equalTo(60))
			.body("decision.revisionNumber", equalTo(1));

		approvedDiscounts("application-process", "MA-1001")
			.statusCode(200)
			.body("$", hasSize(1))
			.body("[0].requestId", equalTo(id))
			.body("[0].revision", equalTo(1))
			.body("[0].discountBps", equalTo(25))
			.body("[0].approvedBy", equalTo("reviewer-ben"));
	}

	@Test
	@DisplayName("US-05 AC-2: an application without an approval gets an empty list")
	void applicationWithoutApprovalGetsEmptyList() {
		approvedDiscounts("application-process", "MA-1002").statusCode(200).body("$", empty());
		approvedDiscounts("application-process", "MA-9999").statusCode(200).body("$", empty());

		String id = submitted("rm-anna", "MA-1001", 25);
		approvedDiscounts("application-process", "MA-1001").statusCode(200).body("$", empty());

		decline("reviewer-ben", id);
		approvedDiscounts("application-process", "MA-1001").statusCode(200).body("$", empty());
	}

	@Test
	@DisplayName("US-05 AC-3: a new request after an approval leaves it effective until the new one is approved")
	void newerApprovalWinsOnlyOnceApproved() {
		String first = submitted("rm-anna", "MA-1001", 25);
		approve("reviewer-ben", first);

		CLOCK.advance(Duration.ofHours(1));
		String second = submit("rm-anna", "MA-1001", newIdempotencyKey(), body(40))
			.then().statusCode(201)
			.body("id", not(equalTo(first)))
			.body("status", equalTo("PENDING"))
			.body("previousRequestId", nullValue())
			.extract().path("id");

		approvedDiscounts("application-process", "MA-1001")
			.statusCode(200)
			.body("$", hasSize(1))
			.body("[0].requestId", equalTo(first))
			.body("[0].discountBps", equalTo(25))
			.body("[0].approvedBy", equalTo("reviewer-ben"));

		CLOCK.advance(Duration.ofHours(1));
		approve("reviewer-dana", second);

		approvedDiscounts("application-process", "MA-1001")
			.statusCode(200)
			.body("$", hasSize(1))
			.body("[0].requestId", equalTo(second))
			.body("[0].revision", equalTo(1))
			.body("[0].discountBps", equalTo(40))
			.body("[0].approvedBy", equalTo("reviewer-dana"))
			.body("[0].decidedAt", equalTo("2026-01-15T11:00:00Z"));
		read(first).body("status", equalTo("APPROVED")).body("decision.decidedBy", equalTo("reviewer-ben"));
	}

	@Test
	@DisplayName("US-05 AC-4: a resubmit after a decline is a new request linked by previousRequestId")
	void resubmitAfterDeclineIsLinkedToDeclinedRequest() {
		String declined = submitted("rm-anna", "MA-1001", 25);
		read(declined).body("previousRequestId", nullValue());
		decline("reviewer-ben", declined);

		CLOCK.advance(Duration.ofHours(1));
		String resubmitted = submit("rm-anna", "MA-1001", newIdempotencyKey(), body(20))
			.then().statusCode(201)
			.body("id", not(equalTo(declined)))
			.body("status", equalTo("PENDING"))
			.body("previousRequestId", equalTo(declined))
			.extract().path("id");

		read(resubmitted).body("status", equalTo("PENDING")).body("previousRequestId", equalTo(declined));
		read(declined)
			.body("status", equalTo("DECLINED"))
			.body("decision.decidedBy", equalTo("reviewer-ben"))
			.body("decision.note", equalTo(DECLINE_NOTE))
			.body("$", hasKey("previousRequestId"))
			.body("previousRequestId", nullValue());
		submit("rm-anna", "MA-1002", newIdempotencyKey(), body(20))
			.then().statusCode(201)
			.body("previousRequestId", nullValue());
	}

	private static String submitted(String userId, String applicationId, int discountBps) {
		return submit(userId, applicationId, newIdempotencyKey(), body(discountBps))
			.then().statusCode(201)
			.extract().path("id");
	}

	private static void approve(String userId, String id) {
		decide(userId, id, "approve", newIdempotencyKey(), REVISION_ONE).then().statusCode(200);
	}

	private static void decline(String userId, String id) {
		decide(userId, id, "decline", newIdempotencyKey(), DECLINE_BODY).then().statusCode(200);
	}

	private static ValidatableResponse read(String id) {
		return given()
			.header("X-User-Id", "rm-anna")
			.when().get("/pricing-exceptions/{id}", id)
			.then().statusCode(200);
	}

	private static ValidatableResponse approvedDiscounts(String userId, String applicationId) {
		return given()
			.header("X-User-Id", userId)
			.when().get("/applications/{applicationId}/approved-discounts", applicationId)
			.then();
	}

	private static String body(int discountBps) {
		return "{\"discountBps\":" + discountBps + ",\"reason\":\"" + VALID_REASON + "\"}";
	}

}
