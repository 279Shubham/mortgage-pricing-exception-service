package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.model.MortgageApplication;
import com.blueharvest.pricingexception.repository.MortgageApplicationRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ApplicationService {

	private final MortgageApplicationRepository applications;

	public ApplicationService(MortgageApplicationRepository applications) {
		this.applications = applications;
	}

	public List<MortgageApplication> findAll() {
		return applications.findAll(Sort.by("id"));
	}

}
