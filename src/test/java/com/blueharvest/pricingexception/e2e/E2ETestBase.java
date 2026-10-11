package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Clock;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class E2ETestBase {

	static final MutableClock CLOCK = new MutableClock();

	@TestBean
	Clock clock;

	@LocalServerPort
	int port;

	@Autowired
	Flyway flyway;

	static Clock clock() {
		return CLOCK;
	}

	static String newIdempotencyKey() {
		return UUID.randomUUID().toString();
	}

	static Response submit(String userId, String applicationId, String idempotencyKey, String body) {
		return given()
			.header("X-User-Id", userId)
			.header("Idempotency-Key", idempotencyKey)
			.contentType("application/json; charset=UTF-8")
			.body(body)
			.when().post("/applications/{applicationId}/pricing-exceptions", applicationId);
	}

	static Response decide(String userId, String requestId, String action, String idempotencyKey, String body) {
		return given()
			.header("X-User-Id", userId)
			.header("Idempotency-Key", idempotencyKey)
			.contentType("application/json; charset=UTF-8")
			.body(body)
			.when().post("/pricing-exceptions/{id}/{action}", requestId, action);
	}

	@BeforeEach
	void resetState() {
		flyway.clean();
		flyway.migrate();
		CLOCK.reset();
		RestAssured.port = port;
	}

}
