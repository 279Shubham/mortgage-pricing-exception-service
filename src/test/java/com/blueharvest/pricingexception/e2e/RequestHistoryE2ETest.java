package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.response.ValidatableResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RequestHistoryE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String REVISED_REASON = "Customer accepted a smaller discount after the call";

	private static final String DECLINE_NOTE = "Discount not justified by the quote";

	@Test
	@DisplayName("US-07 AC-1: the history lists every event in order with its revision, actor and time")
	void historyListsEveryEventInOrder() {
		String id = submitted("rm-lana", "MA-1001", 75);
		CLOCK.advance(Duration.ofMinutes(10));
		revise("rm-lana", id, newIdempotencyKey(), body(40, REVISED_REASON)).then().statusCode(200);
		CLOCK.advance(Duration.ofMinutes(10));
		decide("reviewer-lex", id, "approve", newIdempotencyKey(), "{\"revisionNumber\":2}").then().statusCode(200);

		history("rm-chloe", id).statusCode(200)
			.body("requestId", equalTo(id))
			.body("applicationId", equalTo("MA-1001"))
			.body("events.type", contains("SUBMITTED", "REVISED", "APPROVED"))
			.body("events.revisionNumber", contains(1, 2, 2))
			.body("events.actorId", contains("rm-lana", "rm-lana", "reviewer-lex"))
			.body("events.occurredAt",
					contains("2026-01-15T09:00:00Z", "2026-01-15T09:10:00Z", "2026-01-15T09:20:00Z"));
	}

	@Test
	@DisplayName("US-07 AC-2: each event shows the discount, reason and advice of its revision")
	void eventsShowTheAdviceTheReviewerSaw() {
		String id = submitted("rm-lana", "MA-1001", 75);
		revise("rm-lana", id, newIdempotencyKey(), body(40, REVISED_REASON)).then().statusCode(200);
		decide("reviewer-lex", id, "approve", newIdempotencyKey(), "{\"revisionNumber\":2}").then().statusCode(200);

		history("reviewer-jonathan", id).statusCode(200)
			.body("events.discountBps", contains(75, 40, 40))
			.body("events.reason", contains(VALID_REASON, REVISED_REASON, REVISED_REASON))
			.body("events.advice.recommendation", contains("REVIEW_CAREFULLY", "APPROVE", "APPROVE"))
			.body("events[2].note", nullValue());
	}

	@Test
	@DisplayName("US-07 AC-2: a decline shows the reviewer's note next to the advice")
	void declineShowsTheNote() {
		String id = submitted("rm-lana", "MA-1001", 75);
		decline(id);

		history("rm-lana", id).statusCode(200)
			.body("status", equalTo("DECLINED"))
			.body("events.type", contains("SUBMITTED", "DECLINED"))
			.body("events[0].note", nullValue())
			.body("events[1].note", equalTo(DECLINE_NOTE))
			.body("events[1].advice.recommendation", equalTo("REVIEW_CAREFULLY"));
	}

	@Test
	@DisplayName("US-07 AC-3: the history shows the status and the earlier declined request")
	void historyShowsStatusAndPredecessor() {
		String declined = submitted("rm-lana", "MA-1001", 75);
		decline(declined);
		CLOCK.advance(Duration.ofMinutes(10));
		String next = submitted("rm-lana", "MA-1001", 40);
		withdraw("rm-lana", next, newIdempotencyKey()).then().statusCode(200);

		history("reviewer-lex", declined).statusCode(200).body("previousRequestId", nullValue());
		history("reviewer-lex", next).statusCode(200)
			.body("status", equalTo("WITHDRAWN"))
			.body("previousRequestId", equalTo(declined))
			.body("events.type", contains("SUBMITTED", "WITHDRAWN"));
	}

	@Test
	@DisplayName("US-07 AC-4: the history of an unknown request gets 404")
	void unknownRequestGets404() {
		history("rm-lana", UUID.randomUUID().toString()).statusCode(404);
	}

	@Test
	@DisplayName("US-07 AC-5: each stored event is published as a log line, and a refused call publishes nothing")
	void eventsArePublishedAfterCommit(CapturedOutput output) {
		String id = submitted("rm-lana", "MA-1001", 75);
		revise("rm-chloe", id, newIdempotencyKey(), body(40, REVISED_REASON)).then().statusCode(403);
		withdraw("rm-lana", id, newIdempotencyKey()).then().statusCode(200);

		assertThat(output.getOut())
			.contains("Request event SUBMITTED on request " + id + " revision 1 by rm-lana at 2026-01-15T09:00:00Z")
			.contains("Request event WITHDRAWN on request " + id + " revision 1 by rm-lana at 2026-01-15T09:00:00Z")
			.doesNotContain("Request event REVISED");
	}

	private static void decline(String id) {
		decide("reviewer-lex", id, "decline", newIdempotencyKey(),
				"{\"revisionNumber\":1,\"note\":\"" + DECLINE_NOTE + "\"}")
			.then().statusCode(200);
	}

	private static String submitted(String userId, String applicationId, int discountBps) {
		return submit(userId, applicationId, newIdempotencyKey(), body(discountBps, VALID_REASON))
			.then().statusCode(201)
			.extract().path("id");
	}

	private static ValidatableResponse history(String userId, String id) {
		return given()
			.header("X-User-Id", userId)
			.when().get("/pricing-exceptions/{id}/history", id)
			.then();
	}

	private static String body(int discountBps, String reason) {
		return "{\"discountBps\":" + discountBps + ",\"reason\":\"" + reason + "\"}";
	}

}
