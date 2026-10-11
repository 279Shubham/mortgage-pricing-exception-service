package com.blueharvest.pricingexception.repository;

import com.blueharvest.pricingexception.model.RequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores the history of each request. The history is append-only, and ids increase in insert
 * order, so they give a stable order even when two events carry the same timestamp.
 *
 * <p>This is the database abstraction: Spring Data generates the implementation, so moving from
 * H2 to Postgres is a datasource configuration change (URL, driver, migrations checked), not a
 * code change.
 */
public interface RequestEventRepository extends JpaRepository<RequestEvent, Long> {

}
