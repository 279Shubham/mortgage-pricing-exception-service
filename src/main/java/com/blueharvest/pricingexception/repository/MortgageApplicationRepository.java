package com.blueharvest.pricingexception.repository;

import com.blueharvest.pricingexception.model.MortgageApplication;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only access to the seeded mortgage applications; the service never creates, changes or
 * deletes them.
 *
 * <p>This is the database abstraction: Spring Data generates the implementation, so moving from
 * H2 to another relational database is a datasource configuration change (URL, driver, migrations
 * checked), not a code change.
 */
public interface MortgageApplicationRepository extends JpaRepository<MortgageApplication, String> {

}
