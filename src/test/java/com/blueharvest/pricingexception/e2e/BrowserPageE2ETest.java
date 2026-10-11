package com.blueharvest.pricingexception.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrowserPageE2ETest extends E2ETestBase {

	@Test
	@DisplayName("US-08 AC-1: the page is served at the root and loads nothing from another host")
	void pageIsServedAtRootAndIsSelfContained() {
		String page = given()
			.when().get("/")
			.then().statusCode(200)
			.contentType("text/html")
			.extract().asString();

		assertThat(page).contains("<title>Pricing exceptions</title>").doesNotContain("http://", "https://", "src=\"//");
	}

}
