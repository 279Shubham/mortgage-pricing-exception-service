package com.blueharvest.pricingexception.request.adapter.out.persistence;

import com.blueharvest.pricingexception.request.domain.MortgageApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mortgage_application")
class MortgageApplicationEntity {

	@Id
	private String id;

	@Column(name = "borrower_label")
	private String borrowerLabel;

	@Column(name = "standard_rate_bps")
	private int standardRateBps;

	protected MortgageApplicationEntity() {
	}

	MortgageApplication toDomain() {
		return new MortgageApplication(id, borrowerLabel, standardRateBps);
	}

}
