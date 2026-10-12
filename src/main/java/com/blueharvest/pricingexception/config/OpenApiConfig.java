package com.blueharvest.pricingexception.config;

import com.blueharvest.pricingexception.model.Actor;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

	private static final String OPEN_PATH = "/users";

	private static final String PROBLEM = "ProblemDetail";

	private static final Map<String, String> ERRORS = Map.of(
			"400", "Invalid input. The detail says what was wrong.",
			"401", "Missing or unknown X-User-Id.",
			"403", "The caller's role does not allow this, or the request belongs to someone else.",
			"404", "Unknown request or application.",
			"409", "A rule stands in the way: the request is no longer pending, the stated revision is not the"
					+ " current one, the application already has a pending request, or a call with the same key is"
					+ " still in progress.",
			"422", "The Idempotency-Key was already used with a different request.");

	static {
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(Actor.class);
	}

	@Bean
	GlobalOpenApiCustomizer callerHeaderAndErrorsOnEveryProtectedOperation() {
		return openApi -> {
			openApi.info(new Info().title("Mortgage pricing exceptions").version("1"));
			openApi.getComponents().addSchemas(PROBLEM, problemSchema());
			openApi.getPaths().forEach(OpenApiConfig::describe);
		};
	}

	private static void describe(String path, PathItem item) {
		if (OPEN_PATH.equals(path)) {
			return;
		}
		item.readOperationsMap().forEach((method, operation) -> {
			addCallerHeader(operation);
			errorsOf(path, method, operation)
				.forEach(code -> operation.getResponses().addApiResponse(code, problem(ERRORS.get(code))));
		});
	}

	private static List<String> errorsOf(String path, PathItem.HttpMethod method, Operation operation) {
		boolean write = method == PathItem.HttpMethod.POST;
		boolean namesOneResource = write || path.contains("{id}");
		boolean takesInput = namesOneResource || hasQueryParameter(operation);
		List<String> codes = new ArrayList<>();
		if (takesInput) {
			codes.add("400");
		}
		codes.add("401");
		if (write) {
			codes.add("403");
		}
		if (namesOneResource) {
			codes.add("404");
		}
		if (write) {
			codes.addAll(List.of("409", "422"));
		}
		return codes;
	}

	private static boolean hasQueryParameter(Operation operation) {
		return operation.getParameters().stream().anyMatch(parameter -> "query".equals(parameter.getIn()));
	}

	private static ApiResponse problem(String description) {
		Schema<?> problem = new Schema<>().$ref("#/components/schemas/" + PROBLEM);
		return new ApiResponse().description(description)
			.content(new Content().addMediaType("application/problem+json", new MediaType().schema(problem)));
	}

	private static Schema<?> problemSchema() {
		return new ObjectSchema()
			.addProperty("title", new StringSchema())
			.addProperty("status", new IntegerSchema())
			.addProperty("detail", new StringSchema())
			.addProperty("instance", new StringSchema());
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
