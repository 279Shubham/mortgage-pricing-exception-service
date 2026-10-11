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
@Table(name = "idempotency_record")
public class IdempotencyRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id")
	private String userId;

	@Column(name = "route")
	private String route;

	@Column(name = "idempotency_key")
	private String idempotencyKey;

	@Column(name = "fingerprint")
	private String fingerprint;

	@Column(name = "response_status")
	private Integer responseStatus;

	@Column(name = "response_location")
	private String responseLocation;

	@Column(name = "response_body")
	private String responseBody;

	@Column(name = "expires_at")
	private Instant expiresAt;

	protected IdempotencyRecord() {
	}

	public IdempotencyRecord(String userId, String route, String idempotencyKey, String fingerprint,
			Instant expiresAt) {
		this.userId = userId;
		this.route = route;
		this.idempotencyKey = idempotencyKey;
		this.fingerprint = fingerprint;
		this.expiresAt = expiresAt;
	}

	public void complete(int status, String location, String body) {
		this.responseStatus = status;
		this.responseLocation = location;
		this.responseBody = body;
	}

	public boolean matches(String otherFingerprint) {
		return fingerprint.equals(otherFingerprint);
	}

	public Integer getResponseStatus() {
		return responseStatus;
	}

	public String getResponseLocation() {
		return responseLocation;
	}

	public String getResponseBody() {
		return responseBody;
	}

}
