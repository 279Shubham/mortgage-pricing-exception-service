package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.dto.PricingExceptionResponse;
import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestEvent;
import com.blueharvest.pricingexception.model.RequestRevision;
import com.blueharvest.pricingexception.model.RequestStatus;
import com.blueharvest.pricingexception.model.Role;
import com.blueharvest.pricingexception.repository.PricingExceptionRequestRepository;
import com.blueharvest.pricingexception.repository.RequestEventRepository;
import com.blueharvest.pricingexception.repository.RequestRevisionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionService {

	private final PricingExceptionRequestRepository requests;

	private final RequestRevisionRepository revisions;

	private final RequestEventRepository events;

	private final Clock clock;

	public DecisionService(PricingExceptionRequestRepository requests, RequestRevisionRepository revisions,
			RequestEventRepository events, Clock clock) {
		this.requests = requests;
		this.revisions = revisions;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public PricingExceptionResponse decide(Actor caller, DecideCommand command) {
		requireReviewer(caller);
		PricingExceptionRequest request = findRequest(command.requestId());
		requireAnotherRequester(caller, request);
		requirePending(request);
		requireCurrentRevision(command.revisionNumber(), request);
		Instant now = clock.instant();
		request.decide(command.outcome(), caller.id(), command.revisionNumber(), command.note(), now);
		events.save(new RequestEvent(request.getId(), command.outcome().eventType(), command.revisionNumber(),
				caller.id(), now));
		return PricingExceptionResponse.from(request, currentRevision(request));
	}

	private void requireReviewer(Actor caller) {
		if (!caller.roles().contains(Role.REVIEWER)) {
			throw new ForbiddenException("Only a reviewer may decide a pricing exception");
		}
	}

	private PricingExceptionRequest findRequest(UUID id) {
		return requests.findById(id)
			.orElseThrow(() -> new NotFoundException("Unknown pricing exception request " + id));
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

	private RequestRevision currentRevision(PricingExceptionRequest request) {
		return revisions.findByRequestIdAndRevisionNumber(request.getId(), request.getCurrentRevision())
			.orElseThrow();
	}

}
