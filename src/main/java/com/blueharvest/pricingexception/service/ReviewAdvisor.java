package com.blueharvest.pricingexception.service;

import com.blueharvest.pricingexception.model.Advice;

/**
 * Gives a reviewer a recommendation and a rationale for one revision of a pricing exception
 * request. The advice is non-binding: it never blocks or makes a decision, a human reviewer always
 * decides.
 *
 * <p>The contract: {@code advise} has no side effects, returns an advice for every input that
 * passed submit validation, and does not throw. The rationale is at most 500 characters. It is
 * called once, when a revision is created, and the answer is stored with that revision, so a
 * reviewer always sees the advice that was given for exactly the revision in front of them.
 *
 * <p>To replace the rules, register another bean that implements this interface, for example an
 * LLM-backed advisor, in place of {@code RuleBasedReviewAdvisor}. A replacement that calls a
 * remote service must turn its own failures and timeouts into an advice, because a submit must
 * not fail on its advisor.
 */
public interface ReviewAdvisor {

	Advice advise(int discountBps, String reason);

}
