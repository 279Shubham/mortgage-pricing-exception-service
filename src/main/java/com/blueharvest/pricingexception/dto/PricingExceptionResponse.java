package com.blueharvest.pricingexception.dto;

import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestRevision;
import com.blueharvest.pricingexception.model.RequestStatus;
import java.time.Instant;
import java.util.UUID;

public record PricingExceptionResponse(UUID id, String applicationId, RequestStatus status, String requestedBy,
		Instant submittedAt, Revision currentRevision) {

	public static PricingExceptionResponse from(PricingExceptionRequest request, RequestRevision revision) {
		return new PricingExceptionResponse(request.getId(), request.getApplicationId(), request.getStatus(),
				request.getRequestedBy(), request.getSubmittedAt(), Revision.from(revision));
	}

	public record Revision(int number, int discountBps, String reason, Instant createdAt) {

		static Revision from(RequestRevision revision) {
			return new Revision(revision.getRevisionNumber(), revision.getDiscountBps(), revision.getReason(),
					revision.getCreatedAt());
		}

	}

}
