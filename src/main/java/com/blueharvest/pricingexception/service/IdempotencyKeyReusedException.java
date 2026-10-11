package com.blueharvest.pricingexception.service;

public class IdempotencyKeyReusedException extends RuntimeException {

	public IdempotencyKeyReusedException() {
		super("This Idempotency-Key was already used with a different request");
	}

}
