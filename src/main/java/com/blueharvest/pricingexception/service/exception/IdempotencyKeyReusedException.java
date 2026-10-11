package com.blueharvest.pricingexception.service.exception;

public class IdempotencyKeyReusedException extends RuntimeException {

	public IdempotencyKeyReusedException() {
		super("This Idempotency-Key was already used with a different request");
	}

}
