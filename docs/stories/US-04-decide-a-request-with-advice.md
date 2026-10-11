# US-04 Decide a request with advice

As a reviewer, I want to work the pending queue with non-binding advice and approve or decline each request under four-eyes rules, so that every discount is decided by someone other than its requester, for exactly the revision I reviewed.

## Acceptance criteria

- **AC-1:** A reviewer lists requests filtered by status. Each pending request shows its exact revision number, the discount and the advice snapshot (recommendation and rationale).
- **AC-2:** The advice is non-binding. It flags a discount above 50 bps and a reason shorter than 30 characters. Changing `pricing.advice.high-discount-bps` or `pricing.advice.min-reason-length` changes the advice.
- **AC-3:** A reviewer approves a pending request by stating the revision number reviewed. The request then shows APPROVED, the reviewer and the decision time. A revision number that is not the current revision gets 409.
- **AC-4:** A reviewer declines a pending request with a required note; a missing note gets 400. The request then shows DECLINED, the reviewer, the decision time and the note.
- **AC-5:** The dual-role user gets 403 when deciding their own request, an RM-only user gets 403 when deciding at all, and deciding an already approved or declined request gets 409. In every case the request is unchanged.
- **AC-6:** Approve and decline are safe to retry: the same `Idempotency-Key` and body returns the original response and records the decision once.

## Tests

End-to-end tests: `DecideRequestE2ETest` and `AdviceThresholdsE2ETest`. Every test name starts with the criterion it proves.

A criterion gets its row in this table when its tests are written, so a criterion without a row is not proved yet.

| Criterion | Proved by |
|-----------|-----------|
| AC-1 | `a reviewer lists requests filtered by status`<br>`a missing or unknown status filter gets 400` |
| AC-3 | `a reviewer approves a pending request by stating the revision reviewed`<br>`an approve that does not state the revision gets 400`<br>`a revision number that is not the current revision gets 409`<br>`deciding an unknown request gets 404` |
| AC-4 | `a reviewer declines a pending request with a note`<br>`a decline without a note gets 400` |
| AC-5 | `the dual-role user cannot decide their own request`<br>`an RM-only user cannot decide at all`<br>`deciding an already approved or declined request gets 409` |
| AC-6 | `a retried approve replays the original response and records one decision`<br>`a retried decline replays the original response and records one decision` |

## Plan

The story is delivered in three thin slices, each one tested before the next starts:

1. **The approve path.** `POST /pricing-exceptions/{id}/approve` states the revision number the reviewer looked at. The decision (who, when, which revision) is stored on the request, and an `APPROVED` history event is written in the same transaction. The call goes through the same idempotency wrapper as submit, so it is retry-safe from the first commit. The decision time comes from the injected clock.
2. **Decline, the rules, retries and the queue.** `POST /pricing-exceptions/{id}/decline` needs a note. Before anything is changed the service checks, in this order: the caller is a reviewer (403), the request exists (404), the caller is not its requester (403), the request is still pending (409), and the stated revision is the current one (409). A refused decision leaves the request and its history untouched. A retry with the same key and body replays the stored response. `GET /pricing-exceptions?status=` lists the requests in one status, oldest first, which gives reviewers their pending queue.
3. **The advice snapshot.** A `ReviewAdvisor` is asked once, when a revision is created, and its answer (a recommendation and a rationale) is stored with that revision, so a reviewer always sees the advice that was given for exactly that revision. The rule-based advisor flags a discount above `pricing.advice.high-discount-bps` (default 50) and a reason shorter than `pricing.advice.min-reason-length` (default 30). The advice never blocks a decision. `ReviewAdvisor` is an interface so that another advisor, for example an LLM-backed one, can replace the rules without touching the callers.

Product assumptions, in plain words: any known user may list requests, as with reading one by id; an approval carries no note; a decline note is 1 to 1000 characters; the advisory thresholds are separate from the hard limits on a discount (`pricing.discount.*`), which still reject a submission outright.

## Review notes

Not reviewed yet.

## Verification evidence

Not captured yet: the story is still open.
