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
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class ReviseWithdrawE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String REVISED_REASON = "Customer accepted a smaller discount after the call";

	private static final String ONLY_REQUESTER = "Only the requester may revise or withdraw a pricing exception request";

	private static final String REVISION_ONE ="{\"revisionNumber\":1}";

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

	@Test
	@DisplayName("US-06 AC-4: only the requester may revise or withdraw")
	void onlyRequesterMayReviseOrWithdraw() {
		String id = submitted("rm-anna", "MA-1001", 25, VALID_REASON);

		for (String other : List.of("rm-carl", "reviewer-ben", "dual-erin")) {
			revise(other, id, newIdempotencyKey(), body(40, REVISED_REASON))
				.then().statusCode(403).body("detail", equalTo(ONLY_REQUESTER));
			withdraw(other, id, newIdempotencyKey())
				.then().statusCode(403).body("detail", equalTo(ONLY_REQUESTER));
		}
		read(id).body("status", equalTo("PENDING")).body("currentRevision.number", equalTo(1));
		assertThat(eventTypes()).containsExactly("SUBMITTED");

		decide("reviewer-ben", id, "approve", newIdempotencyKey(), REVISION_ONE).then().statusCode(200);
		revise("rm-carl", id, newIdempotencyKey(), body(40, REVISED_REASON))
			.then().statusCode(403).body("detail", equalTo(ONLY_REQUESTER));
		withdraw("rm-carl", id, newIdempotencyKey())
			.then().statusCode(403).body("detail", equalTo(ONLY_REQUESTER));
	}

	@Test
	@DisplayName("US-06 AC-4: revising or withdrawing a request that is not pending gets 409")
	void closedRequestCannotBeRevisedOrWithdrawn() {
		String approved = submitted("rm-anna", "MA-1001", 25, VALID_REASON);
		decide("reviewer-ben", approved, "approve", newIdempotencyKey(), REVISION_ONE).then().statusCode(200);
		String declined = submitted("rm-anna", "MA-1002", 25, VALID_REASON);
		decide("reviewer-ben", declined, "decline", newIdempotencyKey(), DECLINE_BODY).then().statusCode(200);

		String alreadyApproved = "Request " + approved + " is already APPROVED";
		revise("rm-anna", approved, newIdempotencyKey(), body(40, REVISED_REASON))
			.then().statusCode(409).body("detail", equalTo(alreadyApproved));
		revise("rm-anna", approved, newIdempotencyKey(), body(201, REVISED_REASON))
			.then().statusCode(409).body("detail", equalTo(alreadyApproved));
		withdraw("rm-anna", approved, newIdempotencyKey())
			.then().statusCode(409).body("detail", equalTo(alreadyApproved));
		String alreadyDeclined = "Request " + declined + " is already DECLINED";
		revise("rm-anna", declined, newIdempotencyKey(), body(40, REVISED_REASON))
			.then().statusCode(409).body("detail", equalTo(alreadyDeclined));
		withdraw("rm-anna", declined, newIdempotencyKey())
			.then().statusCode(409).body("detail", equalTo(alreadyDeclined));

		read(approved)
			.body("status", equalTo("APPROVED"))
			.body("decision.decidedBy", equalTo("reviewer-ben"))
			.body("currentRevision.number", equalTo(1));
		read(declined)
			.body("status", equalTo("DECLINED"))
			.body("decision.decidedBy", equalTo("reviewer-ben"))
			.body("currentRevision.number", equalTo(1));
		assertThat(eventTypes()).containsExactly("SUBMITTED", "APPROVED", "SUBMITTED", "DECLINED");
	}

	@Test
	@DisplayName("US-06 AC-4: revising or withdrawing an unknown request gets 404")
	void unknownRequestCannotBeRevisedOrWithdrawn() {
		String unknown = UUID.randomUUID().toString();
		String detail = "Unknown pricing exception request " + unknown;

		revise("rm-anna", unknown, newIdempotencyKey(), body(40, REVISED_REASON))
			.then().statusCode(404).body("detail", equalTo(detail));
		withdraw("rm-anna", unknown, newIdempotencyKey())
			.then().statusCode(404).body("detail", equalTo(detail));
	}

	@ParameterizedTest(name = "US-06 AC-5: {0} is rejected with 400")
	@MethodSource("invalidRevisions")
	@DisplayName("US-06 AC-5: an invalid revision is rejected with 400")
	void invalidRevisionsAreRejected(String label, String body) {
		String id = submitted("rm-anna", "MA-1006", 100, VALID_REASON);

		revise("rm-anna", id, newIdempotencyKey(), body)
			.then().statusCode(400)
			.contentType("application/problem+json");

		read(id).body("currentRevision.number", equalTo(1)).body("currentRevision.discountBps", equalTo(100));
		assertThat(eventTypes()).containsExactly("SUBMITTED");
	}

	static Stream<Arguments> invalidRevisions() {
		String reason = "\"reason\":\"" + REVISED_REASON + "\"";
		return Stream.of(
				Arguments.of("discountBps 0", body(0, REVISED_REASON)),
				Arguments.of("discountBps 201", body(201, REVISED_REASON)),
				Arguments.of("discountBps 150 on MA-1006 (its standard rate)", body(150, REVISED_REASON)),
				Arguments.of("discountBps 25.9", "{\"discountBps\":25.9," + reason + "}"),
				Arguments.of("discountBps \"25\" as a string", "{\"discountBps\":\"25\"," + reason + "}"),
				Arguments.of("a missing discountBps", "{" + reason + "}"),
				Arguments.of("a missing reason", "{\"discountBps\":25}"),
				Arguments.of("a 9-character reason", body(25, "x".repeat(9))),
				Arguments.of("a 1001-character reason", body(25, "x".repeat(1001))),
				Arguments.of("a reason with a control character", body(25, REVISED_REASON + "\\u0001")),
				Arguments.of("a reason of 1000 control characters", body(25, "\\u0001".repeat(1000))),
				Arguments.of("an unknown extra field", "{\"discountBps\":25," + reason + ",\"unexpected\":1}"),
				Arguments.of("an empty body", ""));
	}

	@Test
	@DisplayName("US-06 AC-5: a revision on the exact limits is accepted")
	void revisionOnExactLimitsIsAccepted() {
		String id = submitted("rm-anna", "MA-1006", 100, VALID_REASON);

		revise("rm-anna", id, newIdempotencyKey(), body(149, "x".repeat(10)))
			.then().statusCode(200)
			.body("currentRevision.number", equalTo(2))
			.body("currentRevision.discountBps", equalTo(149));

		String key = newIdempotencyKey();
		String thousandQuotes = body(1, "\\\"".repeat(1000));
		Response original = revise("rm-anna", id, key, thousandQuotes);
		original.then().statusCode(200)
			.body("currentRevision.number", equalTo(3))
			.body("currentRevision.discountBps", equalTo(1))
			.body("currentRevision.reason", equalTo("\"".repeat(1000)));
		Response retry = revise("rm-anna", id, key, thousandQuotes);
		retry.then().statusCode(200);
		assertThat(retry.asString()).isEqualTo(original.asString());
	}

	@Test
	@DisplayName("US-06 AC-2: a decision that states a superseded revision gets 409 and leaves no approved discount")
	void decisionOnSupersededRevisionIsRefused() {
		String id = submitted("rm-anna", "MA-1001", 25, VALID_REASON);
		revise("rm-anna", id, newIdempotencyKey(), body(60, REVISED_REASON)).then().statusCode(200);

		String superseded = "Revision 1 is not the current revision 2";
		decide("reviewer-ben", id, "approve", newIdempotencyKey(), REVISION_ONE)
			.then().statusCode(409).body("detail", equalTo(superseded));
		decide("reviewer-ben", id, "decline", newIdempotencyKey(), DECLINE_BODY)
			.then().statusCode(409).body("detail", equalTo(superseded));
		read(id).body("status", equalTo("PENDING")).body("decision", nullValue());
		approvedDiscounts("MA-1001").body("size()", equalTo(0));

		decide("reviewer-ben", id, "approve", newIdempotencyKey(), "{\"revisionNumber\":2}")
			.then().statusCode(200)
			.body("status", equalTo("APPROVED"))
			.body("decision.revisionNumber", equalTo(2));
		approvedDiscounts("MA-1001")
			.body("size()", equalTo(1))
			.body("revision", contains(2))
			.body("discountBps", contains(60));
	}

	@Test
	@DisplayName("US-06 AC-6: a retried revise replays the original response and creates one revision")
	void retriedReviseReplaysOriginalResponse() {
		String id = submitted("rm-anna", "MA-1001", 75, VALID_REASON);
		String key = newIdempotencyKey();
		Response original = revise("rm-anna", id, key, body(40, REVISED_REASON));
		original.then().statusCode(200);
		CLOCK.advance(Duration.ofMinutes(5));

		Response retry = revise("rm-anna", id, key, body(40, REVISED_REASON));

		retry.then().statusCode(200);
		assertThat(retry.asString()).isEqualTo(original.asString());
		assertThat(eventTypes()).containsExactly("SUBMITTED", "REVISED");
		assertThat(revisionRows(id)).isEqualTo(2);
		revise("rm-anna", id, key, body(30, REVISED_REASON))
			.then().statusCode(422)
			.contentType("application/problem+json");
	}

	@Test
	@DisplayName("US-06 AC-1: earlier revisions keep their content and read as superseded")
	void earlierRevisionsKeepContentAndReadAsSuperseded() {
		String id = submitted("rm-anna", "MA-1001", 75, VALID_REASON);
		revise("rm-anna", id, newIdempotencyKey(), body(40, REVISED_REASON)).then().statusCode(200);
		revise("rm-anna", id, newIdempotencyKey(), body(30, "Client asked")).then().statusCode(200);

		revisions(id)
			.statusCode(200)
			.body("number", contains(1, 2, 3))
			.body("discountBps", contains(75, 40, 30))
			.body("reason", contains(VALID_REASON, REVISED_REASON, "Client asked"))
			.body("advice.recommendation", contains("REVIEW_CAREFULLY", "APPROVE", "REVIEW_CAREFULLY"))
			.body("superseded", contains(true, true, false));

		String neverRevised = submitted("rm-anna", "MA-1002", 25, VALID_REASON);
		revisions(neverRevised)
			.statusCode(200)
			.body("number", contains(1))
			.body("superseded", contains(false));

		revisions(UUID.randomUUID().toString()).statusCode(404);
	}

	private static ValidatableResponse revisions(String id) {
		return given()
			.header("X-User-Id", "reviewer-ben")
			.when().get("/pricing-exceptions/{id}/revisions", id)
			.then();
	}

	private static ValidatableResponse approvedDiscounts(String applicationId) {
		return given()
			.header("X-User-Id", "application-process")
			.when().get("/applications/{applicationId}/approved-discounts", applicationId)
			.then().statusCode(200);
	}

	private int revisionRows(String id) {
		return JdbcClient.create(dataSource)
			.sql("SELECT COUNT(*) FROM request_revision WHERE request_id = ?")
			.param(UUID.fromString(id))
			.query(Integer.class).single();
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
