package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.dto.PricingExceptionResponse;
import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestEvent;
import com.blueharvest.pricingexception.model.RequestEventType;
import com.blueharvest.pricingexception.model.RequestRevision;
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
		PricingExceptionRequest request = findRequest(command.requestId());
		Instant now = clock.instant();
		request.decide(command.outcome(), caller.id(), command.revisionNumber(), command.note(), now);
		events.save(new RequestEvent(request.getId(), RequestEventType.valueOf(command.outcome().name()),
				command.revisionNumber(), caller.id(), now));
		return PricingExceptionResponse.from(request, currentRevision(request));
	}

	private PricingExceptionRequest findRequest(UUID id) {
		return requests.findById(id)
			.orElseThrow(() -> new NotFoundException("Unknown pricing exception request " + id));
	}

	private RequestRevision currentRevision(PricingExceptionRequest request) {
		return revisions.findByRequestIdAndRevisionNumber(request.getId(), request.getCurrentRevision())
			.orElseThrow();
	}

}
