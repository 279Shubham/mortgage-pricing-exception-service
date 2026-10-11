package com.blueharvest.pricingexception.service;

public record SubmitCommand(String applicationId, int discountBps, String reason) {

}
