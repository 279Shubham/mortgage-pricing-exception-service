package com.blueharvest.pricingexception.request.adapter.in.web;

import com.blueharvest.pricingexception.request.application.port.out.UserDirectory;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

	private final UserDirectory users;

	WebConfig(UserDirectory users) {
		this.users = users;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new ActorArgumentResolver(users));
	}

}
