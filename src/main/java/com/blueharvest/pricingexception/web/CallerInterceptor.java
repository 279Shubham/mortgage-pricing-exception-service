package com.blueharvest.pricingexception.web;

import com.blueharvest.pricingexception.model.Actor;
import com.blueharvest.pricingexception.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

class CallerInterceptor implements HandlerInterceptor {

	static final String USER_ID_HEADER = "X-User-Id";

	static final String CALLER_ATTRIBUTE = Actor.class.getName();

	private final UserService users;

	CallerInterceptor(UserService users) {
		this.users = users;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (handler instanceof HandlerMethod) {
			request.setAttribute(CALLER_ATTRIBUTE, resolveCaller(request));
		}
		return true;
	}

	private Actor resolveCaller(HttpServletRequest request) {
		return Optional.ofNullable(request.getHeader(USER_ID_HEADER))
			.flatMap(users::findById)
			.orElseThrow(UnknownCallerException::new);
	}

}
