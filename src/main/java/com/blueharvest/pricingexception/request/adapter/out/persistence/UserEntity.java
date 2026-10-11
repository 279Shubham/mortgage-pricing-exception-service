package com.blueharvest.pricingexception.request.adapter.out.persistence;

import com.blueharvest.pricingexception.request.domain.Actor;
import com.blueharvest.pricingexception.request.domain.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "app_user")
class UserEntity {

	@Id
	private String id;

	@Column(name = "display_name")
	private String displayName;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "role")
	private Set<String> roles;

	protected UserEntity() {
	}

	Actor toActor() {
		Set<Role> actorRoles = roles.stream().map(Role::valueOf).collect(Collectors.toSet());
		return new Actor(id, displayName, actorRoles);
	}

}
