package com.blueharvest.pricingexception.request.adapter.in.web;

import com.blueharvest.pricingexception.request.domain.MortgageApplication;

record ApplicationResponse(String id, String borrowerLabel, int standardRateBps) {

	static ApplicationResponse from(MortgageApplication application) {
		return new ApplicationResponse(application.id(), application.borrowerLabel(), application.standardRateBps());
	}

}
