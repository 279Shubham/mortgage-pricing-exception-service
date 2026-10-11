package com.blueharvest.pricingexception.repository;

import com.blueharvest.pricingexception.model.PricingExceptionRequest;
import com.blueharvest.pricingexception.model.RequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores pricing exception requests. Callers insert with {@code saveAndFlush}, so the database's
 * one-pending-request-per-application constraint fails at the call site and not at commit. Every
 * row carries an optimistic-lock version, so two writers cannot both change the same request.
 * The listing by status returns the oldest submission first, which is the order of a review queue.
 * The lookup by application and status returns the request decided most recently, which for
 * {@code APPROVED} is the approval in effect for that application. The lookup by application that
 * leaves one request out returns the most recently submitted of the application's other requests,
 * whatever its status. A caller that has just inserted a request passes that request's id, and so
 * reads its predecessor as the database holds it after the insert.
 *
 * <p>This is the database abstraction: Spring Data generates the implementation, so moving from
 * H2 to Postgres is a datasource configuration change (URL, driver, migrations checked), not a
 * code change.
 */
public interface PricingExceptionRequestRepository extends JpaRepository<PricingExceptionRequest, UUID> {

	List<PricingExceptionRequest> findByStatusOrderBySubmittedAtAsc(RequestStatus status);

	Optional<PricingExceptionRequest> findFirstByApplicationIdAndStatusOrderByDecidedAtDesc(String applicationId,
			RequestStatus status);

	Optional<PricingExceptionRequest> findFirstByApplicationIdAndIdNotOrderBySubmittedAtDesc(String applicationId,
			UUID id);

}
