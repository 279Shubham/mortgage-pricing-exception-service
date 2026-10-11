package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestRevision;
import com.blueharvest.pricingexception.repository.PricingExceptionRequestRepository;
import com.blueharvest.pricingexception.repository.RequestRevisionRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RequestLookup {

	private final PricingExceptionRequestRepository requests;

	private final RequestRevisionRepository revisions;

	public RequestLookup(PricingExceptionRequestRepository requests, RequestRevisionRepository revisions) {
		this.requests = requests;
		this.revisions = revisions;
	}

	public PricingExceptionRequest find(UUID id) {
		return requests.findById(id)
			.orElseThrow(() -> new NotFoundException("Unknown pricing exception request " + id));
	}

	public RequestRevision currentRevisionOf(PricingExceptionRequest request) {
		return revisions.findByRequestIdAndRevisionNumber(request.getId(), request.getCurrentRevision())
			.orElseThrow();
	}

}
