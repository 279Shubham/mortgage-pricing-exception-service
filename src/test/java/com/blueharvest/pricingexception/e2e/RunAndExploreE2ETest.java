package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class RunAndExploreE2ETest extends E2ETestBase {

	private static final Set<String> OPEN_PATHS = Set.of("/users", "/error");

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Test
	@DisplayName("US-01 AC-1: the service reports UP on /actuator/health without X-User-Id")
	void healthReportsUpWithoutCallerIdentity() {
		given()
			.when().get("/actuator/health")
			.then().statusCode(200)
			.body("status", equalTo("UP"));
	}

	@Test
	@DisplayName("US-01 AC-3: the actuator discovery page is closed and health is the only actuator endpoint")
	void actuatorDiscoveryIsClosed() {
		given()
			.when().get("/actuator")
			.then().statusCode(404)
			.contentType("application/problem+json");
		given()
			.when().get("/actuator/info")
			.then().statusCode(404)
			.contentType("application/problem+json");
		given()
			.when().get("/actuator/health")
			.then().statusCode(200)
			.body("status", equalTo("UP"));
	}

	@Test
	@DisplayName("US-01 AC-3: a seeded X-User-Id resolves to that user and roles on /me")
	void seededCallerResolvesOnMe() {
		given()
			.header("X-User-Id", "rm-lana")
			.when().get("/me")
			.then().statusCode(200)
			.body("id", equalTo("rm-lana"))
			.body("displayName", equalTo("Lana (Relationship Manager)"))
			.body("roles", contains("RM"));
	}

	@Test
	@DisplayName("US-01 AC-4: a missing X-User-Id is a 401 Problem Details response")
	void missingCallerIsUnauthorizedProblem() {
		given()
			.when().get("/me")
			.then().statusCode(401)
			.contentType("application/problem+json")
			.header("WWW-Authenticate", equalTo("X-User-Id realm=\"pricing-exception-service\""))
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
			.header("X-User-Id", "reviewer-lex")
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
			.header("X-User-Id", "reviewer-lex")
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

	@Test
	@DisplayName("US-01 AC-3: anyone can list the six seeded users and their roles without X-User-Id")
	void anyoneListsSeededUsersWithoutCallerIdentity() {
		given()
			.when().get("/users")
			.then().statusCode(200)
			.body("id", contains("application-process", "dual-clark", "reviewer-jonathan", "reviewer-lex", "rm-chloe",
					"rm-lana"))
			.body("find { it.id == 'dual-clark' }.displayName", containsString("RM + Reviewer"))
			.body("find { it.id == 'dual-clark' }.roles", contains("RM", "REVIEWER"))
			.body("find { it.id == 'application-process' }.roles", contains("APPLICATION_PROCESS"));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
		"rm-lana, RM",
		"rm-chloe, RM",
		"reviewer-lex, REVIEWER",
		"reviewer-jonathan, REVIEWER",
		"dual-clark, RM|REVIEWER",
		"application-process, APPLICATION_PROCESS"
	})
	@DisplayName("US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles")
	void eachSeededCallerResolvesToExactlyItsRoles(String userId, String roles) {
		given()
			.header("X-User-Id", userId)
			.when().get("/me")
			.then().statusCode(200)
			.body("id", equalTo(userId))
			.body("roles", equalTo(Arrays.asList(roles.split("\\|"))));
	}

	@Test
	@DisplayName("US-01 AC-4: an unknown X-User-Id is a 401 Problem Details response")
	void unknownCallerIsUnauthorizedProblem() {
		given()
			.header("X-User-Id", "nobody")
			.when().get("/me")
			.then().statusCode(401)
			.contentType("application/problem+json")
			.body("status", equalTo(401))
			.body("detail", equalTo("Missing or unknown X-User-Id"));
	}

	@Test
	@DisplayName("US-01 AC-4: an empty X-User-Id is a 401 Problem Details response")
	void emptyCallerIsUnauthorizedProblem() {
		given()
			.header("X-User-Id", "")
			.when().get("/me")
			.then().statusCode(401)
			.contentType("application/problem+json")
			.body("status", equalTo(401))
			.body("detail", equalTo("Missing or unknown X-User-Id"));
	}

	@Test
	@DisplayName("US-01 AC-4: an unknown path is a 404 Problem Details response even without X-User-Id")
	void unknownPathIsNotFoundProblemWithoutCallerIdentity() {
		given()
			.when().get("/no-such-path")
			.then().statusCode(404)
			.contentType("application/problem+json")
			.body("status", equalTo(404))
			.body("title", equalTo("Not Found"));
	}

	@Test
	@DisplayName("US-01 AC-3: the API description and its Swagger page are open and list the endpoints")
	void apiDescriptionAndSwaggerPageAreOpen() {
		given()
			.when().get("/v3/api-docs")
			.then().statusCode(200)
			.body("paths.'/pricing-exceptions/{id}/history'.get", notNullValue())
			.body("paths.'/applications/{applicationId}/pricing-exceptions'.post.parameters.name",
					hasItems("X-User-Id", "Idempotency-Key"))
			.body("paths.'/me'.get.parameters.name", contains("X-User-Id"));

		given().when().get("/swagger-ui/index.html").then().statusCode(200).contentType("text/html");
	}

	@Test
	@DisplayName("US-01 AC-3: every endpoint except the open ones is a 401 without X-User-Id")
	void everyEndpointOutsideTheOpenOnesNeedsCallerIdentity() {
		List<String> protectedEndpoints = handlerMapping.getHandlerMethods().keySet().stream()
			.flatMap(RunAndExploreE2ETest::endpoints)
			.filter(endpoint -> !OPEN_PATHS.contains(endpoint.split(" ")[1]))
			.toList();
		List<String> reachableWithoutCaller = protectedEndpoints.stream()
			.filter(endpoint -> statusWithoutCaller(endpoint) != 401)
			.toList();

		assertThat(protectedEndpoints).contains("GET /me", "GET /applications");
		assertThat(reachableWithoutCaller).isEmpty();
	}

	private static Stream<String> endpoints(RequestMappingInfo mapping) {
		Set<RequestMethod> declared = mapping.getMethodsCondition().getMethods();
		Set<RequestMethod> methods = declared.isEmpty() ? Set.of(RequestMethod.GET) : declared;
		return mapping.getPatternValues().stream()
			.flatMap(path -> methods.stream().map(method -> method + " " + path.replaceAll("\\{[^}]+}", "x")));
	}

	private static int statusWithoutCaller(String endpoint) {
		String[] methodAndPath = endpoint.split(" ");
		return given().when().request(methodAndPath[0], methodAndPath[1]).then().extract().statusCode();
	}

}
