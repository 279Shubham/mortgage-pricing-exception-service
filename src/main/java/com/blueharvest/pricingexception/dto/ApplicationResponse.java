package com.blueharvest.pricingexception.dto;

import com.blueharvest.pricingexception.model.MortgageApplication;

public record ApplicationResponse(String id, String borrowerLabel, int standardRateBps) {

	public static ApplicationResponse from(MortgageApplication application) {
		return new ApplicationResponse(application.getId(), application.getBorrowerLabel(),
				application.getStandardRateBps());
	}

}
