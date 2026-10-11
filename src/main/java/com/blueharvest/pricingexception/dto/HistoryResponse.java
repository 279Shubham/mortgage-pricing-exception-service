package com.blueharvest.pricingexception.dto;

import com.blueharvest.pricingexception.model.Advice;
import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestEvent;
import com.blueharvest.pricingexception.model.RequestEventType;
import com.blueharvest.pricingexception.model.RequestRevision;
import com.blueharvest.pricingexception.model.RequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HistoryResponse(UUID requestId, String applicationId, RequestStatus status, UUID previousRequestId,
		List<Event> events) {

	public static HistoryResponse from(PricingExceptionRequest request, List<Event> events) {
		return new HistoryResponse(request.getId(), request.getApplicationId(), request.getStatus(),
				request.getPreviousRequestId(), events);
	}

	public record Event(RequestEventType type, int revisionNumber, String actorId, Instant occurredAt,
			int discountBps, String reason, Advice advice, String note) {

		public static Event from(RequestEvent event, RequestRevision revision, String note) {
			return new Event(event.getEventType(), event.getRevisionNumber(), event.getActorId(),
					event.getOccurredAt(), revision.getDiscountBps(), revision.getReason(), revision.getAdvice(),
					note);
		}

	}

}
