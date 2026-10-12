package com.blueharvest.pricingexception.repository;

import com.blueharvest.pricingexception.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only access to the seeded users and their roles; the service never creates, changes or
 * deletes users.
 *
 * <p>This is the database abstraction: Spring Data generates the implementation, so moving from
 * H2 to another relational database is a datasource configuration change, not a code change.
 */
public interface UserRepository extends JpaRepository<User, String> {

}
