package com.blueharvest.pricingexception.service;

public record IdempotencyScope(String userId, String route, String key) {

}
