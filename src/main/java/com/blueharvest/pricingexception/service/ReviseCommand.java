package com.blueharvest.pricingexception.service;

import java.util.UUID;

public record ReviseCommand(UUID requestId, int discountBps, String reason) {

}
