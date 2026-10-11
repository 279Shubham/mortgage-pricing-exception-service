package com.blueharvest.pricingexception.service.idempotency;

public record StoredResponse(int status, String location, String body) {

}
