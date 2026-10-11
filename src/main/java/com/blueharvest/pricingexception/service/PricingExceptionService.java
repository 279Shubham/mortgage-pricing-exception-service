package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.dto.PricingExceptionResponse;
import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.model.MortgageApplication;
import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestEvent;
import com.blueharvest.pricingexception.model.RequestEventType;
import com.blueharvest.pricingexception.model.RequestRevision;
import com.blueharvest.pricingexception.repository.MortgageApplicationRepository;
import com.blueharvest.pricingexception.repository.PricingExceptionRequestRepository;
import com.blueharvest.pricingexception.repository.RequestEventRepository;
import com.blueharvest.pricingexception.repository.RequestRevisionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricingExceptionService {

	private final MortgageApplicationRepository applications;

	private final PricingExceptionRequestRepository requests;

	private final RequestRevisionRepository revisions;

	private final RequestEventRepository events;

	private final Clock clock;

	public PricingExceptionService(MortgageApplicationRepository applications,
			PricingExceptionRequestRepository requests, RequestRevisionRepository revisions,
			RequestEventRepository events, Clock clock) {
		this.applications = applications;
		this.requests = requests;
		this.revisions = revisions;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public PricingExceptionResponse submit(Actor caller, SubmitCommand command) {
		MortgageApplication application = findApplication(command.applicationId());
		Instant now = clock.instant();
		PricingExceptionRequest request = insertPending(
				new PricingExceptionRequest(application.getId(), caller.id(), now));
		RequestRevision revision = revisions
			.save(new RequestRevision(request.getId(), 1, command.discountBps(), command.reason(), now));
		events.save(new RequestEvent(request.getId(), RequestEventType.SUBMITTED, 1, caller.id(), now));
		return PricingExceptionResponse.from(request, revision);
	}

	@Transactional(readOnly = true)
	public PricingExceptionResponse find(UUID id) {
		PricingExceptionRequest request = requests.findById(id)
			.orElseThrow(() -> new NotFoundException("Unknown pricing exception request " + id));
		RequestRevision revision = revisions.findByRequestIdAndRevisionNumber(id, request.getCurrentRevision())
			.orElseThrow();
		return PricingExceptionResponse.from(request, revision);
	}

	private MortgageApplication findApplication(String applicationId) {
		return applications.findById(applicationId)
			.orElseThrow(() -> new NotFoundException("Unknown mortgage application " + applicationId));
	}

	private PricingExceptionRequest insertPending(PricingExceptionRequest request) {
		return requests.saveAndFlush(request);
	}

}
