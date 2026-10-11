package com.blueharvest.pricingexception.service.idempotency;

public record IdempotentResult(int status, String location, Object body) {

}
