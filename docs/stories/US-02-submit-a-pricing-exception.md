# US-02 Submit a pricing exception

As a relationship manager, I want to submit a basis-point discount and a reason for an existing mortgage application and read it back, so that a reviewer can decide on it.

## Acceptance criteria

- **AC-1:** An RM submits a discount (bps) and reason for a seeded application and gets 201 with a `Location`. Any internal user who follows it sees status PENDING, revision 1, the requester and the submission time.
- **AC-2:** Invalid submissions get a Problem Details error and create nothing. This covers an unknown application (404); a discount outside 1–200 bps or not below the standard rate (400); a non-integer such as `25.9` (400, rejected and never truncated); and a reason shorter than 10 characters (400).
- **AC-3:** A caller without the RM role gets 403.
- **AC-4:** A second pending request for the same application gets 409. Two racing submits still leave exactly one pending request (database backstop).

## Tests

End-to-end tests: `SubmitRequestE2ETest`. Every test name starts with the criterion it proves.

| Criterion | Proved by |
|-----------|-----------|
| AC-1 | `an RM submits a discount and gets 201 with a Location to the pending request`<br>`any known caller reads the request back`<br>`the submission time comes from the injected clock`<br>`the submission time follows the injected clock when it moves on`<br>`an unknown request id is a 404 Problem Details response`<br>`a malformed request id is a 400 Problem Details response`<br>`a new request records one SUBMITTED history event by the submitting RM` |
| AC-2 | `an unknown application is a 404 Problem Details response`<br>`an invalid submission is rejected with 400`<br>`values on the exact boundaries are accepted`<br>`a rejected submission creates nothing`<br>`an over-long application id is rejected with 400 before the caller's role is checked` |
| AC-3 | `a caller without the RM role cannot submit` |
| AC-4 | `a second pending request for the same application gets 409`<br>`five racing submits leave exactly one pending request`<br>`an insert that fails for any reason but the one-pending rule is a 500, not a 409` |

## Plan

The story was delivered in three thin slices, each one tested before the next started:

1. **Submit and read back** (commit `189949d`). `POST /applications/{applicationId}/pricing-exceptions` and `GET /pricing-exceptions/{id}` go through the controller, `PricingExceptionService` and the Spring Data repositories. The request, its first revision and its `SUBMITTED` history event are written in the same transaction, with times taken from the injected clock.
2. **Rejections as Problem Details** (failing tests `9fa5852`, then code `15b0583`). A caller without the RM role gets 403, an unknown application gets 404, and the discount and reason rules give 400. JSON input is strict: unknown fields, server-owned fields, decimals and numbers sent as strings are all refused.
3. **One pending request per application** (failing tests `2821223`, then code `a7e7eac`). A unique constraint in the database enforces the rule, and the service turns a violation into a clean 409. A race test fires five submits at once.

Product assumptions, in plain words: the discount is a whole number from 1 to 200 bps (configurable) and below the application's standard rate; the reason is 10 to 1000 characters; any known user may read any request; and an unknown application is a 404. The README lists them under Assumptions.

### Decisions

What was decided while planning this story, and why:

- **A submitted revision never changes.** A request holds numbered revisions, and the first one is written at submit. This is what later lets an approval point at exactly what the reviewer saw.
- **One pending request per application, enforced by the database.** Two open requests for one application could end in two conflicting approvals. A unique constraint holds the rule even when two submits race; a check in the service alone would not.
- **The limits are assumptions, and configurable.** The brief sets none, so the service assumes 1 to 200 bps, below the standard rate, with a reason of 10 to 1000 characters.
- **Strict input.** A decimal or a number sent as a string is refused, never rounded, because a silently changed discount is worse than a rejected one.
- **Spring Data JPA.** Chosen because it is what most Java reviewers know. The request row carries a version column so that concurrent changes cannot overwrite each other.

## Review notes

The story's code was reviewed once, together with US-03. The review found no critical issues. Each fix below is its own commit, and where behaviour changed the failing test was committed first. After the fixes the build is green with 84 tests, 46 of them for this story (commit `116baf5`).

| Finding | Resolution | Commit |
|---------|------------|--------|
| An application id too long for the idempotency table made the key claim fail, and that failure was reported as 409 "still in progress" | The application id is limited to 20 characters and a longer one gets 400. The claim itself is fixed under US-03 | `02f9f75` (tests), `b18378d` (code) |
| The one-pending insert turned every database failure into 409 "already has a pending request" | Only a unique-constraint violation becomes the 409. Any other failure is a 500 | `6f53cf8` (test), `a1ec086` (code) |
| No test checked the `SUBMITTED` history event, so removing it would have left the suite green | A test reads the event row and checks the request, revision, actor and time. The "creates nothing" test now checks all four tables | `e14299e` |

**Left open, by decision.** Two input edge cases were recorded and not fixed, because the API is only reached through the service's own UI: a reason padded with spaces passes the 10-character rule, and a JSON body with a duplicated field is accepted with the last value winning.

## Verification evidence

All evidence below is real captured output from 2026-10-11.

### (a) End-to-end tests, `./gradlew check --rerun-tasks` (commit `1f05524`)

All 41 US-02 tests passed:

```
SubmitRequestE2ETest > US-02 AC-1: an RM submits a discount and gets 201 with a Location to the pending request PASSED
SubmitRequestE2ETest > US-02 AC-1: the submission time comes from the injected clock PASSED
SubmitRequestE2ETest > US-02 AC-4: five racing submits leave exactly one pending request PASSED
SubmitRequestE2ETest > US-02 AC-1: an unknown request id is a 404 Problem Details response PASSED
SubmitRequestE2ETest > US-02 AC-2: values on the exact boundaries are accepted > US-02 AC-2: "1 bps with a 10-character reason" is accepted PASSED
SubmitRequestE2ETest > US-02 AC-2: values on the exact boundaries are accepted > US-02 AC-2: "200 bps with a 1000-character reason" is accepted PASSED
SubmitRequestE2ETest > US-02 AC-2: values on the exact boundaries are accepted > US-02 AC-2: "149 bps on MA-1006" is accepted PASSED
SubmitRequestE2ETest > US-02 AC-2: values on the exact boundaries are accepted > US-02 AC-2: "a reason of ten euro signs" is accepted PASSED
SubmitRequestE2ETest > US-02 AC-3: a caller without the RM role cannot submit > US-02 AC-3: "reviewer-lex" gets 403 PASSED
SubmitRequestE2ETest > US-02 AC-3: a caller without the RM role cannot submit > US-02 AC-3: "reviewer-jonathan" gets 403 PASSED
SubmitRequestE2ETest > US-02 AC-3: a caller without the RM role cannot submit > US-02 AC-3: "application-process" gets 403 PASSED
SubmitRequestE2ETest > US-02 AC-2: an unknown application is a 404 Problem Details response PASSED
SubmitRequestE2ETest > US-02 AC-1: any known caller reads the request back > US-02 AC-1: "rm-lana" reads the request back PASSED
SubmitRequestE2ETest > US-02 AC-1: any known caller reads the request back > US-02 AC-1: "rm-chloe" reads the request back PASSED
SubmitRequestE2ETest > US-02 AC-1: any known caller reads the request back > US-02 AC-1: "reviewer-lex" reads the request back PASSED
SubmitRequestE2ETest > US-02 AC-1: any known caller reads the request back > US-02 AC-1: "dual-clark" reads the request back PASSED
SubmitRequestE2ETest > US-02 AC-1: any known caller reads the request back > US-02 AC-1: "application-process" reads the request back PASSED
SubmitRequestE2ETest > US-02 AC-1: a malformed request id is a 400 Problem Details response PASSED
SubmitRequestE2ETest > US-02 AC-4: a second pending request for the same application gets 409 PASSED
SubmitRequestE2ETest > US-02 AC-2: a rejected submission creates nothing PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "discountBps 25.9" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "discountBps 25.0" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "discountBps \"25\" as a string" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "discountBps 99999999999" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "discountBps 0" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "discountBps -5" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "discountBps 201" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "discountBps 150 on MA-1006 (its standard rate)" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "discountBps 151 on MA-1006" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "a missing discountBps" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "a null discountBps" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "a missing reason" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "a reason of ten spaces" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "a 9-character reason" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "a 1001-character reason" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "an unknown extra field" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "a client-supplied requestedBy" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "a client-supplied status" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "an empty body" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-2: an invalid submission is rejected with 400 > US-02 AC-2: "malformed JSON" is rejected with 400 PASSED
SubmitRequestE2ETest > US-02 AC-1: the submission time follows the injected clock when it moves on PASSED
```

The race test (AC-4) also passed on three back-to-back reruns when it was first written.

### (b) `make test` in a container (commit `1f05524`)

The whole suite (5 architecture rules, 18 US-01, 41 US-02 and 12 US-03 tests) passed inside `eclipse-temurin:21-jdk-noble`, after `pmdMain`:

```
#13 33.43 > Task :pmdMain
#13 44.03 SubmitRequestE2ETest > US-02 AC-1: an RM submits a discount and gets 201 with a Location to the pending request PASSED
#13 44.13 SubmitRequestE2ETest > US-02 AC-1: the submission time comes from the injected clock PASSED
#13 44.13 SubmitRequestE2ETest > US-02 AC-4: five racing submits leave exactly one pending request PASSED
...
#13 45.53 BUILD SUCCESSFUL in 45s
```

### (c) Submit and read back on a running service (commit `1f05524`)

`PORT=18083 ./gradlew bootRun` with JDK 21:

```
o.f.core.internal.command.DbMigrate      : Successfully applied 5 migrations to schema "public", now at version v5 (execution time 00:00.005s)
c.b.p.PricingExceptionServiceApplication : Started PricingExceptionServiceApplication in 1.903 seconds (process running for 2.024)

$ curl -i -X POST localhost:18083/applications/MA-1001/pricing-exceptions -H 'X-User-Id: rm-lana' \
    -H 'Idempotency-Key: readme-demo-1' -H 'Content-Type: application/json' \
    -d '{"discountBps":25,"reason":"Retention offer matching a competitor quote"}'
HTTP/1.1 201
Location: /pricing-exceptions/49222df3-2378-49eb-9019-be689e685e21
Content-Type: application/json
Content-Length: 297

{"id":"49222df3-2378-49eb-9019-be689e685e21","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-lana","submittedAt":"2026-10-11T15:32:18.613Z","currentRevision":{"number":1,"discountBps":25,"reason":"Retention offer matching a competitor quote","createdAt":"2026-10-11T15:32:18.613Z"}}

$ curl -H 'X-User-Id: reviewer-lex' localhost:18083/pricing-exceptions/49222df3-2378-49eb-9019-be689e685e21
{"id":"49222df3-2378-49eb-9019-be689e685e21","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-lana","submittedAt":"2026-10-11T15:32:18.613Z","currentRevision":{"number":1,"discountBps":25,"reason":"Retention offer matching a competitor quote","createdAt":"2026-10-11T15:32:18.613Z"}}
```

The service was stopped after the calls.
