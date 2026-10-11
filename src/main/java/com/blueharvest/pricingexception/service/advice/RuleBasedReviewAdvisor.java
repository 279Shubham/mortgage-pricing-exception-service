package com.blueharvest.pricingexception.service.advice;

import com.blueharvest.pricingexception.model.Advice;
import com.blueharvest.pricingexception.model.AdviceRecommendation;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedReviewAdvisor implements ReviewAdvisor {

	private final int highDiscountBps;

	private final int minReasonLength;

	RuleBasedReviewAdvisor(@Value("${pricing.advice.high-discount-bps}") int highDiscountBps,
			@Value("${pricing.advice.min-reason-length}") int minReasonLength) {
		this.highDiscountBps = highDiscountBps;
		this.minReasonLength = minReasonLength;
	}

	@Override
	public Advice advise(int discountBps, String reason) {
		List<String> concerns = concerns(discountBps, reason);
		if (concerns.isEmpty()) {
			return new Advice(AdviceRecommendation.APPROVE,
					"The discount and the length of the reason are within the advisory thresholds.");
		}
		return new Advice(AdviceRecommendation.REVIEW_CAREFULLY,
				"Review carefully: " + String.join("; ", concerns) + ".");
	}

	private List<String> concerns(int discountBps, String reason) {
		List<String> concerns = new ArrayList<>();
		if (discountBps > highDiscountBps) {
			concerns.add("the discount of " + discountBps + " bps is above the advisory threshold of "
					+ highDiscountBps + " bps");
		}
		if (reason.length() < minReasonLength) {
			concerns.add("the reason has " + reason.length() + " characters, fewer than the advisory minimum of "
					+ minReasonLength);
		}
		return concerns;
	}

}
