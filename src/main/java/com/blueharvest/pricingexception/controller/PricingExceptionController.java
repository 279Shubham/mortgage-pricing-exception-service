package com.blueharvest.pricingexception.controller;

import com.blueharvest.pricingexception.dto.ApproveRequestBody;
import com.blueharvest.pricingexception.dto.ApprovedDiscountResponse;
import com.blueharvest.pricingexception.dto.DeclineRequestBody;
import com.blueharvest.pricingexception.dto.HistoryResponse;
import com.blueharvest.pricingexception.dto.PricingExceptionResponse;
import com.blueharvest.pricingexception.dto.ReviseRequestBody;
import com.blueharvest.pricingexception.dto.RevisionResponse;
import com.blueharvest.pricingexception.dto.SubmitRequestBody;
import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.model.DecisionOutcome;
import com.blueharvest.pricingexception.model.RequestStatus;
import com.blueharvest.pricingexception.service.DecideCommand;
import com.blueharvest.pricingexception.service.DecisionService;
import com.blueharvest.pricingexception.service.IdempotencyScope;
import com.blueharvest.pricingexception.service.IdempotencyService;
import com.blueharvest.pricingexception.service.IdempotentResult;
import com.blueharvest.pricingexception.service.PricingExceptionService;
import com.blueharvest.pricingexception.service.RequestHistoryService;
import com.blueharvest.pricingexception.service.ReviseCommand;
import com.blueharvest.pricingexception.service.StoredResponse;
import com.blueharvest.pricingexception.service.SubmitCommand;
import com.blueharvest.pricingexception.service.WithdrawCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PricingExceptionController {

	private final PricingExceptionService service;

	private final DecisionService decisions;

	private final RequestHistoryService history;

	private final IdempotencyService idempotency;

	PricingExceptionController(PricingExceptionService service, DecisionService decisions,
			RequestHistoryService history, IdempotencyService idempotency) {
		this.service = service;
		this.decisions = decisions;
		this.history = history;
		this.idempotency = idempotency;
	}

	@PostMapping("/applications/{applicationId}/pricing-exceptions")
	ResponseEntity<String> submit(Actor caller, @PathVariable @Size(max = 20) String applicationId,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
			@Valid @RequestBody SubmitRequestBody body) {
		SubmitCommand command = new SubmitCommand(applicationId, body.discountBps(), body.reason());
		IdempotencyScope scope = new IdempotencyScope(caller.id(),
				"POST /applications/" + applicationId + "/pricing-exceptions", idempotencyKey);
		return toResponseEntity(idempotency.execute(scope, command, () -> created(service.submit(caller, command))));
	}

	@GetMapping("/applications/{applicationId}/approved-discounts")
	List<ApprovedDiscountResponse> approvedDiscounts(Actor caller, @PathVariable String applicationId) {
		return service.approvedDiscounts(applicationId);
	}

	@GetMapping("/pricing-exceptions/{id}")
	PricingExceptionResponse find(Actor caller, @PathVariable UUID id) {
		return service.find(id);
	}

	@GetMapping("/pricing-exceptions/{id}/revisions")
	List<RevisionResponse> revisions(Actor caller, @PathVariable UUID id) {
		return history.revisions(id);
	}

	@GetMapping("/pricing-exceptions/{id}/history")
	HistoryResponse history(Actor caller, @PathVariable UUID id) {
		return history.history(id);
	}

	@GetMapping("/pricing-exceptions")
	List<PricingExceptionResponse> findByStatus(Actor caller, @RequestParam RequestStatus status) {
		return service.findByStatus(status);
	}

	@PostMapping("/pricing-exceptions/{id}/approve")
	ResponseEntity<String> approve(Actor caller, @PathVariable UUID id,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
			@Valid @RequestBody ApproveRequestBody body) {
		DecideCommand command = new DecideCommand(id, DecisionOutcome.APPROVE, body.revisionNumber(), null);
		return decide(caller, "approve", idempotencyKey, command);
	}

	@PostMapping("/pricing-exceptions/{id}/decline")
	ResponseEntity<String> decline(Actor caller, @PathVariable UUID id,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
			@Valid @RequestBody DeclineRequestBody body) {
		DecideCommand command = new DecideCommand(id, DecisionOutcome.DECLINE, body.revisionNumber(), body.note());
		return decide(caller, "decline", idempotencyKey, command);
	}

	@PostMapping("/pricing-exceptions/{id}/revise")
	ResponseEntity<String> revise(Actor caller, @PathVariable UUID id,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
			@Valid @RequestBody ReviseRequestBody body) {
		ReviseCommand command = new ReviseCommand(id, body.discountBps(), body.reason());
		IdempotencyScope scope = scope(caller, id, "revise", idempotencyKey);
		return toResponseEntity(idempotency.execute(scope, command, () -> ok(service.revise(caller, command))));
	}

	@PostMapping("/pricing-exceptions/{id}/withdraw")
	ResponseEntity<String> withdraw(Actor caller, @PathVariable UUID id,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey) {
		WithdrawCommand command = new WithdrawCommand(id);
		IdempotencyScope scope = scope(caller, id, "withdraw", idempotencyKey);
		return toResponseEntity(idempotency.execute(scope, command, () -> ok(service.withdraw(caller, command))));
	}

	private ResponseEntity<String> decide(Actor caller, String action, String idempotencyKey, DecideCommand command) {
		IdempotencyScope scope = scope(caller, command.requestId(), action, idempotencyKey);
		return toResponseEntity(idempotency.execute(scope, command, () -> ok(decisions.decide(caller, command))));
	}

	private static IdempotencyScope scope(Actor caller, UUID id, String action, String idempotencyKey) {
		return new IdempotencyScope(caller.id(), "POST /pricing-exceptions/" + id + "/" + action, idempotencyKey);
	}

	private static IdempotentResult created(PricingExceptionResponse response) {
		return new IdempotentResult(HttpStatus.CREATED.value(), "/pricing-exceptions/" + response.id(), response);
	}

	private static IdempotentResult ok(PricingExceptionResponse response) {
		return new IdempotentResult(HttpStatus.OK.value(), null, response);
	}

	private static ResponseEntity<String> toResponseEntity(StoredResponse response) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status())
			.contentType(MediaType.APPLICATION_JSON);
		if (response.location() != null) {
			builder.location(URI.create(response.location()));
		}
		return builder.body(response.body());
	}

}
