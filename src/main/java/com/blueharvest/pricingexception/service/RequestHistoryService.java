package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.dto.HistoryResponse;
import com.blueharvest.pricingexception.dto.RevisionResponse;
import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestEvent;
import com.blueharvest.pricingexception.model.RequestEventType;
import com.blueharvest.pricingexception.model.RequestRevision;
import com.blueharvest.pricingexception.repository.RequestEventRepository;
import com.blueharvest.pricingexception.repository.RequestRevisionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestHistoryService {

	private final RequestLookup lookup;

	private final RequestRevisionRepository revisions;

	private final RequestEventRepository events;

	public RequestHistoryService(RequestLookup lookup, RequestRevisionRepository revisions,
			RequestEventRepository events) {
		this.lookup = lookup;
		this.revisions = revisions;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public HistoryResponse history(UUID id) {
		PricingExceptionRequest request = lookup.find(id);
		Map<Integer, RequestRevision> byNumber = revisions.findByRequestIdOrderByRevisionNumberAsc(id)
			.stream()
			.collect(Collectors.toMap(RequestRevision::getRevisionNumber, Function.identity()));
		List<HistoryResponse.Event> history = events.findByRequestIdOrderByIdAsc(id)
			.stream()
			.map(event -> HistoryResponse.Event.from(event, byNumber.get(event.getRevisionNumber()),
					noteOf(event, request)))
			.toList();
		return HistoryResponse.from(request, history);
	}

	@Transactional(readOnly = true)
	public List<RevisionResponse> revisions(UUID id) {
		PricingExceptionRequest request = lookup.find(id);
		return revisions.findByRequestIdOrderByRevisionNumberAsc(id)
			.stream()
			.map(revision -> RevisionResponse.from(revision, request.getCurrentRevision()))
			.toList();
	}

	private static String noteOf(RequestEvent event, PricingExceptionRequest request) {
		return event.getEventType() == RequestEventType.DECLINED ? request.getDecisionNote() : null;
	}

}
