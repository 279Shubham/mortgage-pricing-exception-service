package com.blueharvest.pricingexception.service;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;

final class UniqueConstraint {

	private UniqueConstraint() {
	}

	static boolean isViolatedBy(DataIntegrityViolationException failure) {
		return failure.getCause() instanceof ConstraintViolationException violation
				&& violation.getKind() == ConstraintKind.UNIQUE;
	}

}
