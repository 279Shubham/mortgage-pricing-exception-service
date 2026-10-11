package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.dto.ApprovedDiscountResponse;
import com.blueharvest.pricingexception.dto.PricingExceptionResponse;
import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.model.Advice;
import com.blueharvest.pricingexception.model.MortgageApplication;
import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestEvent;
import com.blueharvest.pricingexception.model.RequestEventType;
import com.blueharvest.pricingexception.model.RequestRevision;
import com.blueharvest.pricingexception.model.RequestStatus;
import com.blueharvest.pricingexception.model.Role;
import com.blueharvest.pricingexception.repository.MortgageApplicationRepository;
import com.blueharvest.pricingexception.repository.PricingExceptionRequestRepository;
import com.blueharvest.pricingexception.repository.RequestRevisionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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

	private final RequestEventPublisher events;

	private final RequestLookup lookup;

	private final ReviewAdvisor advisor;

	private final Clock clock;

	private final int minBps;

	private final int maxBps;

	public PricingExceptionService(MortgageApplicationRepository applications,
			PricingExceptionRequestRepository requests, RequestRevisionRepository revisions,
			RequestEventPublisher events, RequestLookup lookup, ReviewAdvisor advisor, Clock clock,
			@Value("${pricing.discount.min-bps}") int minBps, @Value("${pricing.discount.max-bps}") int maxBps) {
		this.applications = applications;
		this.requests = requests;
		this.revisions = revisions;
		this.events = events;
		this.lookup = lookup;
		this.advisor = advisor;
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
		request.linkTo(declinedPredecessorOf(request));
		Advice advice = advisor.advise(command.discountBps(), command.reason());
		RequestRevision revision = revisions
			.save(new RequestRevision(request.getId(), 1, command.discountBps(), command.reason(), advice, now));
		events.publishAfterCommit(new RequestEvent(request.getId(), RequestEventType.SUBMITTED, 1, caller.id(), now));
		return PricingExceptionResponse.from(request, revision);
	}

	@Transactional
	public PricingExceptionResponse revise(Actor caller, ReviseCommand command) {
		PricingExceptionRequest request = lookup.find(command.requestId());
		requireRequester(caller, request);
		requirePending(request);
		requireValidDiscount(command.discountBps(), findApplication(request.getApplicationId()));
		Instant now = clock.instant();
		int number = request.moveToNextRevision();
		Advice advice = advisor.advise(command.discountBps(), command.reason());
		RequestRevision revision = revisions.save(new RequestRevision(request.getId(), number,
				command.discountBps(), command.reason(), advice, now));
		events.publishAfterCommit(new RequestEvent(request.getId(), RequestEventType.REVISED, number, caller.id(), now));
		return PricingExceptionResponse.from(request, revision);
	}

	@Transactional
	public PricingExceptionResponse withdraw(Actor caller, WithdrawCommand command) {
		PricingExceptionRequest request = lookup.find(command.requestId());
		requireRequester(caller, request);
		requirePending(request);
		request.withdraw();
		events.publishAfterCommit(new RequestEvent(request.getId(), RequestEventType.WITHDRAWN, request.getCurrentRevision(),
				caller.id(), clock.instant()));
		return withCurrentRevision(request);
	}

	@Transactional(readOnly = true)
	public PricingExceptionResponse find(UUID id) {
		return withCurrentRevision(lookup.find(id));
	}

	@Transactional(readOnly = true)
	public List<PricingExceptionResponse> findByStatus(RequestStatus status) {
		return requests.findByStatusOrderBySubmittedAtAsc(status).stream().map(this::withCurrentRevision).toList();
	}

	@Transactional(readOnly = true)
	public List<ApprovedDiscountResponse> approvedDiscounts(String applicationId) {
		return requests.findFirstByApplicationIdAndStatusOrderByDecidedAtDesc(applicationId, RequestStatus.APPROVED)
			.map(this::withApprovedRevision)
			.stream()
			.toList();
	}

	private ApprovedDiscountResponse withApprovedRevision(PricingExceptionRequest request) {
		RequestRevision approved = revisions
			.findByRequestIdAndRevisionNumber(request.getId(), request.getDecidedRevision())
			.orElseThrow();
		return ApprovedDiscountResponse.from(request, approved);
	}

	private PricingExceptionResponse withCurrentRevision(PricingExceptionRequest request) {
		return PricingExceptionResponse.from(request, lookup.currentRevisionOf(request));
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

	private void requireRequester(Actor caller, PricingExceptionRequest request) {
		if (!request.getRequestedBy().equals(caller.id())) {
			throw new ForbiddenException("Only the requester may revise or withdraw a pricing exception request");
		}
	}

	private void requirePending(PricingExceptionRequest request) {
		if (request.getStatus() != RequestStatus.PENDING) {
			throw new ConflictException("Request " + request.getId() + " is already " + request.getStatus());
		}
	}

	private void requireValidDiscount(int discountBps, MortgageApplication application) {
		int standardRateBps = application.getStandardRateBps();
		if (discountBps < minBps || discountBps > maxBps || discountBps >= standardRateBps) {
			throw new InvalidRequestException("discountBps must be between " + minBps + " and " + maxBps
					+ " and below the standard rate of " + standardRateBps + " bps");
		}
	}

	private UUID declinedPredecessorOf(PricingExceptionRequest request) {
		return requests
			.findFirstByApplicationIdAndIdNotOrderBySubmittedAtDesc(request.getApplicationId(), request.getId())
			.filter(previous -> previous.getStatus() == RequestStatus.DECLINED)
			.map(PricingExceptionRequest::getId)
			.orElse(null);
	}

	private PricingExceptionRequest insertPending(PricingExceptionRequest request) {
		try {
			return requests.saveAndFlush(request);
		}
		catch (DataIntegrityViolationException failure) {
			if (UniqueConstraint.isViolatedBy(failure)) {
				throw new ConflictException(
						"Application " + request.getApplicationId() + " already has a pending request");
			}
			throw failure;
		}
	}

}
