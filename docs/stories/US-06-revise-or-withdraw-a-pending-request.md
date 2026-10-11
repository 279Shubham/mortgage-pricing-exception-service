# US-06 Revise or withdraw a pending request

As a relationship manager, I want to revise or withdraw my pending request, so that I can change course without weakening what a reviewer approves: every decision stays bound to the exact revision the reviewer saw.

## Acceptance criteria

- **AC-1:** The requester revises a pending request by sending a new discount and reason. The request gets a new revision with the next number and its own advice snapshot. Earlier revisions keep their original content and read as superseded.
- **AC-2:** A decision always binds to the revision the reviewer saw. Approving or declining with a superseded revision number gets 409 and leaves no approved discount. Approving the current revision approves that revision's discount.
- **AC-3:** The requester withdraws a pending request. A withdrawn request can no longer be decided or revised, and the application accepts a new request.
- **AC-4:** Anyone other than the requester gets 403 on revise or withdraw. Revising or withdrawing a request that is not pending gets 409, and an unknown request gets 404. In every case the request is unchanged.
- **AC-5:** A revision obeys the same rules as a submission: a discount of 1 to 200 bps below the standard rate, and a reason of 10 to 1000 characters without control characters. An invalid revision gets 400 and creates nothing.
- **AC-6:** Revise and withdraw are safe to retry: the same `Idempotency-Key` and body returns the original response and records the change once.
- **AC-7:** When a decision, a revision and a withdrawal reach the same request at the same moment, only one of them is stored and nothing is half-written. The caller that loses gets an error.

## Tests

End-to-end tests: `ReviseWithdrawE2ETest`. Every test name starts with the criterion it proves.

No test exists yet. A criterion gets its row in this table when its tests are written.

| Criterion | Proved by |
|-----------|-----------|
| AC-7 | No automated test: two changes only overlap by timing, so a test for it would be flaky. It is checked on a running service instead, and the counts are recorded under Verification evidence |

## Plan

The story is delivered in three thin slices. In the first two the failing tests are committed before the code:

1. **The revise path.** `POST /pricing-exceptions/{id}/revise` takes a new discount and reason and gives the request its next revision, with an advice snapshot computed for the new values and a history event, all in one transaction. The call goes through the same retry protection as submit and decide.
2. **Withdraw, the rules, the binding, retries and the list of revisions.** `POST /pricing-exceptions/{id}/withdraw` closes a pending request and frees the application for a new one. Only the requester may revise or withdraw, and only while the request is pending. A decision that states a revision that has since been superseded is refused, proven through the real revise call. Both calls replay on a retry. `GET /pricing-exceptions/{id}/revisions` lists every revision with its original content and marks the superseded ones.
3. **Concurrent changes.** A decision, a revision and a withdrawal all change the same versioned request row, so only one of them can be stored. What the losing caller gets is stated in the code and in the README, and the one-winner behaviour is checked on a running service.

## Review notes

Not reviewed yet.

## Verification evidence

Not captured yet: the story is still open.
