package com.blueharvest.pricingexception.repository;

import com.blueharvest.pricingexception.model.IdempotencyRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores idempotency claims: one record per user, route and key, enforced by a database UNIQUE
 * constraint. A concurrent insert of the same claim waits for the first transaction, then fails if
 * that transaction committed or goes ahead if it rolled back. A record holds the response of a
 * committed 2xx request only. A key is live while now is before its expiry, and from the expiry on
 * it is free for reuse; expired records are removed when the same key is used again, not by a
 * background job.
 *
 * <p>This is the database abstraction: Spring Data generates the implementation, so moving from
 * H2 to another relational database is a datasource configuration change. That database needs a
 * lock timeout so an in-flight retry gets 409 rather than waiting. A Redis-backed store would
 * replace this interface's caller, {@code IdempotencyService}.
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

	Optional<IdempotencyRecord> findByUserIdAndRouteAndIdempotencyKey(String userId, String route,
			String idempotencyKey);

	@Transactional
	@Modifying
	@Query("delete from IdempotencyRecord r where r.userId = :userId and r.route = :route"
			+ " and r.idempotencyKey = :idempotencyKey and r.expiresAt <= :now")
	int deleteExpired(String userId, String route, String idempotencyKey, Instant now);

}
