package com.blueharvest.pricingexception.repository;

import com.blueharvest.pricingexception.model.RequestEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores the history of each request. The history is append-only, and ids increase in insert
 * order, so they give a stable order even when two events carry the same timestamp.
 *
 * <p>This is the database abstraction: Spring Data generates the implementation, so moving from
 * H2 to another relational database is a datasource configuration change (URL, driver, migrations
 * checked), not a code change.
 */
public interface RequestEventRepository extends JpaRepository<RequestEvent, Long> {

	List<RequestEvent> findByRequestIdOrderByIdAsc(UUID requestId);

}
