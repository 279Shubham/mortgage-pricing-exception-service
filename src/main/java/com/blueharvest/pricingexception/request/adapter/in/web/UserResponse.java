package com.blueharvest.pricingexception.request.adapter.in.web;

import com.blueharvest.pricingexception.request.domain.Actor;
import com.blueharvest.pricingexception.request.domain.Role;
import java.util.List;

record UserResponse(String id, String displayName, List<Role> roles) {

	static UserResponse from(Actor actor) {
		List<Role> sortedRoles = actor.roles().stream().sorted().toList();
		return new UserResponse(actor.id(), actor.displayName(), sortedRoles);
	}

}
