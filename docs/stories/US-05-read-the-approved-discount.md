# US-05 Read the approved discount

As the mortgage application process, I want to read the effective approved discount for an application, so that I price the mortgage with the latest approval and nothing else.

## Acceptance criteria

- **AC-1:** The application process, or any internal user, gets 200 with a list holding the latest approval for an application: `requestId`, `revision`, `discountBps`, `approvedBy`, `decidedAt`.
- **AC-2:** An application without an approval gets 200 with an empty list.
- **AC-3:** After an approval the RM can submit a new request. The existing approval stays effective until the new request is approved; then the lookup returns the newer approval.
- **AC-4:** After a decline the RM can resubmit. The new request is linked to the declined one by `previousRequestId`, and the declined request stays declined.

## Tests

End-to-end tests: `ApprovedDiscountE2ETest`. Every test name starts with the criterion it proves.

| Criterion | Proved by |
|-----------|-----------|
| AC-1 | `the application process reads the latest approval for an application`<br>`any internal user reads the same approval`<br>`the discount comes from the approved revision, not from a later revision` |
| AC-2 | `an application without an approval gets an empty list` |
| AC-3 | `a new request after an approval leaves it effective until the new one is approved` |
| AC-4 | `a resubmit after a decline is a new request linked by previousRequestId` |

## Plan

The story file was committed first (`3d33b45`), before any test or code. The story was then delivered in two thin slices. In each one the tests were committed before the code:

1. **The lookup** (failing test `50daaac`, then code `b479e03`). `GET /applications/{applicationId}/approved-discounts` returns a list with the latest approval of that application, or an empty list. The answer is always 200 and always a list, so the application process has one shape to handle and never has to tell "no approval" apart from an error. The discount comes from the exact revision the reviewer approved. Any known user may call it, as with reading a request.
2. **Follow-up submissions** (tests `cadcdf1`, then code `d191128`). After an approval the RM can submit a new request for the same application; the earlier approval stays effective until the new request is itself approved, and then the newer approval wins. After a decline the RM can submit again; the service links the new request to the declined one through `previousRequestId` and leaves the declined request as it is. The link is set by the service, not sent by the caller, so the submit body did not change. Of the four tests in `cadcdf1`, one failed (the link) and three passed on arrival, because slice 1 already returned an empty list where there is no approval and already picked the approval decided most recently. Submitting after an approval needed no new code: the one-pending rule only counts pending requests.

Product assumptions, in plain words: the lookup does not check that the application exists, so an unknown application id gets an empty list like any application without an approval; "latest" means the approval with the most recent decision time; a new request is linked only when the application's most recently submitted request was declined, so the first request for an application and a request that follows an approval have no link.

## Review notes

The story's code was reviewed once, together with US-04, at commit `7d23ed6`. The review found no critical issues, and confirmed that the lookup reads the discount through the decided revision. Each fix below is its own commit. After the fixes the build is green with 112 tests, 6 of them for this story (commit `299aeab`).

| Finding | Resolution | Commit |
|---------|------------|--------|
| A resubmit that overlapped the decline lost its `previousRequestId` link: the declined predecessor was looked up before the new request was inserted, so a decline that committed in between was missed, and nothing sets the link later | The request is inserted first and the predecessor is looked up afterwards, leaving the new request itself out. An insert that succeeds means the decline is committed, so the lookup sees it | `6598b21` |
| Every request had exactly one revision, so the suite stayed green if the lookup read the current revision instead of the approved one | A test approves revision 1 at 25 bps, adds a revision 2 at 60 bps in the database and checks that the lookup still returns revision 1 and 25 bps. With the lookup switched to the current revision for a moment, this test failed and the other 111 passed. The code was already right, so no production code changed | `1bd22a7` |

The lost link has no automated test. The gap is between two statements of one submit, which a test can only hit by timing, so no flaky test was added. It was checked on a running service instead, by firing a decline and a submit together on `MA-1006`:

| Build | Pairs | Submit got 409 | Submit got 201, linked | Submit got 201, link lost |
|-------|-------|----------------|------------------------|---------------------------|
| `7d23ed6` (before) | 300 | 292 | 0 | 8 |
| `6598b21` (after) | 300 | 295 | 5 | 0 |
| `6598b21` (after) | 1500 | 1448 | 52 | 0 |
| `299aeab` (final) | 600 | 589 | 11 | 0 |

Every decline returned 200. That the lookup after the insert sees the committed decline is argued for PostgreSQL (READ COMMITTED) and was only run on H2.

**Left open, by decision.**

- "Latest" depends only on the decision time, which comes from the wall clock. Two approvals in the same millisecond have no tie-break, and a clock stepped backwards between two approvals would make the lookup return the older one. The id of the `APPROVED` history event would be a monotonic second key.
- The application id in the lookup path has no length limit. A 3000-character id gets `200 []`; it only reaches a bound query parameter and is never echoed.
- The revision number is `revision` here, `currentRevision.number` in a request and `decision.revisionNumber` in a decision; the decider is `approvedBy` here and `decidedBy` there. The names were left as they are.

## Verification evidence

All evidence below is real captured output from 2026-10-11. Parts (a) to (d) were taken at commit `d191128` and are kept as they were captured, so their counts (5 tests for this story, 106 in all, 8 migrations) are those of that commit. Part (e) was taken at commit `299aeab`, after the review fixes.

### (a) The story's end-to-end tests

`./gradlew test --tests '*ApprovedDiscountE2ETest' --rerun --console=plain` with JDK 21. All 5 US-05 tests passed:

```
ApprovedDiscountE2ETest > US-05 AC-4: a resubmit after a decline is a new request linked by previousRequestId PASSED
ApprovedDiscountE2ETest > US-05 AC-1: any internal user reads the same approval PASSED
ApprovedDiscountE2ETest > US-05 AC-2: an application without an approval gets an empty list PASSED
ApprovedDiscountE2ETest > US-05 AC-1: the application process reads the latest approval for an application PASSED
ApprovedDiscountE2ETest > US-05 AC-3: a new request after an approval leaves it effective until the new one is approved PASSED
BUILD SUCCESSFUL in 5s
5 actionable tasks: 1 executed, 4 up-to-date
```

### (b) `make test` in a container

The whole build (complexity gate, architecture test and every end-to-end test) ran inside `eclipse-temurin:21-jdk-noble`. 106 tests passed and none failed: the 101 that existed before this story and its 5. The lines for this story's tests and the end of the run:

```
#13 41.77 ApprovedDiscountE2ETest > US-05 AC-4: a resubmit after a decline is a new request linked by previousRequestId PASSED
#13 41.87 ApprovedDiscountE2ETest > US-05 AC-1: any internal user reads the same approval PASSED
#13 41.87 ApprovedDiscountE2ETest > US-05 AC-2: an application without an approval gets an empty list PASSED
#13 41.97 ApprovedDiscountE2ETest > US-05 AC-1: the application process reads the latest approval for an application PASSED
#13 42.07 ApprovedDiscountE2ETest > US-05 AC-3: a new request after an approval leaves it effective until the new one is approved PASSED
...
#13 47.95 BUILD SUCCESSFUL in 47s
#13 47.95 6 actionable tasks: 6 executed
```

### (c) The lookup and the resubmit link on a running service

`PORT=18083 ./gradlew bootRun` with JDK 21 ("Successfully applied 8 migrations"). Before these calls rm-lana had submitted 75 bps on `MA-1001` and reviewer-lex had approved revision 1 (those calls are in the US-04 trail). The calls were made with `curl -i`; the `Date` header is left out below.

```
# the application process reads the approved discount of MA-1001
# curl -i -H 'X-User-Id: application-process' localhost:18083/applications/MA-1001/approved-discounts
HTTP/1.1 200
Content-Type: application/json
Content-Length: 151

[{"requestId":"7fe8deca-9801-4ae4-bebe-56bb7cb7e138","revision":1,"discountBps":75,"approvedBy":"reviewer-lex","decidedAt":"2026-10-11T19:47:01.718Z"}]

# MA-1002 has no request at all
# curl -i -H 'X-User-Id: application-process' localhost:18083/applications/MA-1002/approved-discounts
HTTP/1.1 200
Content-Type: application/json
Content-Length: 2

[]
```

Then the resubmit link on `MA-1003`. rm-chloe submitted 40 bps (request `62e5f9bb-03dc-4cc9-8d11-3a9a2de27ce2`, `"previousRequestId":null`), reviewer-jonathan declined it with the note "The written offer was not attached", and rm-chloe submitted again:

```
# curl -i -X POST localhost:18083/applications/MA-1003/pricing-exceptions -H 'X-User-Id: rm-chloe' \
#   -H 'Idempotency-Key: story-demo-3' -H 'Content-Type: application/json' \
#   -d '{"discountBps":30,"reason":"Written competitor offer now attached to the file"}'
HTTP/1.1 201
Location: /pricing-exceptions/6e6b9b95-ec40-471c-996b-abb85b96eccf
Content-Type: application/json
Content-Length: 508

{"id":"6e6b9b95-ec40-471c-996b-abb85b96eccf","applicationId":"MA-1003","status":"PENDING","requestedBy":"rm-chloe","submittedAt":"2026-10-11T19:47:01.783Z","currentRevision":{"number":1,"discountBps":30,"reason":"Written competitor offer now attached to the file","advice":{"recommendation":"APPROVE","rationale":"The discount and the length of the reason are within the advisory thresholds."},"createdAt":"2026-10-11T19:47:01.783Z"},"decision":null,"previousRequestId":"62e5f9bb-03dc-4cc9-8d11-3a9a2de27ce2"}

# MA-1003 has a declined request and a pending one, so no approval
# curl -i -H 'X-User-Id: application-process' localhost:18083/applications/MA-1003/approved-discounts
HTTP/1.1 200
Content-Type: application/json
Content-Length: 2

[]
```

The service was stopped after the calls.

### (d) The tests failed first

Each slice's tests were run before its code. The failures were on the behaviour that was missing, not on a compile or setup error. Captured before the code commits, with the `java.lang.AssertionError` line and the stack traces left out:

```
# before b479e03
ApprovedDiscountE2ETest > US-05 AC-1: the application process reads the latest approval for an application FAILED
    Expected status code <200> but was <404>.

# before d191128
ApprovedDiscountE2ETest > US-05 AC-4: a resubmit after a decline is a new request linked by previousRequestId FAILED
    JSON path previousRequestId doesn't match.
    Expected: 8c5a4db0-7b43-4ffc-add5-d0f43e83a719
      Actual: null
ApprovedDiscountE2ETest > US-05 AC-1: any internal user reads the same approval PASSED
ApprovedDiscountE2ETest > US-05 AC-2: an application without an approval gets an empty list PASSED
ApprovedDiscountE2ETest > US-05 AC-1: the application process reads the latest approval for an application PASSED
ApprovedDiscountE2ETest > US-05 AC-3: a new request after an approval leaves it effective until the new one is approved PASSED
```

### (e) After the review fixes (commit `299aeab`)

`./gradlew test --tests '*ApprovedDiscountE2ETest' --rerun --console=plain` with JDK 21. All 6 US-05 tests passed, the 5 above and the one added by the review fixes:

```
ApprovedDiscountE2ETest > US-05 AC-1: the discount comes from the approved revision, not from a later revision PASSED
ApprovedDiscountE2ETest > US-05 AC-4: a resubmit after a decline is a new request linked by previousRequestId PASSED
ApprovedDiscountE2ETest > US-05 AC-1: any internal user reads the same approval PASSED
ApprovedDiscountE2ETest > US-05 AC-2: an application without an approval gets an empty list PASSED
ApprovedDiscountE2ETest > US-05 AC-1: the application process reads the latest approval for an application PASSED
ApprovedDiscountE2ETest > US-05 AC-3: a new request after an approval leaves it effective until the new one is approved PASSED
```

`./gradlew check --rerun-tasks --console=plain` with JDK 21 then ran the whole build from scratch: 112 tests, none failed or was skipped, `BUILD SUCCESSFUL in 12s`. The counts per class are in the US-04 trail, part (f). The test log shows `Successfully applied 9 migrations to schema "public", now at version v9`. `make test` in a container was run again at `e65f456`, after the review fixes: 112 tests passed, none failed, `BUILD SUCCESSFUL in 46s`.
