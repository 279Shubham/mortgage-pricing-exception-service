package com.blueharvest.pricingexception.request.domain;

import java.util.Set;

public record Actor(String id, String displayName, Set<Role> roles) {

	public Actor {
		roles = Set.copyOf(roles);
	}

}
