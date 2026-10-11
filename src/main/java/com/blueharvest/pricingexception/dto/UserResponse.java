package com.blueharvest.pricingexception.dto;

import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.model.Role;
import java.util.List;

public record UserResponse(String id, String displayName, List<Role> roles) {

	public static UserResponse from(Actor actor) {
		List<Role> sortedRoles = actor.roles().stream().sorted().toList();
		return new UserResponse(actor.id(), actor.displayName(), sortedRoles);
	}

}
