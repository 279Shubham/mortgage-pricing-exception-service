package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class DecideRequestE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String REVISION_ONE = "{\"revisionNumber\":1}";

	private static final String DECLINE_NOTE = "Discount not justified by the quote";

	private static final String DECLINE_BODY = "{\"revisionNumber\":1,\"note\":\"" + DECLINE_NOTE + "\"}";

	private static final String PROBLEM_JSON = "application/problem+json";

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

		read(id)
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

	@Test
	@DisplayName("US-04 AC-3: an approve that does not state the revision gets 400")
	void approveWithoutRevisionIsBadRequest() {
		String id = submitted("rm-anna", "MA-1001", 25);

		decide("reviewer-ben", id, "approve", newIdempotencyKey(), "{}")
			.then().statusCode(400)
			.contentType(PROBLEM_JSON);
		decide("reviewer-ben", id, "approve", newIdempotencyKey(), "{\"revisionNumber\":\"1\"}")
			.then().statusCode(400)
			.contentType(PROBLEM_JSON);

		assertStillPendingAndUndecided(id);
	}

	@Test
	@DisplayName("US-04 AC-3: a revision number that is not the current revision gets 409")
	void staleRevisionNumberIsConflict() {
		String id = submitted("rm-anna", "MA-1001", 25);

		decide("reviewer-ben", id, "approve", newIdempotencyKey(), "{\"revisionNumber\":2}")
			.then().statusCode(409)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("Revision 2 is not the current revision 1"));
		decide("reviewer-ben", id, "decline", newIdempotencyKey(),
				"{\"revisionNumber\":0,\"note\":\"" + DECLINE_NOTE + "\"}")
			.then().statusCode(409)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("Revision 0 is not the current revision 1"));

		assertStillPendingAndUndecided(id);
	}

	@Test
	@DisplayName("US-04 AC-3: an approval of a revision the request has moved on from gets 409")
	void approvalOfSupersededRevisionIsConflict() {
		String id = submitted("rm-anna", "MA-1001", 25);
		reviseInDatabase(id, 2, 60);

		decide("reviewer-ben", id, "approve", newIdempotencyKey(), REVISION_ONE)
			.then().statusCode(409)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("Revision 1 is not the current revision 2"));
		assertStillPendingAndUndecided(id);

		decide("reviewer-ben", id, "approve", newIdempotencyKey(), "{\"revisionNumber\":2}")
			.then().statusCode(200)
			.body("status", equalTo("APPROVED"))
			.body("decision.revisionNumber", equalTo(2))
			.body("currentRevision.discountBps", equalTo(60));
	}

	@Test
	@DisplayName("US-04 AC-3: deciding an unknown request gets 404")
	void unknownRequestIsNotFound() {
		UUID unknown = UUID.randomUUID();

		approve("reviewer-ben", unknown.toString())
			.then().statusCode(404)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("Unknown pricing exception request " + unknown));
		decline("reviewer-ben", unknown.toString())
			.then().statusCode(404)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("Unknown pricing exception request " + unknown));
	}

	@Test
	@DisplayName("US-04 AC-4: a reviewer declines a pending request with a note")
	void reviewerDeclinesPendingRequestWithNote() {
		String id = submitted("rm-anna", "MA-1001", 25);
		CLOCK.advance(Duration.ofMinutes(30));

		decline("reviewer-dana", id)
			.then().statusCode(200)
			.header("Location", nullValue())
			.body("status", equalTo("DECLINED"))
			.body("decision.decidedBy", equalTo("reviewer-dana"))
			.body("decision.decidedAt", equalTo("2026-01-15T09:30:00Z"))
			.body("decision.revisionNumber", equalTo(1))
			.body("decision.note", equalTo(DECLINE_NOTE));

		read(id)
			.body("status", equalTo("DECLINED"))
			.body("decision.decidedBy", equalTo("reviewer-dana"))
			.body("decision.decidedAt", equalTo("2026-01-15T09:30:00Z"))
			.body("decision.note", equalTo(DECLINE_NOTE));

		List<Map<String, Object>> declines = historyEvents("DECLINED");
		assertThat(declines).hasSize(1);
		assertThat(declines.get(0).get("request_id")).hasToString(id);
		assertThat(declines.get(0)).containsEntry("revision_number", 1).containsEntry("actor_id", "reviewer-dana");
	}

	@Test
	@DisplayName("US-04 AC-4: a decline without a note gets 400")
	void declineWithoutNoteIsBadRequest() {
		String id = submitted("rm-anna", "MA-1001", 25);

		decide("reviewer-dana", id, "decline", newIdempotencyKey(), REVISION_ONE)
			.then().statusCode(400)
			.contentType(PROBLEM_JSON)
			.body("status", equalTo(400));
		decide("reviewer-dana", id, "decline", newIdempotencyKey(), "{\"revisionNumber\":1,\"note\":\"   \"}")
			.then().statusCode(400)
			.contentType(PROBLEM_JSON);
		decide("reviewer-dana", id, "decline", newIdempotencyKey(),
				"{\"revisionNumber\":1,\"note\":\"" + "x".repeat(1001) + "\"}")
			.then().statusCode(400)
			.contentType(PROBLEM_JSON);

		assertStillPendingAndUndecided(id);
	}

	@Test
	@DisplayName("US-04 AC-4: a decline note with a control character gets 400")
	void declineNoteWithControlCharacterIsBadRequest() {
		String id = submitted("rm-anna", "MA-1001", 25);

		decide("reviewer-dana", id, "decline", newIdempotencyKey(), declineBody(DECLINE_NOTE + "\\u0001"))
			.then().statusCode(400)
			.contentType(PROBLEM_JSON);
		decide("reviewer-dana", id, "decline", newIdempotencyKey(), declineBody("\\u0001".repeat(1000)))
			.then().statusCode(400)
			.contentType(PROBLEM_JSON);

		assertStillPendingAndUndecided(id);
	}

	@Test
	@DisplayName("US-04 AC-4: a 1000-character reason and note of quotes and line breaks are declined and replayed")
	void longestEscapedReasonAndNoteAreDeclinedAndReplayed() {
		String id = submitted("rm-anna", "MA-1001", 25, "\\\"".repeat(1000));
		String key = newIdempotencyKey();
		String body = declineBody("\\n".repeat(999) + "x");

		Response original = decide("reviewer-dana", id, "decline", key, body);
		original.then().statusCode(200)
			.body("status", equalTo("DECLINED"))
			.body("currentRevision.reason", equalTo("\"".repeat(1000)))
			.body("decision.note", equalTo("\n".repeat(999) + "x"));

		assertThat(decide("reviewer-dana", id, "decline", key, body).asString()).isEqualTo(original.asString());
	}

	@Test
	@DisplayName("US-04 AC-5: the dual-role user cannot decide their own request")
	void dualRoleUserCannotDecideOwnRequest() {
		String id = submitted("dual-erin", "MA-1001", 25);

		approve("dual-erin", id)
			.then().statusCode(403)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("A reviewer may not decide their own request"));
		decline("dual-erin", id)
			.then().statusCode(403)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("A reviewer may not decide their own request"));
		assertStillPendingAndUndecided(id);

		approve("reviewer-ben", id).then().statusCode(200).body("decision.decidedBy", equalTo("reviewer-ben"));
	}

	@Test
	@DisplayName("US-04 AC-5: an RM-only user cannot decide at all")
	void rmOnlyUserCannotDecide() {
		String id = submitted("rm-anna", "MA-1001", 25);

		approve("rm-carl", id)
			.then().statusCode(403)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("Only a reviewer may decide a pricing exception"));
		decline("rm-carl", id)
			.then().statusCode(403)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("Only a reviewer may decide a pricing exception"));
		approve("rm-anna", id).then().statusCode(403);
		approve("rm-carl", UUID.randomUUID().toString()).then().statusCode(403);

		assertStillPendingAndUndecided(id);
	}

	@Test
	@DisplayName("US-04 AC-5: deciding an already approved or declined request gets 409")
	void decidedRequestCannotBeDecidedAgain() {
		String approved = submitted("rm-anna", "MA-1001", 25);
		approve("reviewer-ben", approved).then().statusCode(200);
		String declined = submitted("rm-carl", "MA-1002", 25);
		decline("reviewer-dana", declined).then().statusCode(200);

		decline("reviewer-dana", approved)
			.then().statusCode(409)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("Request " + approved + " is already APPROVED"));
		approve("reviewer-dana", approved).then().statusCode(409);
		approve("reviewer-ben", declined)
			.then().statusCode(409)
			.contentType(PROBLEM_JSON)
			.body("detail", equalTo("Request " + declined + " is already DECLINED"));

		read(approved)
			.body("status", equalTo("APPROVED"))
			.body("decision.decidedBy", equalTo("reviewer-ben"))
			.body("decision.note", nullValue());
		read(declined)
			.body("status", equalTo("DECLINED"))
			.body("decision.decidedBy", equalTo("reviewer-dana"))
			.body("decision.note", equalTo(DECLINE_NOTE));
		assertThat(eventTypes()).containsExactly("SUBMITTED", "APPROVED", "SUBMITTED", "DECLINED");
	}

	@Test
	@DisplayName("US-04 AC-6: a retried approve replays the original response and records one decision")
	void retriedApproveReplaysOriginalResponse() {
		String id = submitted("rm-anna", "MA-1001", 25);
		String key = newIdempotencyKey();
		Response original = decide("reviewer-ben", id, "approve", key, REVISION_ONE);
		original.then().statusCode(200);

		CLOCK.advance(Duration.ofMinutes(5));
		Response retry = decide("reviewer-ben", id, "approve", key, "{ \"revisionNumber\" : 1 }");

		retry.then().statusCode(200).header("Location", nullValue());
		assertThat(retry.asString()).isEqualTo(original.asString());
		assertThat(eventTypes()).containsExactly("SUBMITTED", "APPROVED");
		decide("reviewer-ben", id, "approve", key, "{\"revisionNumber\":2}").then().statusCode(422);
	}

	@Test
	@DisplayName("US-04 AC-6: a retried decline replays the original response and records one decision")
	void retriedDeclineReplaysOriginalResponse() {
		String id = submitted("rm-anna", "MA-1001", 25);
		String key = newIdempotencyKey();
		Response original = decide("reviewer-dana", id, "decline", key, DECLINE_BODY);
		original.then().statusCode(200);

		CLOCK.advance(Duration.ofMinutes(5));
		Response retry = decide("reviewer-dana", id, "decline", key, DECLINE_BODY);

		retry.then().statusCode(200);
		assertThat(retry.asString()).isEqualTo(original.asString());
		assertThat(eventTypes()).containsExactly("SUBMITTED", "DECLINED");
		decide("reviewer-dana", id, "decline", key, "{\"revisionNumber\":1,\"note\":\"Another note\"}")
			.then().statusCode(422);
	}

	@Test
	@DisplayName("US-04 AC-1: a reviewer lists requests filtered by status")
	void reviewerListsRequestsByStatus() {
		String oldest = submitted("rm-anna", "MA-1001", 25);
		CLOCK.advance(Duration.ofMinutes(1));
		String newer = submitted("rm-carl", "MA-1002", 40);
		CLOCK.advance(Duration.ofMinutes(1));
		String approved = submitted("rm-anna", "MA-1003", 30);
		approve("reviewer-ben", approved).then().statusCode(200);

		list("reviewer-ben", "PENDING")
			.statusCode(200)
			.body("id", contains(oldest, newer))
			.body("applicationId", contains("MA-1001", "MA-1002"))
			.body("status", contains("PENDING", "PENDING"))
			.body("currentRevision.number", contains(1, 1))
			.body("currentRevision.discountBps", contains(25, 40));
		list("reviewer-ben", "APPROVED")
			.statusCode(200)
			.body("id", contains(approved))
			.body("decision.decidedBy", contains("reviewer-ben"));
		list("reviewer-ben", "DECLINED").statusCode(200).body("$", empty());
	}

	@Test
	@DisplayName("US-04 AC-1: a missing or unknown status filter gets 400")
	void listNeedsKnownStatus() {
		list("reviewer-ben", "NOPE").statusCode(400).contentType(PROBLEM_JSON);
		given()
			.header("X-User-Id", "reviewer-ben")
			.when().get("/pricing-exceptions")
			.then().statusCode(400)
			.contentType(PROBLEM_JSON);
	}

	@Test
	@DisplayName("US-04 AC-2: a pending request shows an advice snapshot that flags a discount above 50 bps and a short reason")
	void pendingRequestShowsAdviceSnapshot() {
		submitted("rm-anna", "MA-1001", 25, VALID_REASON);
		submitted("rm-anna", "MA-1002", 75, VALID_REASON);
		submitted("rm-anna", "MA-1003", 25, "Client asked");

		list("reviewer-ben", "PENDING")
			.statusCode(200)
			.body(adviceOf("MA-1001") + ".recommendation", equalTo("APPROVE"))
			.body(adviceOf("MA-1001") + ".rationale", not(emptyOrNullString()))
			.body(adviceOf("MA-1002") + ".recommendation", equalTo("REVIEW_CAREFULLY"))
			.body(adviceOf("MA-1002") + ".rationale", containsString("50"))
			.body(adviceOf("MA-1003") + ".recommendation", equalTo("REVIEW_CAREFULLY"))
			.body(adviceOf("MA-1003") + ".rationale", containsString("30"));
	}

	@Test
	@DisplayName("US-04 AC-2: a discount of exactly 50 bps and a reason of exactly 30 characters are not flagged")
	void adviceThresholdsAreExclusive() {
		submitted("rm-anna", "MA-1001", 50, "x".repeat(30));
		submitted("rm-anna", "MA-1002", 51, "x".repeat(30));
		submitted("rm-anna", "MA-1003", 50, "x".repeat(29));
		submitted("rm-anna", "MA-1004", 75, "Client asked");

		list("reviewer-ben", "PENDING")
			.statusCode(200)
			.body(adviceOf("MA-1001") + ".recommendation", equalTo("APPROVE"))
			.body(adviceOf("MA-1002") + ".recommendation", equalTo("REVIEW_CAREFULLY"))
			.body(adviceOf("MA-1003") + ".recommendation", equalTo("REVIEW_CAREFULLY"))
			.body(adviceOf("MA-1004") + ".recommendation", equalTo("REVIEW_CAREFULLY"))
			.body(adviceOf("MA-1004") + ".rationale", allOf(containsString("50"), containsString("30")));
	}

	@Test
	@DisplayName("US-04 AC-2: advice is non-binding, a flagged request can still be approved")
	void flaggedRequestCanStillBeApproved() {
		String id = submitted("rm-anna", "MA-1001", 75, VALID_REASON);
		read(id).body("currentRevision.advice.recommendation", equalTo("REVIEW_CAREFULLY"));

		approve("reviewer-ben", id).then().statusCode(200).body("status", equalTo("APPROVED"));

		read(id)
			.body("status", equalTo("APPROVED"))
			.body("currentRevision.advice.recommendation", equalTo("REVIEW_CAREFULLY"));
	}

	private static String submitted(String userId, String applicationId, int discountBps) {
		return submitted(userId, applicationId, discountBps, VALID_REASON);
	}

	private static String submitted(String userId, String applicationId, int discountBps, String reason) {
		return submit(userId, applicationId, newIdempotencyKey(), body(discountBps, reason))
			.then().statusCode(201)
			.extract().path("id");
	}

	private static String adviceOf(String applicationId) {
		return "find { it.applicationId == '" + applicationId + "' }.currentRevision.advice";
	}

	private static Response approve(String userId, String id) {
		return decide(userId, id, "approve", newIdempotencyKey(), REVISION_ONE);
	}

	private static Response decline(String userId, String id) {
		return decide(userId, id, "decline", newIdempotencyKey(), DECLINE_BODY);
	}

	private static String declineBody(String note) {
		return "{\"revisionNumber\":1,\"note\":\"" + note + "\"}";
	}

	private static ValidatableResponse read(String id) {
		return given()
			.header("X-User-Id", "rm-anna")
			.when().get("/pricing-exceptions/{id}", id)
			.then().statusCode(200);
	}

	private static ValidatableResponse list(String userId, String status) {
		return given()
			.header("X-User-Id", userId)
			.queryParam("status", status)
			.when().get("/pricing-exceptions")
			.then();
	}

	private void assertStillPendingAndUndecided(String id) {
		read(id).body("status", equalTo("PENDING")).body("decision", nullValue());
		assertThat(eventTypes()).containsExactly("SUBMITTED");
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
