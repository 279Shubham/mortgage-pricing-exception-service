package com.blueharvest.pricingexception.service.idempotency;

public record IdempotencyScope(String userId, String route, String key) {

}
