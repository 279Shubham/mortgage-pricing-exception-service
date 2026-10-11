package com.blueharvest.pricingexception.web;

import com.blueharvest.pricingexception.model.Actor;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ActorArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType().equals(Actor.class);
	}

	@Override
	public Actor resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		Object caller = webRequest.getAttribute(CallerInterceptor.CALLER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		return Optional.ofNullable(caller).map(Actor.class::cast).orElseThrow(UnknownCallerException::new);
	}

}
