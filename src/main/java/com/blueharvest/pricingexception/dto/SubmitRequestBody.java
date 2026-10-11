package com.blueharvest.pricingexception.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitRequestBody(@NotNull Integer discountBps,
		@NotBlank @Size(min = 10, max = 1000) @Pattern(regexp = FreeText.WITHOUT_CONTROL_CHARACTERS,
				message = FreeText.NO_CONTROL_CHARACTERS) String reason) {

}
