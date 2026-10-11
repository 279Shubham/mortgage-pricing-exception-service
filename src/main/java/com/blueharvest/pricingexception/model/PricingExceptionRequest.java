package com.blueharvest.pricingexception.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pricing_exception_request")
public class PricingExceptionRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "application_id")
	private String applicationId;

	@Column(name = "requested_by")
	private String requestedBy;

	@Enumerated(EnumType.STRING)
	private RequestStatus status;

	@Column(name = "current_revision")
	private int currentRevision;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Version
	private Long version;

	protected PricingExceptionRequest() {
	}

	public PricingExceptionRequest(String applicationId, String requestedBy, Instant submittedAt) {
		this.applicationId = applicationId;
		this.requestedBy = requestedBy;
		this.submittedAt = submittedAt;
		this.status = RequestStatus.PENDING;
		this.currentRevision = 1;
	}

	public UUID getId() {
		return id;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public String getRequestedBy() {
		return requestedBy;
	}

	public RequestStatus getStatus() {
		return status;
	}

	public int getCurrentRevision() {
		return currentRevision;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

}
