package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.restassured.response.ValidatableResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovedDiscountE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String REVISION_ONE = "{\"revisionNumber\":1}";

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

	private static String submitted(String userId, String applicationId, int discountBps) {
		return submit(userId, applicationId, newIdempotencyKey(), body(discountBps))
			.then().statusCode(201)
			.extract().path("id");
	}

	private static void approve(String userId, String id) {
		decide(userId, id, "approve", newIdempotencyKey(), REVISION_ONE).then().statusCode(200);
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
