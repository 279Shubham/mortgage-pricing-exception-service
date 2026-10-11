package com.blueharvest.pricingexception.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_event")
public class RequestEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id")
	private UUID requestId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type")
	private RequestEventType eventType;

	@Column(name = "revision_number")
	private int revisionNumber;

	@Column(name = "actor_id")
	private String actorId;

	@Column(name = "occurred_at")
	private Instant occurredAt;

	protected RequestEvent() {
	}

	public RequestEvent(UUID requestId, RequestEventType eventType, int revisionNumber, String actorId,
			Instant occurredAt) {
		this.requestId = requestId;
		this.eventType = eventType;
		this.revisionNumber = revisionNumber;
		this.actorId = actorId;
		this.occurredAt = occurredAt;
	}

	public Long getId() {
		return id;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public RequestEventType getEventType() {
		return eventType;
	}

	public int getRevisionNumber() {
		return revisionNumber;
	}

	public String getActorId() {
		return actorId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

}
