package com.blueharvest.pricingexception.request.adapter.out.persistence;

import com.blueharvest.pricingexception.request.application.port.out.ApplicationCatalogue;
import com.blueharvest.pricingexception.request.domain.MortgageApplication;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class JpaApplicationCatalogue implements ApplicationCatalogue {

	private final MortgageApplicationJpaRepository applications;

	JpaApplicationCatalogue(MortgageApplicationJpaRepository applications) {
		this.applications = applications;
	}

	@Override
	public List<MortgageApplication> findAll() {
		return applications.findAll(Sort.by("id")).stream().map(MortgageApplicationEntity::toDomain).toList();
	}

}
