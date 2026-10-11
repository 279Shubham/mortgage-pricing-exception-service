# Mortgage Pricing Exception Service

## Problem

Relationship managers (RMs) sometimes need to offer a customer a better rate than the standard one. This service lets an RM request a discount, in basis points (bps), on the standard rate of an existing mortgage application. An authorised reviewer approves or declines the request, the mortgage application process reads the approved discount, and internal users can see the full history of every request.

## Quick start

```bash
make run
curl -H 'X-User-Id: rm-anna' localhost:8080/me
```

You need Docker and nothing else. No local JDK is required.

## Run

**With Docker (recommended).** No local JDK is needed.

```bash
make run              # builds the image and starts the service on http://localhost:8080
make run PORT=9090    # same, on another host port if 8080 is taken
make stop             # stops the running container (Ctrl+C in the make run terminal works too)
```

`make run` runs in the foreground and builds an image for your machine's own architecture (arm64 or amd64).

**Directly with Gradle (fallback).** This needs JDK 21.

```bash
sdk env                      # with SDKMAN: picks the JDK pinned in .sdkmanrc
./gradlew bootRun            # starts the service on http://localhost:8080
PORT=9090 ./gradlew bootRun  # on another port
```

Without SDKMAN, install any JDK 21 (for example `sdk install java 21.0.10-tem`). If Gradle cannot find a JDK 21, it stops with a toolchain error. In that case use `make run`.

## Test

```bash
make test         # runs every check inside a container, no local JDK needed
./gradlew check   # the same checks locally, needs JDK 21
```

Both commands run three things, and any failure fails the build:

1. **Complexity gate (PMD):** a method with cyclomatic or cognitive complexity above 8 fails the build.
2. **Architecture test (ArchUnit):** checks the layering (controllers call services, and only services use the Spring Data repositories) and bans reading the system time directly: any `java.time` `now()` without a clock argument (method references such as `Instant::now` included); `System.currentTimeMillis()` and `System.nanoTime()`; system `Clock` or `InstantSource` factories outside the clock config; the legacy `java.util.Date` and `java.util.Calendar` types; and Hibernate's `@CreationTimestamp`, `@UpdateTimestamp` and `@CurrentTimestamp`. Time always comes from the injected clock.
3. **End-to-end suite:** starts the full service on a random port and calls it over HTTP.

The tests are end-to-end only, and their names follow the story acceptance criteria (for example `US-01 AC-3: a seeded X-User-Id resolves to that user and roles on /me`), so the test report reads as a checklist of user stories. Each story in [`docs/stories/`](docs/stories/) maps its criteria to the tests that prove them.

## Explore the API

**Identity.** Every call says who the caller is through the `X-User-Id` header, using one of the seeded user ids (see [Seed data](#seed-data)). `/actuator/health` and `/users` are open. Every other endpoint needs a known id: one interceptor checks the caller before any controller runs, so a new endpoint is protected by default. Actuator endpoints are served outside that caller check, so only `/actuator/health` is exposed and the actuator discovery page (`/actuator`) is switched off. A missing or unknown id gets `401` with an `application/problem+json` body:

```json
{"detail":"Missing or unknown X-User-Id","instance":"/me","status":401,"title":"Unauthorized"}
```

This header is a demo-only trust model, not authentication. See [Production readiness gaps](#production-readiness-gaps).

**Health:**

```bash
curl localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

**Seeded users** (open, feeds the demo user switcher):

```bash
curl localhost:8080/users
# [{"id":"application-process","displayName":"Mortgage application process (service user)","roles":["APPLICATION_PROCESS"]},
#  {"id":"dual-erin","displayName":"Erin (RM + Reviewer, four-eyes demo)","roles":["RM","REVIEWER"]}, ...]
```

**Who am I:**

```bash
curl -H 'X-User-Id: rm-anna' localhost:8080/me
# {"id":"rm-anna","displayName":"Anna (Relationship Manager)","roles":["RM"]}
```

**Mortgage applications:**

```bash
curl -H 'X-User-Id: reviewer-ben' localhost:8080/applications
# [{"id":"MA-1001","borrowerLabel":"Fictional household A","standardRateBps":425}, ...]
```

**No identity, so 401:**

```bash
curl -i localhost:8080/me
# HTTP/1.1 401
# WWW-Authenticate: X-User-Id realm="pricing-exception-service"
# Content-Type: application/problem+json
#
# {"detail":"Missing or unknown X-User-Id","instance":"/me","status":401,"title":"Unauthorized"}
```

**Submit a pricing exception.** An RM asks for a discount on an application. Every POST needs an `Idempotency-Key` header (see [Assumptions](#assumptions)):

```bash
curl -i -X POST localhost:8080/applications/MA-1001/pricing-exceptions \
  -H 'X-User-Id: rm-anna' \
  -H 'Idempotency-Key: readme-demo-2' \
  -H 'Content-Type: application/json' \
  -d '{"discountBps":75,"reason":"Retention offer matching a competitor quote"}'
# HTTP/1.1 201
# Location: /pricing-exceptions/7fe8deca-9801-4ae4-bebe-56bb7cb7e138
# Content-Type: application/json
#
# {"id":"7fe8deca-9801-4ae4-bebe-56bb7cb7e138","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-anna",
#  "submittedAt":"2026-10-11T19:47:01.594Z","currentRevision":{"number":1,"discountBps":75,
#  "reason":"Retention offer matching a competitor quote","advice":{"recommendation":"REVIEW_CAREFULLY",
#  "rationale":"Review carefully: the discount of 75 bps is above the advisory threshold of 50 bps."},
#  "createdAt":"2026-10-11T19:47:01.594Z"},"decision":null,"previousRequestId":null}
```

The id and the times differ on every run. Use the `Location` you get back in the next calls. `advice` is a non-binding recommendation stored with the revision; 75 bps is above the advisory threshold, so it says to review carefully. `decision` stays `null` until a reviewer decides. `previousRequestId` is only set on a resubmit after a decline.

**Read it back.** Any known user can read a request, here a reviewer:

```bash
curl -H 'X-User-Id: reviewer-ben' localhost:8080/pricing-exceptions/7fe8deca-9801-4ae4-bebe-56bb7cb7e138
# {"id":"7fe8deca-9801-4ae4-bebe-56bb7cb7e138","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-anna", ...}
```

**Retry safely.** Sending the exact same submit again, with the same key, does not create a second request. It returns the stored answer: the same `201`, the same `Location` and the same body, byte for byte.

Reusing the key for a different body (here `"discountBps":30`) is refused with `422`:

```bash
curl -i -X POST localhost:8080/applications/MA-1001/pricing-exceptions \
  -H 'X-User-Id: rm-anna' \
  -H 'Idempotency-Key: readme-demo-2' \
  -H 'Content-Type: application/json' \
  -d '{"discountBps":30,"reason":"Retention offer matching a competitor quote"}'
# HTTP/1.1 422
# Content-Type: application/problem+json
#
# {"detail":"This Idempotency-Key was already used with a different request","instance":"/applications/MA-1001/pricing-exceptions","status":422,"title":"Unprocessable Content"}
```

Leaving the `Idempotency-Key` header out gets `400`:

```bash
curl -i -X POST localhost:8080/applications/MA-1001/pricing-exceptions \
  -H 'X-User-Id: rm-anna' \
  -H 'Content-Type: application/json' \
  -d '{"discountBps":75,"reason":"Retention offer matching a competitor quote"}'
# HTTP/1.1 400
# Content-Type: application/problem+json
#
# {"detail":"Required header 'Idempotency-Key' is not present.","instance":"/applications/MA-1001/pricing-exceptions","status":400,"title":"Bad Request"}
```

**The pending queue.** A reviewer lists the requests in one status, oldest first. Each one shows its current revision number, the discount and the advice:

```bash
curl -H 'X-User-Id: reviewer-ben' 'localhost:8080/pricing-exceptions?status=PENDING'
# [{"id":"7fe8deca-9801-4ae4-bebe-56bb7cb7e138","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-anna",
#   "submittedAt":"2026-10-11T19:47:01.594Z","currentRevision":{"number":1,"discountBps":75,
#   "reason":"Retention offer matching a competitor quote","advice":{"recommendation":"REVIEW_CAREFULLY",
#   "rationale":"Review carefully: the discount of 75 bps is above the advisory threshold of 50 bps."},
#   "createdAt":"2026-10-11T19:47:01.594Z"},"decision":null,"previousRequestId":null}]
```

**Approve.** The reviewer states the revision number they looked at. The advice does not bind them:

```bash
curl -i -X POST localhost:8080/pricing-exceptions/7fe8deca-9801-4ae4-bebe-56bb7cb7e138/approve \
  -H 'X-User-Id: reviewer-ben' \
  -H 'Idempotency-Key: readme-demo-3' \
  -H 'Content-Type: application/json' \
  -d '{"revisionNumber":1}'
# HTTP/1.1 200
# Content-Type: application/json
#
# {"id":"7fe8deca-9801-4ae4-bebe-56bb7cb7e138","applicationId":"MA-1001","status":"APPROVED","requestedBy":"rm-anna", ...,
#  "decision":{"decidedBy":"reviewer-ben","decidedAt":"2026-10-11T19:47:01.718Z","revisionNumber":1,"note":null},
#  "previousRequestId":null}
```

Declining is the same call on `/decline`, with a required note: `-d '{"revisionNumber":1,"note":"The written offer was not attached"}'`. `rm-anna` trying either call gets `403`, and so does a reviewer deciding their own request.

**Read the approved discount.** The mortgage application process asks for the approval in effect for an application. The answer is always a list: one item, or empty when there is no approval:

```bash
curl -H 'X-User-Id: application-process' localhost:8080/applications/MA-1001/approved-discounts
# [{"requestId":"7fe8deca-9801-4ae4-bebe-56bb7cb7e138","revision":1,"discountBps":75,"approvedBy":"reviewer-ben","decidedAt":"2026-10-11T19:47:01.718Z"}]

curl -H 'X-User-Id: application-process' localhost:8080/applications/MA-1002/approved-discounts
# []
```

## Seed data

All seed data is fictional. The service loads it on every start, and it resets on restart.

**Users**

| Id | Roles | Purpose |
|----|-------|---------|
| `rm-anna` | RM | Relationship manager |
| `rm-carl` | RM | Second relationship manager |
| `reviewer-ben` | REVIEWER | Reviewer |
| `reviewer-dana` | REVIEWER | Second reviewer |
| `dual-erin` | RM, REVIEWER | Four-eyes demo user: holds both roles, so it can show that nobody decides their own request |
| `application-process` | APPLICATION_PROCESS | Service user for the mortgage application process |

**Mortgage applications**

| Id | Borrower label | Standard rate (bps) |
|----|----------------|---------------------|
| `MA-1001` | Fictional household A | 425 |
| `MA-1002` | Fictional household B | 380 |
| `MA-1003` | Fictional household C | 495 |
| `MA-1004` | Fictional household D | 520 |
| `MA-1005` | Fictional household E | 355 |
| `MA-1006` | Fictional household F (low rate) | 150 |

## Assumptions

The brief sets no limits for a pricing exception, so the service assumes these. Each limit is either configurable or a one-line change.

- **Discount.** A whole number of basis points from 1 to 200, configurable with `pricing.discount.min-bps` and `pricing.discount.max-bps`. It must also be strictly below the application's standard rate (so at most 149 bps on `MA-1006`, whose standard rate is 150 bps). Non-integers such as `25.9`, or a number sent as a string such as `"25"`, are rejected with `400` and never rounded.
- **Reason.** 10 to 1000 characters, counted as characters, not bytes. Control characters are rejected with `400`, except line breaks and tabs.
- **One pending request per application.** A second one gets `409`. The database enforces this, so two submits that race still leave exactly one pending request.
- **Reading.** Any known internal user may read any request.
- **Deciding.** Only a user with the reviewer role may approve or decline, and never their own request (so `dual-erin` cannot decide what she submitted). The call states the revision number the reviewer looked at, and it must be the request's current revision; otherwise `409`. A decline needs a note of at most 1000 characters, with the same rule on control characters as a reason. An approval carries no note.
- **Advice.** Every revision carries a recommendation and a rationale from a rule-based advisor. The advice is non-binding: nothing reads it when a decision is made. It flags a discount above `pricing.advice.high-discount-bps` (default 50) and a reason shorter than `pricing.advice.min-reason-length` (default 30). A short reason is what counts as a weak one.
- **Approved-discount lookup.** Returns the latest approval of an application as a list of one, and an empty list when there is none, always with `200`. An unknown application id also gets an empty list. After an approval a new request may be submitted; the earlier approval stays in effect until the new one is approved.
- **Resubmit after a decline.** A declined request stays declined. The next submit for that application is a new request, and the service sets its `previousRequestId` to the declined request when the application's most recent request was declined. The caller does not send the link.
- **Unknown application.** A submit for an application id that does not exist gets `404`. An id longer than 20 characters gets `400`.
- **`Idempotency-Key`.** Required on every POST. Any non-blank value of at most 255 characters is accepted and used exactly as sent (no trimming, no change of case). A key belongs to one caller and one route, so another user, or the same user on another application, can use the same key without clashing. A key is kept for `pricing.idempotency.ttl` (default `24h`). The same key with the same body replays the stored response; a different body gets `422`; a retry while the first call is still running gets `409`. A replay returns the original status, `Location`, content type and body; other response headers are not stored. This follows the convention of the expired IETF Idempotency-Key draft and common industry practice. It is not a ratified standard.

## Stakeholder conflict: changing a submitted discount

## Scope choices

## Architecture and decisions

## How AI agents were used and verified

AI agents wrote the plans, the tests and the code. Each feature went through the same loop, and each step left something a person can check:

1. **Plan.** A user story with numbered acceptance criteria, and a plan that splits it into thin slices.
2. **Execute.** For each slice, the failing end-to-end tests are committed first and the code that makes them pass second. The commit history shows the pairs, for example `9fa5852` (tests) then `15b0583` (code).
3. **Review.** A separate review pass reads the story's code. Each finding is fixed in its own commit or recorded as left open.
4. **Verify.** The full build (complexity gate, architecture test, end-to-end suite) runs, and its real output is pasted into the story. Where a gate was added, a temporary probe shows it failing first.

Each story file in [`docs/stories/`](docs/stories/) is the record of one pass through that loop: criteria, a criterion-to-test table, the plan, review notes and verification evidence.

| Story | End-to-end tests |
|-------|------------------|
| [US-01 Run and explore the service](docs/stories/US-01-run-and-explore.md) | `RunAndExploreE2ETest`, `ArchitectureTest` |
| [US-02 Submit a pricing exception](docs/stories/US-02-submit-a-pricing-exception.md) | `SubmitRequestE2ETest` |
| [US-03 Retry safely](docs/stories/US-03-retry-safely.md) | `RetrySafelyE2ETest` |
| [US-04 Decide a request with advice](docs/stories/US-04-decide-a-request-with-advice.md) | `DecideRequestE2ETest`, `AdviceThresholdsE2ETest` |
| [US-05 Read the approved discount](docs/stories/US-05-read-the-approved-discount.md) | `ApprovedDiscountE2ETest` |

From US-04 onwards the story file is committed with its criteria before the tests and code, and the evidence is added when the story closes. The first three story files were written up after their code.

## Production readiness gaps

- **Identity is demo-only.** The `X-User-Id` header is a demo-only trust model: any caller can claim any seeded id. In production the `X-User-Id` lookup in `CallerInterceptor` is replaced by an OAuth2/OIDC resource server that takes the caller from a verified token.
- **Storage is in-memory.** H2 runs in memory and resets on every restart. The target is Postgres. The Flyway migrations already run in H2's PostgreSQL mode, so the swap is a datasource change plus a migration check.
- **`GET /users` is open** only to feed the demo user switcher. In production it would be removed or protected.
- **Single instance.** The service runs as one instance, and health is the only actuator endpoint exposed.
- **No cleanup job for idempotency records.** An expired record is removed only when the same key is reused, so production needs a scheduled cleanup. Until a record is removed, its stored response keeps a copy of the request's reason text.
- **Postgres needs two changes.** Set `lock_timeout`, so that a retry of a call still in flight gets `409` instead of waiting. Replace the H2 generated column behind the one-pending rule with a partial unique index (H2 has no partial indexes).
- **Concurrent decisions are not handled cleanly.** When two reviewers decide the same request at the same moment, or a decision, a revision and a withdrawal reach it together, only one change is stored, but the other caller gets a `500` with Spring Boot's default error body instead of a `409`. Nothing is half-written, and a retry with the same key is answered from the stored state.
- **Errors are Spring's built-in Problem Details only.** There is no catalogue of error types. This is by design.

## Time spent
