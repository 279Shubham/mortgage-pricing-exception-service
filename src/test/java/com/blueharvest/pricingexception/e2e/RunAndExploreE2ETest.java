package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

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

	@Test
	@DisplayName("US-01 AC-3: a known caller lists the six fictional seeded applications with integer standard rates in bps")
	void knownCallerListsSeededApplications() {
		given()
			.header("X-User-Id", "reviewer-ben")
			.when().get("/applications")
			.then().statusCode(200)
			.body("id", contains("MA-1001", "MA-1002", "MA-1003", "MA-1004", "MA-1005", "MA-1006"))
			.body("find { it.id == 'MA-1001' }.standardRateBps", equalTo(Integer.valueOf(425)))
			.body("standardRateBps", everyItem(greaterThan(0)))
			.body("borrowerLabel", everyItem(startsWith("Fictional household")));
	}

	@Test
	@DisplayName("US-01 AC-3: exactly one seeded application has a standard rate below the 200 bps discount cap")
	void exactlyOneSeededApplicationIsBelowTheDiscountCap() {
		given()
			.header("X-User-Id", "reviewer-ben")
			.when().get("/applications")
			.then().statusCode(200)
			.body("findAll { it.standardRateBps < 200 }.id", contains("MA-1006"))
			.body("find { it.id == 'MA-1006' }.standardRateBps", equalTo(150))
			.body("findAll { it.id != 'MA-1006' }.standardRateBps",
					everyItem(allOf(greaterThanOrEqualTo(350), lessThanOrEqualTo(520))));
	}

	@Test
	@DisplayName("US-01 AC-4: listing applications without X-User-Id is a 401 Problem Details response")
	void listingApplicationsWithoutCallerIsUnauthorizedProblem() {
		given()
			.when().get("/applications")
			.then().statusCode(401)
			.contentType("application/problem+json")
			.body("status", equalTo(401));
	}

}
