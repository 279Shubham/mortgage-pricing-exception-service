package com.blueharvest.pricingexception.service.command;

import com.blueharvest.pricingexception.model.DecisionOutcome;
import java.util.UUID;

public record DecideCommand(UUID requestId, DecisionOutcome outcome, int revisionNumber, String note) {

}
