package com.blueharvest.pricingexception.request.adapter.in.web;

import com.blueharvest.pricingexception.request.application.port.out.UserDirectory;
import com.blueharvest.pricingexception.request.domain.Actor;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class UserController {

	private final UserDirectory users;

	UserController(UserDirectory users) {
		this.users = users;
	}

	@GetMapping("/me")
	UserResponse me(Actor caller) {
		return UserResponse.from(caller);
	}

	@GetMapping("/users")
	List<UserResponse> users() {
		return users.findAll().stream().map(UserResponse::from).toList();
	}

}
