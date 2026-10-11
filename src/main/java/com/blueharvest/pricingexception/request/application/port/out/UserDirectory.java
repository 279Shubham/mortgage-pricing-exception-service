package com.blueharvest.pricingexception.request.application.port.out;

import com.blueharvest.pricingexception.request.domain.Actor;
import java.util.List;
import java.util.Optional;

/**
 * Looks up the internal users who may call the service.
 *
 * <p>Contract: {@link #findById(String)} returns the user with all of their roles, or an empty
 * result when the id is unknown or blank. Ids are matched exactly (case-sensitive).
 * {@link #findAll()} returns every user with all of their roles, ordered by id ascending; it is
 * used to offer a user switcher and is never null. The directory is read-only; the service never
 * creates, changes or removes users through it.
 *
 * <p>Replacing the local adapter: the local implementation reads the seeded {@code app_user} and
 * {@code app_user_role} rows. In production, provide another implementation that resolves the
 * caller from the subject of a verified OAuth2/OIDC token and maps identity-provider groups to
 * {@link com.blueharvest.pricingexception.request.domain.Role}. List users from the identity
 * provider's directory or SCIM API, or drop the listing once callers authenticate for real.
 * Callers depend only on this interface, so no other code changes.
 */
public interface UserDirectory {

	Optional<Actor> findById(String userId);

	List<Actor> findAll();

}
