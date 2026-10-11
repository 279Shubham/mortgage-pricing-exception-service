package com.blueharvest.pricingexception.request.adapter.out.persistence;

import com.blueharvest.pricingexception.request.application.port.out.UserDirectory;
import com.blueharvest.pricingexception.request.domain.Actor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class JpaUserDirectory implements UserDirectory {

	private final UserJpaRepository users;

	JpaUserDirectory(UserJpaRepository users) {
		this.users = users;
	}

	@Override
	public Optional<Actor> findById(String userId) {
		return users.findById(userId).map(UserEntity::toActor);
	}

	@Override
	public List<Actor> findAll() {
		return users.findAll(Sort.by("id")).stream().map(UserEntity::toActor).toList();
	}

}
