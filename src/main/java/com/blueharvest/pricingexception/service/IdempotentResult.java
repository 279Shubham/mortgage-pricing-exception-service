package com.blueharvest.pricingexception.service;

public record IdempotentResult(int status, String location, Object body) {

}
