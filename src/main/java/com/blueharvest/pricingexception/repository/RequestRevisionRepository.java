package com.blueharvest.pricingexception.repository;

import com.blueharvest.pricingexception.model.RequestRevision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores the revisions of each request. Revisions are immutable and append-only: there is exactly
 * one row per request and revision number, and a stored revision is never changed or deleted.
 *
 * <p>This is the database abstraction: Spring Data generates the implementation, so moving from
 * H2 to Postgres is a datasource configuration change (URL, driver, migrations checked), not a
 * code change.
 */
public interface RequestRevisionRepository extends JpaRepository<RequestRevision, UUID> {

	Optional<RequestRevision> findByRequestIdAndRevisionNumber(UUID requestId, int revisionNumber);

}
