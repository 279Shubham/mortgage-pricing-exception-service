package com.blueharvest.pricingexception.web;

import com.blueharvest.pricingexception.service.UserService;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

	private static final List<String> OPEN_PATHS = List.of("/users", "/actuator/health/**", "/error");

	private final UserService users;

	WebConfig(UserService users) {
		this.users = users;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new CallerInterceptor(users)).excludePathPatterns(OPEN_PATHS);
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new ActorArgumentResolver());
	}

}
