package com.blueharvest.pricingexception.dto;

final class FreeText {

	static final String WITHOUT_CONTROL_CHARACTERS = "[\\P{Cntrl}\\r\\n\\t]*";

	static final String NO_CONTROL_CHARACTERS = "must not contain control characters";

	private FreeText() {
	}

}
