package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.response.Response;
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

	private static final String REVISION_ONE = "{\"revisionNumber\":1}";

	private static final String DECLINE_BODY = "{\"revisionNumber\":1,\"note\":\"Discount not justified by the quote\"}";

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

	@Test
	@DisplayName("US-06 AC-3: the requester withdraws a pending request")
	void requesterWithdrawsPendingRequest() {
		String id = submitted("rm-anna", "MA-1001", 25, VALID_REASON);
		CLOCK.advance(Duration.ofMinutes(10));

		withdraw("rm-anna", id, newIdempotencyKey())
			.then().statusCode(200)
			.header("Location", nullValue())
			.body("id", equalTo(id))
			.body("status", equalTo("WITHDRAWN"))
			.body("decision", nullValue())
			.body("currentRevision.number", equalTo(1));

		read(id)
			.body("status", equalTo("WITHDRAWN"))
			.body("decision", nullValue())
			.body("currentRevision.number", equalTo(1));

		assertThat(eventTypes()).containsExactly("SUBMITTED", "WITHDRAWN");
		Map<String, Object> withdrawn = historyEvents("WITHDRAWN").get(0);
		assertThat(withdrawn.get("request_id")).hasToString(id);
		assertThat(withdrawn).containsEntry("revision_number", 1).containsEntry("actor_id", "rm-anna");
		assertThat(((OffsetDateTime) withdrawn.get("occurred_at")).toInstant())
			.isEqualTo(MutableClock.START.plus(Duration.ofMinutes(10)));
	}

	@Test
	@DisplayName("US-06 AC-3: a withdrawn request cannot be decided or revised, and the application accepts a new request")
	void withdrawnRequestIsClosedAndFreesTheApplication() {
		String id = submitted("rm-anna", "MA-1001", 25, VALID_REASON);
		submit("rm-carl", "MA-1001", newIdempotencyKey(), body(30, VALID_REASON)).then().statusCode(409);
		withdraw("rm-anna", id, newIdempotencyKey()).then().statusCode(200);

		String alreadyWithdrawn = "Request " + id + " is already WITHDRAWN";
		decide("reviewer-ben", id, "approve", newIdempotencyKey(), REVISION_ONE)
			.then().statusCode(409).body("detail", equalTo(alreadyWithdrawn));
		decide("reviewer-ben", id, "decline", newIdempotencyKey(), DECLINE_BODY)
			.then().statusCode(409).body("detail", equalTo(alreadyWithdrawn));
		revise("rm-anna", id, newIdempotencyKey(), body(40, REVISED_REASON))
			.then().statusCode(409).body("detail", equalTo(alreadyWithdrawn));
		withdraw("rm-anna", id, newIdempotencyKey())
			.then().statusCode(409).body("detail", equalTo(alreadyWithdrawn));

		CLOCK.advance(Duration.ofMinutes(1));
		String newId = submit("rm-anna", "MA-1001", newIdempotencyKey(), body(25, VALID_REASON))
			.then().statusCode(201)
			.body("status", equalTo("PENDING"))
			.body("previousRequestId", nullValue())
			.extract().path("id");

		assertThat(newId).isNotEqualTo(id);
		list("PENDING").body("id", contains(newId));
		list("WITHDRAWN").body("id", contains(id));
	}

	@Test
	@DisplayName("US-06 AC-6: a retried withdraw replays the original response and records one withdrawal")
	void retriedWithdrawReplaysOriginalResponse() {
		String id = submitted("rm-anna", "MA-1001", 25, VALID_REASON);
		String key = newIdempotencyKey();
		Response original = withdraw("rm-anna", id, key);
		original.then().statusCode(200);
		CLOCK.advance(Duration.ofMinutes(5));

		Response retry = withdraw("rm-anna", id, key);

		retry.then().statusCode(200);
		assertThat(retry.asString()).isEqualTo(original.asString());
		assertThat(eventTypes()).containsExactly("SUBMITTED", "WITHDRAWN");
	}

	private static ValidatableResponse list(String status) {
		return given()
			.header("X-User-Id", "reviewer-ben")
			.queryParam("status", status)
			.when().get("/pricing-exceptions")
			.then().statusCode(200);
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
