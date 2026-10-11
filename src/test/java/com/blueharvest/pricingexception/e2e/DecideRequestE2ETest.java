package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class DecideRequestE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String REVISION_ONE = "{\"revisionNumber\":1}";

	@Autowired
	DataSource dataSource;

	@Test
	@DisplayName("US-04 AC-3: a reviewer approves a pending request by stating the revision reviewed")
	void reviewerApprovesPendingRequest() {
		String id = submitted("rm-anna", "MA-1001", 25);
		CLOCK.advance(Duration.ofHours(1));

		decide("reviewer-ben", id, "approve", newIdempotencyKey(), REVISION_ONE)
			.then().statusCode(200)
			.header("Location", nullValue())
			.body("id", equalTo(id))
			.body("status", equalTo("APPROVED"))
			.body("decision.decidedBy", equalTo("reviewer-ben"))
			.body("decision.decidedAt", equalTo("2026-01-15T10:00:00Z"))
			.body("decision.revisionNumber", equalTo(1));

		given()
			.header("X-User-Id", "rm-anna")
			.when().get("/pricing-exceptions/{id}", id)
			.then().statusCode(200)
			.body("status", equalTo("APPROVED"))
			.body("decision.decidedBy", equalTo("reviewer-ben"))
			.body("decision.decidedAt", equalTo("2026-01-15T10:00:00Z"))
			.body("decision.revisionNumber", equalTo(1));

		List<Map<String, Object>> approvals = historyEvents("APPROVED");
		assertThat(approvals).hasSize(1);
		Map<String, Object> approval = approvals.get(0);
		assertThat(approval.get("request_id")).hasToString(id);
		assertThat(approval).containsEntry("revision_number", 1).containsEntry("actor_id", "reviewer-ben");
		assertThat(((OffsetDateTime) approval.get("occurred_at")).toInstant())
			.isEqualTo(MutableClock.START.plus(Duration.ofHours(1)));
	}

	private static String submitted(String userId, String applicationId, int discountBps) {
		return submit(userId, applicationId, newIdempotencyKey(), body(discountBps, VALID_REASON))
			.then().statusCode(201)
			.extract().path("id");
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
