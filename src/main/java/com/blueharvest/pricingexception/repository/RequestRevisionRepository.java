package com.blueharvest.pricingexception.repository;

import com.blueharvest.pricingexception.model.RequestRevision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores the revisions of each request. Revisions are immutable and append-only: there is exactly
 * one row per request and revision number, and a stored revision is never changed or deleted.
 *
 * <p>The list of a request's revisions is ordered by revision number, oldest first. A revision is
 * superseded once the request's current revision number is higher than its own. That is derived
 * from the request when the list is read and is never written to the revision row.
 *
 * <p>This is the database abstraction: Spring Data generates the implementation, so moving from
 * H2 to another relational database is a datasource configuration change (URL, driver, migrations
 * checked), not a code change.
 */
public interface RequestRevisionRepository extends JpaRepository<RequestRevision, UUID> {

	Optional<RequestRevision> findByRequestIdAndRevisionNumber(UUID requestId, int revisionNumber);

	List<RequestRevision> findByRequestIdOrderByRevisionNumberAsc(UUID requestId);

}
