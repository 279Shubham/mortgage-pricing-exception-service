package com.blueharvest.pricingexception.service.command;

public record SubmitCommand(String applicationId, int discountBps, String reason) {

}
