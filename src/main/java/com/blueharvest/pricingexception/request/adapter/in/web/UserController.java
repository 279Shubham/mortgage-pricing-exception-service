package com.blueharvest.pricingexception.request.adapter.in.web;

import com.blueharvest.pricingexception.request.domain.Actor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class UserController {

	@GetMapping("/me")
	UserResponse me(Actor caller) {
		return UserResponse.from(caller);
	}

}
