package com.blueharvest.pricingexception.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class UnknownCallerException extends ErrorResponseException {

	UnknownCallerException() {
		super(HttpStatus.UNAUTHORIZED);
		getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "X-User-Id realm=\"pricing-exception-service\"");
		getBody().setDetail("Missing or unknown X-User-Id");
	}

}
