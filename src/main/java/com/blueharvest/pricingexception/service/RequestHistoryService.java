package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.dto.RevisionResponse;
import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.repository.RequestRevisionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestHistoryService {

	private final RequestLookup lookup;

	private final RequestRevisionRepository revisions;

	public RequestHistoryService(RequestLookup lookup, RequestRevisionRepository revisions) {
		this.lookup = lookup;
		this.revisions = revisions;
	}

	@Transactional(readOnly = true)
	public List<RevisionResponse> revisions(UUID id) {
		PricingExceptionRequest request = lookup.find(id);
		return revisions.findByRequestIdOrderByRevisionNumberAsc(id)
			.stream()
			.map(revision -> RevisionResponse.from(revision, request.getCurrentRevision()))
			.toList();
	}

}
