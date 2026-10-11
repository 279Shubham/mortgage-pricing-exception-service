package com.blueharvest.pricingexception.controller;

import com.blueharvest.pricingexception.dto.PricingExceptionResponse;
import com.blueharvest.pricingexception.dto.SubmitRequestBody;
import com.blueharvest.pricingexception.model.Actor;
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

	private final IdempotencyService idempotency;

	PricingExceptionController(PricingExceptionService service, IdempotencyService idempotency) {
		this.service = service;
		this.idempotency = idempotency;
	}

	@PostMapping("/applications/{applicationId}/pricing-exceptions")
	ResponseEntity<String> submit(Actor caller, @PathVariable String applicationId,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
			@Valid @RequestBody SubmitRequestBody body) {
		SubmitCommand command = new SubmitCommand(applicationId, body.discountBps(), body.reason());
		IdempotencyScope scope = new IdempotencyScope(caller.id(),
				"POST /applications/" + applicationId + "/pricing-exceptions", idempotencyKey);
		StoredResponse response = idempotency.execute(scope, command, () -> created(service.submit(caller, command)));
		return ResponseEntity.status(response.status())
			.location(URI.create(response.location()))
			.contentType(MediaType.APPLICATION_JSON)
			.body(response.body());
	}

	@GetMapping("/pricing-exceptions/{id}")
	PricingExceptionResponse find(Actor caller, @PathVariable UUID id) {
		return service.find(id);
	}

	private static IdempotentResult created(PricingExceptionResponse response) {
		return new IdempotentResult(HttpStatus.CREATED.value(), "/pricing-exceptions/" + response.id(), response);
	}

}
