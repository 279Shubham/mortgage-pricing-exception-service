package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.model.RequestEvent;
import com.blueharvest.pricingexception.repository.RequestEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class RequestEventPublisher {

	private static final Logger LOG = LoggerFactory.getLogger(RequestEventPublisher.class);

	private final RequestEventRepository events;

	public RequestEventPublisher(RequestEventRepository events) {
		this.events = events;
	}

	public void publishAfterCommit(RequestEvent event) {
		events.save(event);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				announce(event);
			}
		});
	}

	private void announce(RequestEvent event) {
		LOG.info("Request event {} on request {} revision {} by {} at {}", event.getEventType(),
				event.getRequestId(), event.getRevisionNumber(), event.getActorId(), event.getOccurredAt());
	}

}
