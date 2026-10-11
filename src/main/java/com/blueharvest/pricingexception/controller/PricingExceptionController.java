package com.blueharvest.pricingexception.controller;

import com.blueharvest.pricingexception.dto.PricingExceptionResponse;
import com.blueharvest.pricingexception.dto.SubmitRequestBody;
import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.service.PricingExceptionService;
import com.blueharvest.pricingexception.service.SubmitCommand;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PricingExceptionController {

	private final PricingExceptionService service;

	PricingExceptionController(PricingExceptionService service) {
		this.service = service;
	}

	@PostMapping("/applications/{applicationId}/pricing-exceptions")
	ResponseEntity<PricingExceptionResponse> submit(Actor caller, @PathVariable String applicationId,
			@Valid @RequestBody SubmitRequestBody body) {
		SubmitCommand command = new SubmitCommand(applicationId, body.discountBps(), body.reason());
		PricingExceptionResponse response = service.submit(caller, command);
		return ResponseEntity.created(URI.create("/pricing-exceptions/" + response.id())).body(response);
	}

	@GetMapping("/pricing-exceptions/{id}")
	PricingExceptionResponse find(Actor caller, @PathVariable UUID id) {
		return service.find(id);
	}

}
