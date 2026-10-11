package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = { "pricing.advice.high-discount-bps=20", "pricing.advice.min-reason-length=60" })
class AdviceThresholdsE2ETest extends E2ETestBase {

	private static final String REASON_OF_43_CHARACTERS = "Retention offer matching a competitor quote";

	@Test
	@DisplayName("US-04 AC-2: changing the configured thresholds changes the advice")
	void configuredThresholdsChangeTheAdvice() {
		String body = "{\"discountBps\":25,\"reason\":\"" + REASON_OF_43_CHARACTERS + "\"}";

		submit("rm-anna", "MA-1001", newIdempotencyKey(), body)
			.then().statusCode(201)
			.body("currentRevision.advice.recommendation", equalTo("REVIEW_CAREFULLY"))
			.body("currentRevision.advice.rationale", allOf(containsString("20"), containsString("60")));

		given()
			.header("X-User-Id", "reviewer-ben")
			.queryParam("status", "PENDING")
			.when().get("/pricing-exceptions")
			.then().statusCode(200)
			.body("currentRevision.advice.recommendation", contains("REVIEW_CAREFULLY"));
	}

}
