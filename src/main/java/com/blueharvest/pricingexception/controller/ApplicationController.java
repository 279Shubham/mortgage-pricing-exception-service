package com.blueharvest.pricingexception.controller;

import com.blueharvest.pricingexception.dto.ApplicationResponse;
import com.blueharvest.pricingexception.service.ApplicationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApplicationController {

	private final ApplicationService applicationService;

	ApplicationController(ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@GetMapping("/applications")
	List<ApplicationResponse> applications() {
		return applicationService.findAll().stream().map(ApplicationResponse::from).toList();
	}

}
