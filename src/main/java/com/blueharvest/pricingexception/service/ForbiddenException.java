package com.blueharvest.pricingexception.service;

public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}

}
