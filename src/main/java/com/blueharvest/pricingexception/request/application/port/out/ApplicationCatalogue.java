package com.blueharvest.pricingexception.request.application.port.out;

import com.blueharvest.pricingexception.request.domain.MortgageApplication;
import java.util.List;

/**
 * Provides the existing mortgage applications that a pricing exception can be requested for.
 *
 * <p>Contract: {@link #findAll()} returns every known mortgage application, ordered by id
 * ascending. Each standard rate is an integer number of basis points greater than 0. The
 * catalogue is read-only; the service never creates, changes or removes applications through it.
 * The result is never null; it is an empty list when there are no applications.
 *
 * <p>Replacing the local adapter: the local implementation reads the seeded
 * {@code mortgage_application} rows. In production, provide another implementation that calls
 * the mortgage-origination system's application API, with a cache in front of it if needed.
 * Callers depend only on this interface, so no other code changes.
 */
public interface ApplicationCatalogue {

	List<MortgageApplication> findAll();

}
