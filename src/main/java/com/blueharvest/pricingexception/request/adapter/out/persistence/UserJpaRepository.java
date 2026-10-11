package com.blueharvest.pricingexception.request.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<UserEntity, String> {

}
