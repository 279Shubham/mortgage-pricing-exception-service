package com.blueharvest.pricingexception.controller;

import com.blueharvest.pricingexception.dto.ApproveRequestBody;
import com.blueharvest.pricingexception.dto.DeclineRequestBody;
import com.blueharvest.pricingexception.dto.PricingExceptionResponse;
import com.blueharvest.pricingexception.dto.SubmitRequestBody;
import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.model.RequestStatus;
import com.blueharvest.pricingexception.service.DecideCommand;
import com.blueharvest.pricingexception.service.DecisionService;
import com.blueharvest.pricingexception.service.IdempotencyScope;
import com.blueharvest.pricingexception.service.IdempotencyService;
import com.blueharvest.pricingexception.service.IdempotentResult;
import com.blueharvest.pricingexception.service.PricingExceptionService;
import com.blueharvest.pricingexception.service.StoredResponse;
import com.blueharvest.pricingexception.service.SubmitCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PricingExceptionController {

	private final PricingExceptionService service;

	private final DecisionService decisions;

	private final IdempotencyService idempotency;

	PricingExceptionController(PricingExceptionService service, DecisionService decisions,
			IdempotencyService idempotency) {
		this.service = service;
		this.decisions = decisions;
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

	@GetMapping("/pricing-exceptions/{id}")
	PricingExceptionResponse find(Actor caller, @PathVariable UUID id) {
		return service.find(id);
	}

	@PostMapping("/pricing-exceptions/{id}/approve")
	ResponseEntity<String> approve(Actor caller, @PathVariable UUID id,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
			@Valid @RequestBody ApproveRequestBody body) {
		DecideCommand command = new DecideCommand(id, RequestStatus.APPROVED, body.revisionNumber(), null);
		return decide(caller, "approve", idempotencyKey, command);
	}

	@PostMapping("/pricing-exceptions/{id}/decline")
	ResponseEntity<String> decline(Actor caller, @PathVariable UUID id,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
			@Valid @RequestBody DeclineRequestBody body) {
		DecideCommand command = new DecideCommand(id, RequestStatus.DECLINED, body.revisionNumber(), body.note());
		return decide(caller, "decline", idempotencyKey, command);
	}

	private ResponseEntity<String> decide(Actor caller, String action, String idempotencyKey, DecideCommand command) {
		IdempotencyScope scope = new IdempotencyScope(caller.id(),
				"POST /pricing-exceptions/" + command.requestId() + "/" + action, idempotencyKey);
		return toResponseEntity(idempotency.execute(scope, command, () -> ok(decisions.decide(caller, command))));
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
