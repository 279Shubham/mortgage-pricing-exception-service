package com.blueharvest.pricingexception.controller;

import com.blueharvest.pricingexception.dto.UserResponse;
import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.service.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class UserController {

	private final UserService userService;

	UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	UserResponse me(Actor caller) {
		return UserResponse.from(caller);
	}

	@GetMapping("/users")
	List<UserResponse> users() {
		return userService.findAll().stream().map(UserResponse::from).toList();
	}

}
