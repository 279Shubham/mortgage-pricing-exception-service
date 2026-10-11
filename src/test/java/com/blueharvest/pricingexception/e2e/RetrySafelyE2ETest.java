package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetrySafelyE2ETest extends E2ETestBase {

	private static final String VALID_REASON = "Retention offer matching a competitor quote";

	private static final String VALID_BODY = body(25, VALID_REASON);

	@Test
	@DisplayName("US-03 AC-1: a retry with the same key and payload replays the original status, body and Location")
	void retryReplaysOriginalResponse() {
		String key = newIdempotencyKey();
		Response original = submit("rm-anna", "MA-1001", key, VALID_BODY);
		original.then().statusCode(201);

		String reorderedAndSpaced = "{ \"reason\" : \"" + VALID_REASON + "\",  \"discountBps\" : 25 }";
		Response retry = submit("rm-anna", "MA-1001", key, reorderedAndSpaced);

		retry.then().statusCode(201).header("Location", equalTo(original.header("Location")));
		assertThat(retry.asString()).isEqualTo(original.asString());
		given().header("X-User-Id", "rm-anna").when().get(original.header("Location")).then().statusCode(200);
		submit("rm-anna", "MA-1001", newIdempotencyKey(), VALID_BODY).then().statusCode(409);
	}

	@Test
	@DisplayName("US-03 AC-2: the same key with a different payload gets 422")
	void sameKeyDifferentPayloadIsUnprocessable() {
		String key = newIdempotencyKey();
		Response original = submit("rm-anna", "MA-1001", key, VALID_BODY);
		original.then().statusCode(201);

		submit("rm-anna", "MA-1001", key, body(30, VALID_REASON))
			.then().statusCode(422)
			.contentType("application/problem+json")
			.body("title", equalTo("Unprocessable Content"));
		submit("rm-anna", "MA-1001", key, body(25, "A different reason for the same discount"))
			.then().statusCode(422)
			.contentType("application/problem+json");
		submit("rm-anna", "MA-1001", key, VALID_BODY)
			.then().statusCode(201)
			.header("Location", equalTo(original.header("Location")));
	}

	private static String body(int discountBps, String reason) {
		return "{\"discountBps\":" + discountBps + ",\"reason\":\"" + reason + "\"}";
	}

}
