package com.blueharvest.pricingexception.repository;

import com.blueharvest.pricingexception.model.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores idempotency claims: one record per user, route and key, enforced by a database UNIQUE
 * constraint. A concurrent insert of the same claim waits for the first transaction, then fails if
 * that transaction committed or goes ahead if it rolled back. A record holds the response of a
 * committed 2xx request only.
 *
 * <p>This is the database abstraction: Spring Data generates the implementation, so moving from
 * H2 to Postgres is a datasource configuration change. Postgres needs a {@code lock_timeout} so an
 * in-flight retry gets 409 rather than waiting. A Redis-backed store would replace this
 * interface's caller, {@code IdempotencyService}.
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

	Optional<IdempotencyRecord> findByUserIdAndRouteAndIdempotencyKey(String userId, String route,
			String idempotencyKey);

}
