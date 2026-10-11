package com.blueharvest.pricingexception.request.adapter.in.web;

import com.blueharvest.pricingexception.request.application.port.out.ApplicationCatalogue;
import com.blueharvest.pricingexception.request.domain.Actor;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApplicationController {

	private final ApplicationCatalogue catalogue;

	ApplicationController(ApplicationCatalogue catalogue) {
		this.catalogue = catalogue;
	}

	@GetMapping("/applications")
	List<ApplicationResponse> applications(Actor caller) {
		return catalogue.findAll().stream().map(ApplicationResponse::from).toList();
	}

}
