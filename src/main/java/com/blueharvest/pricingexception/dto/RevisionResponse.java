package com.blueharvest.pricingexception.dto;

import com.blueharvest.pricingexception.model.Advice;
import com.blueharvest.pricingexception.model.RequestRevision;
import java.time.Instant;

public record RevisionResponse(int number, int discountBps, String reason, Advice advice, Instant createdAt,
		boolean superseded) {

	public static RevisionResponse from(RequestRevision revision, int currentRevision) {
		return new RevisionResponse(revision.getRevisionNumber(), revision.getDiscountBps(), revision.getReason(),
				revision.getAdvice(), revision.getCreatedAt(), revision.getRevisionNumber() < currentRevision);
	}

}
