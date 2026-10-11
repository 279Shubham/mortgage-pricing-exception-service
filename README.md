# Mortgage Pricing Exception Service

## Problem

Relationship managers (RMs) sometimes need to offer a customer a better rate than the standard one. This service lets an RM request a discount, in basis points (bps), on the standard rate of an existing mortgage application. An authorised reviewer approves or declines the request, the mortgage application process reads the approved discount, and internal users can see the full history of every request.

## Quick start

```bash
make run     # then open http://localhost:8080/
make test
```

You need Docker and nothing else. No local JDK is required. The page at `http://localhost:8080/` walks the whole flow; the same calls with `curl` are under [Try it](#try-it).

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

## Try it

**In a browser.** Open `http://localhost:8080/`. Pick a seeded user at the top right, submit a request, then switch user to approve, decline, revise or withdraw it, and open its history. The page offers every action to every user and shows the service's answer, so a refusal such as `403` is visible too. A good first path: submit as Lana, switch to Lex Luthor, approve, press History.

**With curl.** Every call names the caller in the `X-User-Id` header, and every POST needs an `Idempotency-Key`. The ids and times in the output differ on every run.

```bash
# 1. Lana, a relationship manager, asks for 75 bps on application MA-1001
curl -i -X POST localhost:8080/applications/MA-1001/pricing-exceptions \
  -H 'X-User-Id: rm-lana' -H 'Idempotency-Key: demo-1' -H 'Content-Type: application/json' \
  -d '{"discountBps":75,"reason":"Retention offer matching a competitor quote"}'
# HTTP/1.1 201
# Location: /pricing-exceptions/7fe8deca-9801-4ae4-bebe-56bb7cb7e138
#
# {"id":"7fe8deca-...","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-lana",
#  "currentRevision":{"number":1,"discountBps":75,"reason":"Retention offer matching a competitor quote",
#   "advice":{"recommendation":"REVIEW_CAREFULLY",
#    "rationale":"Review carefully: the discount of 75 bps is above the advisory threshold of 50 bps."}, ...},
#  "decision":null,"previousRequestId":null}

# 2. Lex, a reviewer, looks at the pending queue and approves revision 1
curl -H 'X-User-Id: reviewer-lex' 'localhost:8080/pricing-exceptions?status=PENDING'
curl -X POST localhost:8080/pricing-exceptions/<id>/approve \
  -H 'X-User-Id: reviewer-lex' -H 'Idempotency-Key: demo-2' -H 'Content-Type: application/json' \
  -d '{"revisionNumber":1}'
# {"id":"7fe8deca-...","status":"APPROVED", ...,
#  "decision":{"decidedBy":"reviewer-lex","decidedAt":"2026-10-11T19:47:01.718Z","revisionNumber":1,"note":null}}

# 3. The mortgage application process reads the approved discount
curl -H 'X-User-Id: application-process' localhost:8080/applications/MA-1001/approved-discounts
# [{"requestId":"7fe8deca-...","revision":1,"discountBps":75,"approvedBy":"reviewer-lex","decidedAt":"2026-10-11T19:47:01.718Z"}]

# 4. Anyone internal reads the full history
curl -H 'X-User-Id: rm-chloe' localhost:8080/pricing-exceptions/<id>/history
# {"requestId":"...","applicationId":"MA-1001","status":"APPROVED","previousRequestId":null,"events":[
#  {"type":"SUBMITTED","revisionNumber":1,"actorId":"rm-lana","occurredAt":"...","discountBps":75, ...,
#   "advice":{"recommendation":"REVIEW_CAREFULLY", ...},"note":null},
#  {"type":"APPROVED","revisionNumber":1,"actorId":"reviewer-lex", ...}]}
```

Things worth trying after that:

- **Retry.** Send step 1 again with the same key: the same `201`, `Location` and body come back, and no second request is created. Change the body and keep the key: `422`.
- **Four eyes.** Approve as `rm-lana`: `403`. Submit as `dual-clark`, who holds both roles, and approve as `dual-clark`: `403`.
- **Revise.** `POST /pricing-exceptions/<id>/revise` as the requester with a new `discountBps` and `reason`. The request moves to revision 2 with its own advice. Approving `{"revisionNumber":1}` now gets `409`.
- **Decline and try again.** `POST .../decline` needs a note: `{"revisionNumber":1,"note":"The written offer was not attached"}`. The next submit for that application is a new request whose `previousRequestId` points at the declined one.
- **Withdraw.** `POST .../withdraw` as the requester, with no body. The application then accepts a new request.

## API

| Call | Who | What it does |
|------|-----|--------------|
| `POST /applications/{applicationId}/pricing-exceptions` | relationship manager | Submit a request. `201` with `Location` |
| `GET /pricing-exceptions/{id}` | any known user | Read a request with its current revision, advice and decision |
| `GET /pricing-exceptions?status=PENDING` | any known user | List the requests in one status, oldest first |
| `POST /pricing-exceptions/{id}/approve` | reviewer, not the requester | Approve the stated revision |
| `POST /pricing-exceptions/{id}/decline` | reviewer, not the requester | Decline the stated revision, with a note |
| `POST /pricing-exceptions/{id}/revise` | the requester | Replace the discount and reason with a new revision |
| `POST /pricing-exceptions/{id}/withdraw` | the requester | Withdraw a pending request |
| `GET /pricing-exceptions/{id}/revisions` | any known user | Every revision, with the superseded ones marked |
| `GET /pricing-exceptions/{id}/history` | any known user | Every event in order, with the advice next to each decision |
| `GET /applications/{applicationId}/approved-discounts` | any known user | The approval in effect: a list of one, or empty |
| `GET /applications`, `GET /me` | any known user | The seeded applications; the caller and their roles |
| `GET /users`, `GET /actuator/health` | open | The seeded users (feeds the page's user switcher); health |

Errors are `application/problem+json`: `400` invalid input or a missing key, `401` a missing or unknown `X-User-Id`, `403` wrong role or not your request, `404` unknown request or application, `409` a rule conflict (already decided, superseded revision, second pending request), `422` a key reused with another body.

One interceptor checks the caller before any controller runs, so a new endpoint is protected by default. The header is a demo-only trust model, not authentication; see [Production readiness gaps](#production-readiness-gaps). There is no OpenAPI or Swagger page.

## Seed data

All seed data is fictional. The service loads it on every start, and it resets on restart.

**Users**

| Id | Roles | Purpose |
|----|-------|---------|
| `rm-lana` | RM | Relationship manager |
| `rm-chloe` | RM | Second relationship manager |
| `reviewer-lex` | REVIEWER | Reviewer |
| `reviewer-jonathan` | REVIEWER | Second reviewer |
| `dual-clark` | RM, REVIEWER | Four-eyes demo user: holds both roles, so it can show that nobody decides their own request |
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

## Stakeholder conflict: changing a submitted discount

The brief holds two wishes that cannot both be met literally. The relationship manager wants to change a discount after submitting it. The reviewer's approval must apply to exactly the request that was reviewed.

The service resolves it with **revisions**. A submitted revision never changes. To change the discount, the requester revises: the request gets a new numbered revision with its own advice, and the earlier one stays readable and is marked superseded. A decision states the revision number the reviewer looked at, and the service refuses it with `409` if that is no longer the current one. So the manager can still change course, and an approval can never land on something the reviewer did not see.

## Scope choices

Built, because the core value depends on it:

- The full lifecycle: submit, revise, withdraw, approve, decline, resubmit after a decline.
- Four-eyes rules, one pending request per application (held by the database), and retry safety on every POST.
- A rule-based advisor whose advice is stored with each revision and never binds the reviewer.
- The approved-discount lookup for the application process, the full history, and a small page to try it all.

Left out, on purpose:

- **Real authentication.** A header checked against seeded users stands in for it.
- **An LLM advisor.** No paid service is allowed. `ReviewAdvisor` is an interface, so one can replace the rules later.
- **Approval tiers, authority limits and approvals that expire.** Natural next steps, not needed to show the core.
- **A message broker and an outbox.** Events are logged after commit, which shows where publishing belongs.
- **A clean answer for the loser of a race.** Only one of two simultaneous changes is stored, but the loser gets a `500`. Accepted to keep the service small.
- **Unit tests.** The end-to-end suite is the evidence of behaviour.
- **An OpenAPI page and a polished UI.**

## Architecture and decisions

Plain layered Spring MVC: **controller → service → repository**, on Java 21, Spring Boot 4.1, Spring Data JPA, H2 and Flyway.

```
controller/   HTTP endpoints, no logic
service/      the rules: PricingExceptionService, DecisionService, RequestHistoryService,
              IdempotencyService, RuleBasedReviewAdvisor, RequestEventPublisher
repository/   Spring Data interfaces
model/ dto/   entities and response shapes
web/          caller check and error mapping
```

| Decision | Why |
|----------|-----|
| Layers, not ports and adapters | Simpler to read. An earlier ports-and-adapters layout was removed for that reason |
| Interfaces only for the repositories and `ReviewAdvisor` | Those are the two realistic swaps: H2 to Postgres, and rules to an LLM. Everything else is a plain class |
| A request holds immutable, numbered revisions | An approval can point at exactly what was reviewed |
| A version column on the request row | Two simultaneous changes cannot both be stored |
| The one-pending rule is a database constraint | It holds even when two submits race |
| The idempotency key is claimed in the same transaction as the change | No key without its request, and no request without its key |
| Advice is stored with the revision | The history shows what the reviewer was told, even if the rules change later |
| Time comes from one injected clock | Tests control time, for example the 24-hour key expiry, without sleeping |
| End-to-end tests only, named after acceptance criteria | The test report reads as the list of user stories |
| Two build gates: method complexity and an architecture test | "Simple code" and the layering are checked, not just promised |
| Flyway owns the schema; H2 runs in PostgreSQL mode | Reproducible from an empty database, and close to the production target |
| Spring Boot 4.1 | Open-source support for the 3.5 line ended on 2026-06-30 |

Each story file lists the decisions taken for that story.

## Assumptions

The brief sets no limits for a pricing exception, so the service assumes these. Each limit is either configurable or a one-line change.

- **Discount.** A whole number of basis points from 1 to 200, configurable with `pricing.discount.min-bps` and `pricing.discount.max-bps`. It must also be strictly below the application's standard rate (so at most 149 bps on `MA-1006`, whose standard rate is 150 bps). Non-integers such as `25.9`, or a number sent as a string such as `"25"`, are rejected with `400` and never rounded.
- **Reason.** 10 to 1000 characters, counted as characters, not bytes. Control characters are rejected with `400`, except line breaks and tabs.
- **One pending request per application.** A second one gets `409`. The database enforces this, so two submits that race still leave exactly one pending request.
- **Reading.** Any known internal user may read any request.
- **Deciding.** Only a user with the reviewer role may approve or decline, and never their own request (so `dual-clark` cannot decide what she submitted). The call states the revision number the reviewer looked at, and it must be the request's current revision; otherwise `409`. A decline needs a note of at most 1000 characters, with the same rule on control characters as a reason. An approval carries no note.
- **Advice.** Every revision carries a recommendation and a rationale from a rule-based advisor. The advice is non-binding: nothing reads it when a decision is made. It flags a discount above `pricing.advice.high-discount-bps` (default 50) and a reason shorter than `pricing.advice.min-reason-length` (default 30). A short reason is what counts as a weak one.
- **Approved-discount lookup.** Returns the latest approval of an application as a list of one, and an empty list when there is none, always with `200`. An unknown application id also gets an empty list. After an approval a new request may be submitted; the earlier approval stays in effect until the new one is approved.
- **Resubmit after a decline.** A declined request stays declined. The next submit for that application is a new request, and the service sets its `previousRequestId` to the declined request when the application's most recent request was declined. The caller does not send the link.
- **Unknown application.** A submit for an application id that does not exist gets `404`. An id longer than 20 characters gets `400`.
- **`Idempotency-Key`.** Required on every POST. Any non-blank value of at most 255 characters is accepted and used exactly as sent (no trimming, no change of case). A key belongs to one caller and one route, so another user, or the same user on another application, can use the same key without clashing. A key is kept for `pricing.idempotency.ttl` (default `24h`). The same key with the same body replays the stored response; a different body gets `422`; a retry while the first call is still running gets `409`. A replay returns the original status, `Location`, content type and body; other response headers are not stored. This follows the convention of the expired IETF Idempotency-Key draft and common industry practice. It is not a ratified standard.

## How AI agents were used and verified

AI agents wrote the plans, the tests and the code; a person set the scope, made the product decisions and steered. Each feature went through the same loop, and each step left something a person can check:

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
| [US-06 Revise or withdraw a pending request](docs/stories/US-06-revise-or-withdraw-a-pending-request.md) | `ReviseWithdrawE2ETest` |
| [US-07 See a request's full history](docs/stories/US-07-see-a-request-full-history.md) | `RequestHistoryE2ETest` |
| [US-08 Try the service in a browser](docs/stories/US-08-try-the-service-in-a-browser.md) | `BrowserPageE2ETest` |

Where the trail is weaker than the loop above:

- **Order.** From US-04 onwards the story file is committed with its criteria before the tests and code. The first three story files were written up after their code.
- **Review.** US-01 to US-05 were reviewed and their findings are in the stories. US-06, US-07 and US-08 were not reviewed; their stories say so.
- **How much process.** The first stories ran a heavy workflow with separate planning, review and verification agents. It produced far more planning text than code, so the last stories were planned and written directly in one session, with the same story-first, test-first order.
- **Manual checks.** Beyond the suite, the finished service was exercised with a script of 126 HTTP checks, three races of twelve parallel calls, and a walk through the page in a browser. The results are in US-06 and US-08.

## Production readiness gaps

- **Identity is demo-only.** The `X-User-Id` header is a demo-only trust model: any caller can claim any seeded id. In production the `X-User-Id` lookup in `CallerInterceptor` is replaced by an OAuth2/OIDC resource server that takes the caller from a verified token.
- **Storage is in-memory.** H2 runs in memory and resets on every restart. The target is Postgres. The Flyway migrations already run in H2's PostgreSQL mode, so the swap is a datasource change plus a migration check.
- **`GET /users` is open** only to feed the demo user switcher. In production it would be removed or protected.
- **Single instance.** The service runs as one instance, and health is the only actuator endpoint exposed.
- **No cleanup job for idempotency records.** An expired record is removed only when the same key is reused, so production needs a scheduled cleanup. Until a record is removed, its stored response keeps a copy of the request's reason text.
- **Postgres needs two changes.** Set `lock_timeout`, so that a retry of a call still in flight gets `409` instead of waiting. Replace the H2 generated column behind the one-pending rule with a partial unique index (H2 has no partial indexes).
- **Concurrent decisions are not handled cleanly.** When two reviewers decide the same request at the same moment, or a decision, a revision and a withdrawal reach it together, only one change is stored, but the other caller gets a `500` with Spring Boot's default error body instead of a `409`. Nothing is half-written, and a retry with the same key is answered from the stored state.
- **Events are only logged, with no outbox.** If the service stops between a commit and the log line, that event is stored but never announced. Production needs an outbox table and a broker.
- **Errors are Spring's built-in Problem Details only.** There is no catalogue of error types. This is by design.

## Time spent

_To be filled in by the author._
