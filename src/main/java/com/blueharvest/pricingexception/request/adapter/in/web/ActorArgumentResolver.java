package com.blueharvest.pricingexception.request.adapter.in.web;

import com.blueharvest.pricingexception.request.application.port.out.UserDirectory;
import com.blueharvest.pricingexception.request.domain.Actor;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ActorArgumentResolver implements HandlerMethodArgumentResolver {

	public static final String USER_ID_HEADER = "X-User-Id";

	private final UserDirectory users;

	ActorArgumentResolver(UserDirectory users) {
		this.users = users;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType().equals(Actor.class);
	}

	@Override
	public Actor resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		return Optional.ofNullable(webRequest.getHeader(USER_ID_HEADER))
			.flatMap(users::findById)
			.orElseThrow(UnknownCallerException::new);
	}

}
