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

	@Column(name = "decided_by")
	private String decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "decided_revision")
	private Integer decidedRevision;

	@Column(name = "decision_note")
	private String decisionNote;

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

	public String getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public Integer getDecidedRevision() {
		return decidedRevision;
	}

	public String getDecisionNote() {
		return decisionNote;
	}

	public void decide(RequestStatus outcome, String reviewerId, int revisionNumber, String note, Instant decidedAt) {
		this.status = outcome;
		this.decidedBy = reviewerId;
		this.decidedRevision = revisionNumber;
		this.decisionNote = note;
		this.decidedAt = decidedAt;
	}

}
