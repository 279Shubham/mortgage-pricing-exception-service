package com.blueharvest.pricingexception.dto;

import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestRevision;
import java.time.Instant;
import java.util.UUID;

public record ApprovedDiscountResponse(UUID requestId, int revision, int discountBps, String approvedBy,
		Instant decidedAt) {

	public static ApprovedDiscountResponse from(PricingExceptionRequest request, RequestRevision approvedRevision) {
		return new ApprovedDiscountResponse(request.getId(), approvedRevision.getRevisionNumber(),
				approvedRevision.getDiscountBps(), request.getDecidedBy(), request.getDecidedAt());
	}

}
