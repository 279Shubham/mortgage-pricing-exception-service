package com.blueharvest.pricingexception.config;

import com.blueharvest.pricingexception.model.Actor;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

	private static final String OPEN_PATH = "/users";

	static {
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(Actor.class);
	}

	@Bean
	GlobalOpenApiCustomizer callerHeaderOnEveryProtectedOperation() {
		return openApi -> {
			openApi.info(new Info().title("Mortgage pricing exceptions").version("1"));
			openApi.getPaths().forEach((path, item) -> {
				if (!OPEN_PATH.equals(path)) {
					item.readOperations().forEach(OpenApiConfig::addCallerHeader);
				}
			});
		};
	}

	private static void addCallerHeader(Operation operation) {
		HeaderParameter caller = new HeaderParameter();
		caller.name("X-User-Id").required(true).description("A seeded user id, for example rm-lana")
			.schema(new StringSchema());
		if (operation.getParameters() == null) {
			operation.addParametersItem(caller);
		}
		else {
			operation.getParameters().add(0, caller);
		}
	}

}
