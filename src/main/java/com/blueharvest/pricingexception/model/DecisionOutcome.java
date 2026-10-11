package com.blueharvest.pricingexception.model;

public enum DecisionOutcome {

	APPROVE(RequestStatus.APPROVED, RequestEventType.APPROVED),
	DECLINE(RequestStatus.DECLINED, RequestEventType.DECLINED);

	private final RequestStatus status;

	private final RequestEventType eventType;

	DecisionOutcome(RequestStatus status, RequestEventType eventType) {
		this.status = status;
		this.eventType = eventType;
	}

	public RequestStatus status() {
		return status;
	}

	public RequestEventType eventType() {
		return eventType;
	}

}
