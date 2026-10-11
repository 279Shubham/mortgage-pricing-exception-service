package com.blueharvest.pricingexception.request.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class UnknownCallerException extends ErrorResponseException {

	UnknownCallerException() {
		super(HttpStatus.UNAUTHORIZED);
		getBody().setDetail("Missing or unknown X-User-Id");
	}

}
