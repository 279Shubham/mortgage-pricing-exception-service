package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RunAndExploreE2ETest extends E2ETestBase {

	@Test
	@DisplayName("US-01 AC-1: the service reports UP on /actuator/health without X-User-Id")
	void healthReportsUpWithoutCallerIdentity() {
		given()
			.when().get("/actuator/health")
			.then().statusCode(200)
			.body("status", equalTo("UP"));
	}

}
