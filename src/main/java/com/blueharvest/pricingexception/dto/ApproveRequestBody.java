package com.blueharvest.pricingexception.dto;

import jakarta.validation.constraints.NotNull;

public record ApproveRequestBody(@NotNull Integer revisionNumber) {

}
