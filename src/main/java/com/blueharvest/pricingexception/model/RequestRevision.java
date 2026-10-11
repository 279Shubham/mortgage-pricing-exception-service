package com.blueharvest.pricingexception.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_revision")
public class RequestRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "request_id")
	private UUID requestId;

	@Column(name = "revision_number")
	private int revisionNumber;

	@Column(name = "discount_bps")
	private int discountBps;

	private String reason;

	@Column(name = "created_at")
	private Instant createdAt;

	protected RequestRevision() {
	}

	public RequestRevision(UUID requestId, int revisionNumber, int discountBps, String reason, Instant createdAt) {
		this.requestId = requestId;
		this.revisionNumber = revisionNumber;
		this.discountBps = discountBps;
		this.reason = reason;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public int getRevisionNumber() {
		return revisionNumber;
	}

	public int getDiscountBps() {
		return discountBps;
	}

	public String getReason() {
		return reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
