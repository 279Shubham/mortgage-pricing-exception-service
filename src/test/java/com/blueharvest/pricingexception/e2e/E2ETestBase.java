package com.blueharvest.pricingexception.e2e;

import io.restassured.RestAssured;
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

	@BeforeEach
	void resetState() {
		flyway.clean();
		flyway.migrate();
		CLOCK.reset();
		RestAssured.port = port;
	}

}
