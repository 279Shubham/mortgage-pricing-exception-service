package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.model.RequestStatus;
import java.util.UUID;

public record DecideCommand(UUID requestId, RequestStatus outcome, int revisionNumber, String note) {

}
