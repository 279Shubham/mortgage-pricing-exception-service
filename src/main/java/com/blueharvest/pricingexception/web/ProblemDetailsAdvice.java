package com.blueharvest.pricingexception.web;

import com.blueharvest.pricingexception.service.exception.ConflictException;
import com.blueharvest.pricingexception.service.exception.ForbiddenException;
import com.blueharvest.pricingexception.service.exception.IdempotencyKeyReusedException;
import com.blueharvest.pricingexception.service.exception.InvalidRequestException;
import com.blueharvest.pricingexception.service.exception.NotFoundException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

	@ExceptionHandler
	ProblemDetail notFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail forbidden(ForbiddenException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail invalid(InvalidRequestException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail conflict(ConflictException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail stillInProgress(PessimisticLockingFailureException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Another request is still in progress; retry later");
	}

	@ExceptionHandler
	ProblemDetail keyReused(IdempotencyKeyReusedException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		e.getBody().setDetail(e.getParameterValidationResults().stream()
			.flatMap(ProblemDetailsAdvice::rejections)
			.sorted()
			.collect(Collectors.joining("; ")));
		return handleExceptionInternal(e, e.getBody(), headers, status, request);
	}

	private static Stream<String> rejections(ParameterValidationResult result) {
		if (result instanceof ParameterErrors errors) {
			return errors.getFieldErrors().stream().map(error -> error.getField() + ": " + error.getDefaultMessage());
		}
		String parameter = result.getMethodParameter().getParameterName();
		return result.getResolvableErrors().stream().map(error -> parameter + ": " + error.getDefaultMessage());
	}

}
