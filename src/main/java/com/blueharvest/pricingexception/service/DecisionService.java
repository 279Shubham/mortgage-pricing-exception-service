package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.dto.PricingExceptionResponse;
import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestEvent;
import com.blueharvest.pricingexception.model.RequestStatus;
import com.blueharvest.pricingexception.model.Role;
import com.blueharvest.pricingexception.service.exception.ConflictException;
import com.blueharvest.pricingexception.service.exception.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionService {

	private final RequestLookup lookup;

	private final RequestEventPublisher events;

	private final Clock clock;

	public DecisionService(RequestLookup lookup, RequestEventPublisher events, Clock clock) {
		this.lookup = lookup;
		this.events = events;
		this.clock = clock;
	}

	// Not implemented yet: a decision, a revision and a withdrawal reaching the same request
	// at the same moment. The version on the request row lets only one of them through; the
	// other caller gets a 500 instead of a 409, and a retry with the same key is then
	// answered from the stored state.
	@Transactional
	public PricingExceptionResponse decide(Actor caller, DecideCommand command) {
		requireReviewer(caller);
		PricingExceptionRequest request = lookup.find(command.requestId());
		requireAnotherRequester(caller, request);
		requirePending(request);
		requireCurrentRevision(command.revisionNumber(), request);
		Instant now = clock.instant();
		request.decide(command.outcome(), caller.id(), command.revisionNumber(), command.note(), now);
		events.publishAfterCommit(new RequestEvent(request.getId(), command.outcome().eventType(), command.revisionNumber(),
				caller.id(), now));
		return PricingExceptionResponse.from(request, lookup.currentRevisionOf(request));
	}

	private void requireReviewer(Actor caller) {
		if (!caller.roles().contains(Role.REVIEWER)) {
			throw new ForbiddenException("Only a reviewer may decide a pricing exception");
		}
	}

	private void requireAnotherRequester(Actor caller, PricingExceptionRequest request) {
		if (request.getRequestedBy().equals(caller.id())) {
			throw new ForbiddenException("A reviewer may not decide their own request");
		}
	}

	private void requirePending(PricingExceptionRequest request) {
		if (request.getStatus() != RequestStatus.PENDING) {
			throw new ConflictException("Request " + request.getId() + " is already " + request.getStatus());
		}
	}

	private void requireCurrentRevision(int statedRevision, PricingExceptionRequest request) {
		if (statedRevision != request.getCurrentRevision()) {
			throw new ConflictException("Revision " + statedRevision + " is not the current revision "
					+ request.getCurrentRevision());
		}
	}

}
