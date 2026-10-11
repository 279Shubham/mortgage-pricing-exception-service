package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

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

	@Test
	@DisplayName("US-01 AC-3: a seeded X-User-Id resolves to that user and roles on /me")
	void seededCallerResolvesOnMe() {
		given()
			.header("X-User-Id", "rm-anna")
			.when().get("/me")
			.then().statusCode(200)
			.body("id", equalTo("rm-anna"))
			.body("displayName", equalTo("Anna (Relationship Manager)"))
			.body("roles", contains("RM"));
	}

	@Test
	@DisplayName("US-01 AC-4: a missing X-User-Id is a 401 Problem Details response")
	void missingCallerIsUnauthorizedProblem() {
		given()
			.when().get("/me")
			.then().statusCode(401)
			.contentType("application/problem+json")
			.body("status", equalTo(401))
			.body("title", equalTo("Unauthorized"))
			.body("detail", equalTo("Missing or unknown X-User-Id"))
			.body("trace", nullValue())
			.body("exception", nullValue())
			.body("timestamp", nullValue());
	}

}
