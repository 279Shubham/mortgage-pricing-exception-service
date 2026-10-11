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

| Criterion | Proved by |
|-----------|-----------|
| AC-1 | `a reviewer lists requests filtered by status`<br>`a missing or unknown status filter gets 400`<br>The advice snapshot in the queue is read by the first AC-2 test |
| AC-2 | `a pending request shows an advice snapshot that flags a discount above 50 bps and a short reason`<br>`a discount of exactly 50 bps and a reason of exactly 30 characters are not flagged`<br>`advice is non-binding, a flagged request can still be approved`<br>`changing the configured thresholds changes the advice` (`AdviceThresholdsE2ETest`) |
| AC-3 | `a reviewer approves a pending request by stating the revision reviewed`<br>`an approve that does not state the revision gets 400`<br>`a revision number that is not the current revision gets 409`<br>`an approval of a revision the request has moved on from gets 409`<br>`deciding an unknown request gets 404` |
| AC-4 | `a reviewer declines a pending request with a note`<br>`a decline without a note gets 400`<br>`a decline note with a control character gets 400`<br>`a 1000-character reason and note of quotes and line breaks are declined and replayed` |
| AC-5 | `the dual-role user cannot decide their own request`<br>`an RM-only user cannot decide at all`<br>`deciding an already approved or declined request gets 409` |
| AC-6 | `a retried approve replays the original response and records one decision`<br>`a retried decline replays the original response and records one decision` |

`AdviceThresholdsE2ETest` starts the service a second time with `pricing.advice.high-discount-bps=20` and `pricing.advice.min-reason-length=60`. It is the only test class that does not share the suite's single application context.

## Plan

The story was delivered in three thin slices. In each one the failing tests were committed before the code:

1. **The approve path** (failing test `1e51f08`, then code `d699339`). `POST /pricing-exceptions/{id}/approve` states the revision number the reviewer looked at. The decision (who, when, which revision) is stored on the request, and an `APPROVED` history event is written in the same transaction. The call goes through the same idempotency wrapper as submit, so it was retry-safe from its first commit. The decision time comes from the injected clock.
2. **Decline, the rules, retries and the queue** (failing tests `6471dbd`, then code `eb358a3` for decline and the rules and `6ac2696` for the queue). `POST /pricing-exceptions/{id}/decline` needs a note. Before anything is changed the service checks, in this order: the caller is a reviewer (403), the request exists (404), the caller is not its requester (403), the request is still pending (409), and the stated revision is the current one (409). A refused decision leaves the request and its history untouched. A retry with the same key and body replays the stored response. `GET /pricing-exceptions?status=` lists the requests in one status, oldest first, which gives reviewers their pending queue. Of the twelve tests in `6471dbd`, ten failed and two passed on arrival (the retried approve, and the approve without a revision), because slice 1 had already put approve behind the idempotency wrapper and bean validation.
3. **The advice snapshot** (failing tests `9e45f57`, then code `67e1ef5`). A `ReviewAdvisor` is asked once, when a revision is created, and its answer (a recommendation and a rationale) is stored with that revision, so a reviewer always sees the advice that was given for exactly that revision. The rule-based advisor flags a discount above `pricing.advice.high-discount-bps` (default 50) and a reason shorter than `pricing.advice.min-reason-length` (default 30). The advice never blocks a decision. `ReviewAdvisor` is an interface so that another advisor, for example an LLM-backed one, can replace the rules without touching the callers.

Product assumptions, in plain words: any known user may list requests, as with reading one by id; an approval carries no note; a decline note is 1 to 1000 characters and may hold line breaks and tabs but no other control character; the advisory thresholds are separate from the hard limits on a discount (`pricing.discount.*`), which still reject a submission outright.

**Not in this story.** Two reviewers deciding the same request at the same moment: the request row carries an optimistic-lock version, so only one decision is stored, but the loser's error is not yet turned into a clean 409. That belongs to the story that adds revise and withdraw, where all the races on one request are handled together.

## Review notes

The story's code was reviewed once, together with US-05, at commit `7d23ed6`. The review found no critical issues. It confirmed, on a running service, that the five checks run in the documented order, that the decision, its history event and its idempotency record are one transaction, and that 80 pairs of concurrent decisions never stored two decisions. Each fix below is its own commit, and where behaviour changed the failing test was committed first. After the fixes the build is green with 112 tests, 20 of them for this story (commit `299aeab`).

| Finding | Resolution | Commit |
|---------|------------|--------|
| A decline that passed validation failed with 500: a reason and a note made of control characters grow sixfold in JSON, and the stored response no longer fitted the idempotency record | Control characters other than line breaks and tabs are refused with 400, in a reason and in a decline note. The largest response that can still be stored is pinned by a test and is replayed on a retry | `58ae4c8` (tests), `544016e` (code) |
| Every request had exactly one revision, so no test could tell "the stated revision is the current one" from "the stated revision exists" | A test moves a pending request to revision 2 in the database: approving revision 1 gets 409 and leaves it pending, approving revision 2 is accepted. The code was already right, so the test passed on arrival | `1bd22a7` |
| The four-eyes rule and the binding of a decision to a revision existed only in service code; the database accepted a self-approved request bound to a revision that does not exist | Migration V9 adds four constraints: a decision is complete or absent, the decider is not the requester, the decided revision exists, and the advice recommendation is a known value | `0b52d55` |
| The migration that adds the advice columns only worked on an empty table | V7 now adds the columns as nullable, backfills existing revisions and then sets NOT NULL. It was rewritten in place, because it had only ever run on in-memory databases | `8f42633` |
| A decision took any request status as its outcome, so `PENDING` or `WITHDRAWN` compiled, and the history event type was found by enum name | A two-value `DecisionOutcome` (approve, decline) carries both the status and the event type | `299aeab` |

The two migration fixes and the outcome type have no test of their own: the constraints and the backfill cannot be reached over HTTP, and the outcome type changes no behaviour. The migrations were checked by replaying V1 to V9 in a throwaway H2 database in PostgreSQL mode: the old V7 failed on a table with one row and the new one backfilled it, and V9 accepted four valid rows and refused eight invalid ones, each by the intended constraint.

**Left open, by decision.**

- Two reviewers deciding the same request at the same moment: one decision is stored and the other caller gets a 500 that is not a Problem Details body. A retry with the same key then gets a clean 409. This is the item under "Not in this story" above.
- Two of the five check orderings are not pinned by a test (requester before pending, pending before revision), and no test shows the dual-role user deciding someone else's request or an approve with a note getting 400. All four were confirmed on a running service.
- The short-reason advice rule counts padding, so a reason of one letter and 29 spaces is not flagged. A replacement advisor that returns no recommendation or a rationale over 500 characters fails the submit with a 500.
- The advisor is called inside the submit transaction. That is fine for the rules, and has to be revisited before a slow, remote advisor replaces them.
- The request lookup and the current-revision lookup are written twice, in the two services.
- The V9 rule "a decision is complete or absent" expects a withdrawn request to carry no decision. The withdraw story has to adjust it if a withdrawal fills those columns.

## Verification evidence

All evidence below is real captured output from 2026-10-11. Parts (a) to (c) were taken at commit `67e1ef5`, when the story's code was complete. Parts (d) and (e) were taken at commit `d191128`, after US-05 was built on top, which is why the responses in (e) also carry `previousRequestId`. Part (f) was taken at commit `299aeab`, after the review fixes; parts (a) to (e) are kept as they were captured, so their test counts (17 for this story, 101 and 106 in all) and the 8 migrations in (e) are those of the earlier commits.

### (a) The story's end-to-end tests

`./gradlew test --tests '*DecideRequestE2ETest' --tests '*AdviceThresholdsE2ETest' --rerun --console=plain` with JDK 21. All 17 US-04 tests passed:

```
AdviceThresholdsE2ETest > US-04 AC-2: changing the configured thresholds changes the advice PASSED
DecideRequestE2ETest > US-04 AC-3: a revision number that is not the current revision gets 409 PASSED
DecideRequestE2ETest > US-04 AC-3: deciding an unknown request gets 404 PASSED
DecideRequestE2ETest > US-04 AC-3: a reviewer approves a pending request by stating the revision reviewed PASSED
DecideRequestE2ETest > US-04 AC-2: a discount of exactly 50 bps and a reason of exactly 30 characters are not flagged PASSED
DecideRequestE2ETest > US-04 AC-4: a reviewer declines a pending request with a note PASSED
DecideRequestE2ETest > US-04 AC-6: a retried approve replays the original response and records one decision PASSED
DecideRequestE2ETest > US-04 AC-2: a pending request shows an advice snapshot that flags a discount above 50 bps and a short reason PASSED
DecideRequestE2ETest > US-04 AC-5: deciding an already approved or declined request gets 409 PASSED
DecideRequestE2ETest > US-04 AC-5: the dual-role user cannot decide their own request PASSED
DecideRequestE2ETest > US-04 AC-4: a decline without a note gets 400 PASSED
DecideRequestE2ETest > US-04 AC-6: a retried decline replays the original response and records one decision PASSED
DecideRequestE2ETest > US-04 AC-1: a missing or unknown status filter gets 400 PASSED
DecideRequestE2ETest > US-04 AC-5: an RM-only user cannot decide at all PASSED
DecideRequestE2ETest > US-04 AC-3: an approve that does not state the revision gets 400 PASSED
DecideRequestE2ETest > US-04 AC-2: advice is non-binding, a flagged request can still be approved PASSED
DecideRequestE2ETest > US-04 AC-1: a reviewer lists requests filtered by status PASSED
BUILD SUCCESSFUL in 6s
5 actionable tasks: 1 executed, 4 up-to-date
```

### (b) The whole build

`./gradlew check --rerun-tasks --console=plain` with JDK 21 ran the complexity gate, the architecture test and every end-to-end test from scratch. 101 tests ran, none failed or was skipped: the 84 that existed before this story and its 17. Counts per class, from the test report files:

| Test class | Tests | Failures | Errors |
|------------|-------|----------|--------|
| `ArchitectureTest` | 5 | 0 | 0 |
| `RunAndExploreE2ETest` | 18 | 0 | 0 |
| `SubmitRequestE2ETest` | 46 | 0 | 0 |
| `RetrySafelyE2ETest` | 15 | 0 | 0 |
| `DecideRequestE2ETest` | 16 | 0 | 0 |
| `AdviceThresholdsE2ETest` | 1 | 0 | 0 |

```
> Task :pmdMain
> Task :test
...
> Task :check
BUILD SUCCESSFUL in 10s
6 actionable tasks: 6 executed
```

### (c) The tests failed first

Each slice's tests were run before its code and failed on the behaviour that was missing, not on a compile or setup error. Captured before the code commits, with the `java.lang.AssertionError` line and the stack traces left out:

```
# before d699339
DecideRequestE2ETest > US-04 AC-3: a reviewer approves a pending request by stating the revision reviewed FAILED
    Expected status code <200> but was <404>.

# before eb358a3
DecideRequestE2ETest > US-04 AC-5: the dual-role user cannot decide their own request FAILED
    Expected status code <403> but was <200>.
DecideRequestE2ETest > US-04 AC-3: a revision number that is not the current revision gets 409 FAILED
    Expected status code <409> but was <200>.

# before 67e1ef5
AdviceThresholdsE2ETest > US-04 AC-2: changing the configured thresholds changes the advice FAILED
    JSON path currentRevision.advice.recommendation doesn't match.
    Expected: REVIEW_CAREFULLY
      Actual: null
```

### (d) `make test` in a container (commit `d191128`)

The whole build ran inside `eclipse-temurin:21-jdk-noble`. 106 tests passed and none failed: this story's 17, the 84 before it and the 5 of US-05. The lines for this story's tests and the end of the run:

```
#13 41.27 AdviceThresholdsE2ETest > US-04 AC-2: changing the configured thresholds changes the advice PASSED
...
#13 42.27 DecideRequestE2ETest > US-04 AC-3: a revision number that is not the current revision gets 409 PASSED
#13 42.27 DecideRequestE2ETest > US-04 AC-3: deciding an unknown request gets 404 PASSED
#13 42.37 DecideRequestE2ETest > US-04 AC-3: a reviewer approves a pending request by stating the revision reviewed PASSED
#13 42.47 DecideRequestE2ETest > US-04 AC-2: a discount of exactly 50 bps and a reason of exactly 30 characters are not flagged PASSED
#13 42.57 DecideRequestE2ETest > US-04 AC-4: a reviewer declines a pending request with a note PASSED
#13 42.67 DecideRequestE2ETest > US-04 AC-6: a retried approve replays the original response and records one decision PASSED
#13 42.77 DecideRequestE2ETest > US-04 AC-2: a pending request shows an advice snapshot that flags a discount above 50 bps and a short reason PASSED
#13 42.87 DecideRequestE2ETest > US-04 AC-5: deciding an already approved or declined request gets 409 PASSED
#13 42.97 DecideRequestE2ETest > US-04 AC-5: the dual-role user cannot decide their own request PASSED
#13 43.07 DecideRequestE2ETest > US-04 AC-4: a decline without a note gets 400 PASSED
#13 43.07 DecideRequestE2ETest > US-04 AC-6: a retried decline replays the original response and records one decision PASSED
#13 43.17 DecideRequestE2ETest > US-04 AC-1: a missing or unknown status filter gets 400 PASSED
#13 43.17 DecideRequestE2ETest > US-04 AC-5: an RM-only user cannot decide at all PASSED
#13 43.27 DecideRequestE2ETest > US-04 AC-3: an approve that does not state the revision gets 400 PASSED
#13 43.37 DecideRequestE2ETest > US-04 AC-2: advice is non-binding, a flagged request can still be approved PASSED
#13 43.47 DecideRequestE2ETest > US-04 AC-1: a reviewer lists requests filtered by status PASSED
...
#13 47.95 BUILD SUCCESSFUL in 47s
#13 47.95 6 actionable tasks: 6 executed
```

### (e) Submit, queue and approve on a running service (commit `d191128`)

`PORT=18083 ./gradlew bootRun` with JDK 21 ("Successfully applied 8 migrations"). The calls were made with `curl -i`; the `Date` header is left out below. The 75 bps discount is above the advisory threshold, so the advice says to review carefully, and the reviewer approves anyway.

```
# rm-lana submits 75 bps on MA-1001
# curl -i -X POST localhost:18083/applications/MA-1001/pricing-exceptions -H 'X-User-Id: rm-lana' \
#   -H 'Idempotency-Key: readme-demo-2' -H 'Content-Type: application/json' \
#   -d '{"discountBps":75,"reason":"Retention offer matching a competitor quote"}'
HTTP/1.1 201
Location: /pricing-exceptions/7fe8deca-9801-4ae4-bebe-56bb7cb7e138
Content-Type: application/json
Content-Length: 483

{"id":"7fe8deca-9801-4ae4-bebe-56bb7cb7e138","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-lana","submittedAt":"2026-10-11T19:47:01.594Z","currentRevision":{"number":1,"discountBps":75,"reason":"Retention offer matching a competitor quote","advice":{"recommendation":"REVIEW_CAREFULLY","rationale":"Review carefully: the discount of 75 bps is above the advisory threshold of 50 bps."},"createdAt":"2026-10-11T19:47:01.594Z"},"decision":null,"previousRequestId":null}

# reviewer-lex reads the pending queue
# curl -i -H 'X-User-Id: reviewer-lex' 'localhost:18083/pricing-exceptions?status=PENDING'
HTTP/1.1 200
Content-Type: application/json
Content-Length: 485

[{"id":"7fe8deca-9801-4ae4-bebe-56bb7cb7e138","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-lana","submittedAt":"2026-10-11T19:47:01.594Z","currentRevision":{"number":1,"discountBps":75,"reason":"Retention offer matching a competitor quote","advice":{"recommendation":"REVIEW_CAREFULLY","rationale":"Review carefully: the discount of 75 bps is above the advisory threshold of 50 bps."},"createdAt":"2026-10-11T19:47:01.594Z"},"decision":null,"previousRequestId":null}]

# reviewer-lex approves revision 1
# curl -i -X POST localhost:18083/pricing-exceptions/7fe8deca-9801-4ae4-bebe-56bb7cb7e138/approve \
#   -H 'X-User-Id: reviewer-lex' -H 'Idempotency-Key: readme-demo-3' -H 'Content-Type: application/json' \
#   -d '{"revisionNumber":1}'
HTTP/1.1 200
Content-Type: application/json
Content-Length: 578

{"id":"7fe8deca-9801-4ae4-bebe-56bb7cb7e138","applicationId":"MA-1001","status":"APPROVED","requestedBy":"rm-lana","submittedAt":"2026-10-11T19:47:01.594Z","currentRevision":{"number":1,"discountBps":75,"reason":"Retention offer matching a competitor quote","advice":{"recommendation":"REVIEW_CAREFULLY","rationale":"Review carefully: the discount of 75 bps is above the advisory threshold of 50 bps."},"createdAt":"2026-10-11T19:47:01.594Z"},"decision":{"decidedBy":"reviewer-lex","decidedAt":"2026-10-11T19:47:01.718Z","revisionNumber":1,"note":null},"previousRequestId":null}
```

A decline was captured in the same run, on `MA-1003`: reviewer-jonathan declined rm-chloe's 40 bps request with a note.

```
# curl -i -X POST localhost:18083/pricing-exceptions/62e5f9bb-03dc-4cc9-8d11-3a9a2de27ce2/decline \
#   -H 'X-User-Id: reviewer-jonathan' -H 'Idempotency-Key: story-demo-2' -H 'Content-Type: application/json' \
#   -d '{"revisionNumber":1,"note":"The written offer was not attached"}'
HTTP/1.1 200
Content-Type: application/json
Content-Length: 597

{"id":"62e5f9bb-03dc-4cc9-8d11-3a9a2de27ce2","applicationId":"MA-1003","status":"DECLINED","requestedBy":"rm-chloe","submittedAt":"2026-10-11T19:47:01.752Z","currentRevision":{"number":1,"discountBps":40,"reason":"Customer has a lower written offer elsewhere","advice":{"recommendation":"APPROVE","rationale":"The discount and the length of the reason are within the advisory thresholds."},"createdAt":"2026-10-11T19:47:01.752Z"},"decision":{"decidedBy":"reviewer-jonathan","decidedAt":"2026-10-11T19:47:01.772Z","revisionNumber":1,"note":"The written offer was not attached"},"previousRequestId":null}
```

The service was stopped after the calls.

### (f) After the review fixes (commit `299aeab`)

`./gradlew test --tests '*DecideRequestE2ETest' --tests '*AdviceThresholdsE2ETest' --rerun --console=plain` with JDK 21. All 20 US-04 tests passed, the 17 above and the three added by the review fixes:

```
AdviceThresholdsE2ETest > US-04 AC-2: changing the configured thresholds changes the advice PASSED
DecideRequestE2ETest > US-04 AC-3: a revision number that is not the current revision gets 409 PASSED
DecideRequestE2ETest > US-04 AC-3: deciding an unknown request gets 404 PASSED
DecideRequestE2ETest > US-04 AC-3: an approval of a revision the request has moved on from gets 409 PASSED
DecideRequestE2ETest > US-04 AC-3: a reviewer approves a pending request by stating the revision reviewed PASSED
DecideRequestE2ETest > US-04 AC-2: a discount of exactly 50 bps and a reason of exactly 30 characters are not flagged PASSED
DecideRequestE2ETest > US-04 AC-4: a reviewer declines a pending request with a note PASSED
DecideRequestE2ETest > US-04 AC-6: a retried approve replays the original response and records one decision PASSED
DecideRequestE2ETest > US-04 AC-4: a decline note with a control character gets 400 PASSED
DecideRequestE2ETest > US-04 AC-2: a pending request shows an advice snapshot that flags a discount above 50 bps and a short reason PASSED
DecideRequestE2ETest > US-04 AC-5: deciding an already approved or declined request gets 409 PASSED
DecideRequestE2ETest > US-04 AC-5: the dual-role user cannot decide their own request PASSED
DecideRequestE2ETest > US-04 AC-4: a decline without a note gets 400 PASSED
DecideRequestE2ETest > US-04 AC-6: a retried decline replays the original response and records one decision PASSED
DecideRequestE2ETest > US-04 AC-1: a missing or unknown status filter gets 400 PASSED
DecideRequestE2ETest > US-04 AC-5: an RM-only user cannot decide at all PASSED
DecideRequestE2ETest > US-04 AC-3: an approve that does not state the revision gets 400 PASSED
DecideRequestE2ETest > US-04 AC-4: a 1000-character reason and note of quotes and line breaks are declined and replayed PASSED
DecideRequestE2ETest > US-04 AC-2: advice is non-binding, a flagged request can still be approved PASSED
DecideRequestE2ETest > US-04 AC-1: a reviewer lists requests filtered by status PASSED
```

`./gradlew check --rerun-tasks --console=plain` with JDK 21 then ran the complexity gate, the architecture test and every end-to-end test from scratch. 112 tests ran, none failed or was skipped. Counts per class, from the test report files:

| Test class | Tests | Failures | Errors |
|------------|-------|----------|--------|
| `ArchitectureTest` | 5 | 0 | 0 |
| `RunAndExploreE2ETest` | 18 | 0 | 0 |
| `SubmitRequestE2ETest` | 48 | 0 | 0 |
| `RetrySafelyE2ETest` | 15 | 0 | 0 |
| `DecideRequestE2ETest` | 19 | 0 | 0 |
| `AdviceThresholdsE2ETest` | 1 | 0 | 0 |
| `ApprovedDiscountE2ETest` | 6 | 0 | 0 |

```
> Task :pmdMain
> Task :test
...
> Task :check
BUILD SUCCESSFUL in 12s
6 actionable tasks: 6 executed
```

The test log shows the schema with the new migration: `Successfully applied 9 migrations to schema "public", now at version v9`.

The control-character tests failed first. At `58ae4c8`, before the fix, three of the four new cases failed (two of them belong to US-02, for the reason): the two reasons were accepted with 201 and the decline note with 200, where 400 is expected. The fourth, the 1000-character reason and note of quotes and line breaks, passed on arrival, because it pins what is still accepted. `make test` in a container was run again at `e65f456`, after the review fixes: 112 tests passed, none failed, `BUILD SUCCESSFUL in 46s`.
