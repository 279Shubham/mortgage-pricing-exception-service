package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.dto.PricingExceptionResponse;
import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.model.MortgageApplication;
import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestEvent;
import com.blueharvest.pricingexception.model.RequestEventType;
import com.blueharvest.pricingexception.model.RequestRevision;
import com.blueharvest.pricingexception.model.Role;
import com.blueharvest.pricingexception.repository.MortgageApplicationRepository;
import com.blueharvest.pricingexception.repository.PricingExceptionRequestRepository;
import com.blueharvest.pricingexception.repository.RequestEventRepository;
import com.blueharvest.pricingexception.repository.RequestRevisionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricingExceptionService {

	private final MortgageApplicationRepository applications;

	private final PricingExceptionRequestRepository requests;

	private final RequestRevisionRepository revisions;

	private final RequestEventRepository events;

	private final Clock clock;

	private final int minBps;

	private final int maxBps;

	public PricingExceptionService(MortgageApplicationRepository applications,
			PricingExceptionRequestRepository requests, RequestRevisionRepository revisions,
			RequestEventRepository events, Clock clock, @Value("${pricing.discount.min-bps}") int minBps,
			@Value("${pricing.discount.max-bps}") int maxBps) {
		this.applications = applications;
		this.requests = requests;
		this.revisions = revisions;
		this.events = events;
		this.clock = clock;
		this.minBps = minBps;
		this.maxBps = maxBps;
	}

	@Transactional
	public PricingExceptionResponse submit(Actor caller, SubmitCommand command) {
		requireRole(caller, Role.RM);
		MortgageApplication application = findApplication(command.applicationId());
		requireValidDiscount(command.discountBps(), application);
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

	private void requireRole(Actor caller, Role role) {
		if (!caller.roles().contains(role)) {
			throw new ForbiddenException("Only a relationship manager may submit a pricing exception");
		}
	}

	private void requireValidDiscount(int discountBps, MortgageApplication application) {
		int standardRateBps = application.getStandardRateBps();
		if (discountBps < minBps || discountBps > maxBps || discountBps >= standardRateBps) {
			throw new InvalidRequestException("discountBps must be between " + minBps + " and " + maxBps
					+ " and below the standard rate of " + standardRateBps + " bps");
		}
	}

	private PricingExceptionRequest insertPending(PricingExceptionRequest request) {
		try {
			return requests.saveAndFlush(request);
		}
		catch (DataIntegrityViolationException pendingExists) {
			throw new ConflictException("Application " + request.getApplicationId() + " already has a pending request");
		}
	}

}
