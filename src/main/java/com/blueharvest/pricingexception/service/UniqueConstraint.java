package com.blueharvest.pricingexception.service;

import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class UniqueConstraint {

	private UniqueConstraint() {
	}

	public static boolean isViolatedBy(DataIntegrityViolationException failure) {
		return failure.getCause() instanceof ConstraintViolationException violation
				&& violation.getKind() == ConstraintKind.UNIQUE;
	}

}
