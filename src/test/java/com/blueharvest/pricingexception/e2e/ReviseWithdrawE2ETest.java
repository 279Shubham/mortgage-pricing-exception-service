package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.response.ValidatableResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class ReviseWithdrawE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String REVISED_REASON = "Customer accepted a smaller discount after the call";

	@Autowired
	DataSource dataSource;

	@Test
	@DisplayName("US-06 AC-1: the requester revises a pending request and gets a new revision with its own advice")
	void requesterRevisesPendingRequest() {
		String id = submitted("rm-anna", "MA-1001", 75, VALID_REASON);
		read(id).body("currentRevision.advice.recommendation", equalTo("REVIEW_CAREFULLY"));
		CLOCK.advance(Duration.ofMinutes(10));

		revise("rm-anna", id, newIdempotencyKey(), body(40, REVISED_REASON))
			.then().statusCode(200)
			.header("Location", nullValue())
			.body("id", equalTo(id))
			.body("status", equalTo("PENDING"))
			.body("submittedAt", equalTo("2026-01-15T09:00:00Z"))
			.body("currentRevision.number", equalTo(2))
			.body("currentRevision.discountBps", equalTo(40))
			.body("currentRevision.reason", equalTo(REVISED_REASON))
			.body("currentRevision.advice.recommendation", equalTo("APPROVE"))
			.body("currentRevision.createdAt", equalTo("2026-01-15T09:10:00Z"))
			.body("decision", nullValue());

		read(id)
			.body("status", equalTo("PENDING"))
			.body("currentRevision.number", equalTo(2))
			.body("currentRevision.discountBps", equalTo(40))
			.body("currentRevision.reason", equalTo(REVISED_REASON))
			.body("currentRevision.advice.recommendation", equalTo("APPROVE"))
			.body("currentRevision.createdAt", equalTo("2026-01-15T09:10:00Z"));

		assertThat(eventTypes()).containsExactly("SUBMITTED", "REVISED");
		Map<String, Object> revised = historyEvents("REVISED").get(0);
		assertThat(revised.get("request_id")).hasToString(id);
		assertThat(revised).containsEntry("revision_number", 2).containsEntry("actor_id", "rm-anna");
		assertThat(((OffsetDateTime) revised.get("occurred_at")).toInstant())
			.isEqualTo(MutableClock.START.plus(Duration.ofMinutes(10)));
	}

	private static String submitted(String userId, String applicationId, int discountBps, String reason) {
		return submit(userId, applicationId, newIdempotencyKey(), body(discountBps, reason))
			.then().statusCode(201)
			.extract().path("id");
	}

	private static ValidatableResponse read(String id) {
		return given()
			.header("X-User-Id", "reviewer-ben")
			.when().get("/pricing-exceptions/{id}", id)
			.then().statusCode(200);
	}

	private List<String> eventTypes() {
		return JdbcClient.create(dataSource)
			.sql("SELECT event_type FROM request_event ORDER BY id")
			.query(String.class).list();
	}

	private List<Map<String, Object>> historyEvents(String eventType) {
		return JdbcClient.create(dataSource)
			.sql("SELECT request_id, event_type, revision_number, actor_id, occurred_at FROM request_event"
					+ " WHERE event_type = ? ORDER BY id")
			.param(eventType)
			.query().listOfRows();
	}

	private static String body(int discountBps, String reason) {
		return "{\"discountBps\":" + discountBps + ",\"reason\":\"" + reason + "\"}";
	}

}
