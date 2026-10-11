package com.blueharvest.pricingexception.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mortgage_application")
public class MortgageApplication {

	@Id
	private String id;

	@Column(name = "borrower_label")
	private String borrowerLabel;

	@Column(name = "standard_rate_bps")
	private int standardRateBps;

	protected MortgageApplication() {
	}

	public String getId() {
		return id;
	}

	public String getBorrowerLabel() {
		return borrowerLabel;
	}

	public int getStandardRateBps() {
		return standardRateBps;
	}

}
