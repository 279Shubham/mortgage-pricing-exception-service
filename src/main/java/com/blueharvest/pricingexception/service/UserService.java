package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.model.User;
import com.blueharvest.pricingexception.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository users;

	public UserService(UserRepository users) {
		this.users = users;
	}

	public Optional<Actor> findById(String userId) {
		return users.findById(userId).map(User::toActor);
	}

	public List<Actor> findAll() {
		return users.findAll(Sort.by("id")).stream().map(User::toActor).toList();
	}

}
