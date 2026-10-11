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

The tests are end-to-end only, and their names follow the story acceptance criteria (for example `US-01 AC-3: a seeded X-User-Id resolves to that user and roles on /me`), so the test report reads as a checklist of user stories.

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
  -H 'Idempotency-Key: readme-demo-1' \
  -H 'Content-Type: application/json' \
  -d '{"discountBps":25,"reason":"Retention offer matching a competitor quote"}'
# HTTP/1.1 201
# Location: /pricing-exceptions/49222df3-2378-49eb-9019-be689e685e21
# Content-Type: application/json
#
# {"id":"49222df3-2378-49eb-9019-be689e685e21","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-anna",
#  "submittedAt":"2026-10-11T15:32:18.613Z","currentRevision":{"number":1,"discountBps":25,
#  "reason":"Retention offer matching a competitor quote","createdAt":"2026-10-11T15:32:18.613Z"}}
```

The id and the times differ on every run. Use the `Location` you get back in the next call.

**Read it back.** Any known user can read a request, here a reviewer:

```bash
curl -H 'X-User-Id: reviewer-ben' localhost:8080/pricing-exceptions/49222df3-2378-49eb-9019-be689e685e21
# {"id":"49222df3-2378-49eb-9019-be689e685e21","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-anna", ...}
```

**Retry safely.** Sending the exact same submit again, with the same key, does not create a second request. It returns the stored answer: the same `201`, the same `Location` and the same body, byte for byte.

Reusing the key for a different body (here `"discountBps":30`) is refused with `422`:

```bash
curl -i -X POST localhost:8080/applications/MA-1001/pricing-exceptions \
  -H 'X-User-Id: rm-anna' \
  -H 'Idempotency-Key: readme-demo-1' \
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
  -d '{"discountBps":25,"reason":"Retention offer matching a competitor quote"}'
# HTTP/1.1 400
# Content-Type: application/problem+json
#
# {"detail":"Required header 'Idempotency-Key' is not present.","instance":"/applications/MA-1001/pricing-exceptions","status":400,"title":"Bad Request"}
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
- **Reason.** 10 to 1000 characters, counted as characters, not bytes.
- **One pending request per application.** A second one gets `409`. The database enforces this, so two submits that race still leave exactly one pending request.
- **Reading.** Any known internal user may read any request.
- **Unknown application.** A submit for an application id that does not exist gets `404`.
- **`Idempotency-Key`.** Required on every POST. Any non-blank value of at most 255 characters is accepted and used exactly as sent (no trimming, no change of case). A key belongs to one caller and one route, so another user, or the same user on another application, can use the same key without clashing. A key is kept for `pricing.idempotency.ttl` (default `24h`). The same key with the same body replays the stored response; a different body gets `422`; a retry while the first call is still running gets `409`. A replay returns the original status, `Location`, content type and body; other response headers are not stored. This follows the convention of the expired IETF Idempotency-Key draft and common industry practice. It is not a ratified standard.

## Stakeholder conflict: changing a submitted discount

## Scope choices

## Architecture and decisions

## How AI agents were used and verified

## Production readiness gaps

- **Identity is demo-only.** The `X-User-Id` header is a demo-only trust model: any caller can claim any seeded id. In production the `X-User-Id` lookup in `CallerInterceptor` is replaced by an OAuth2/OIDC resource server that takes the caller from a verified token.
- **Storage is in-memory.** H2 runs in memory and resets on every restart. The target is Postgres. The Flyway migrations already run in H2's PostgreSQL mode, so the swap is a datasource change plus a migration check.
- **`GET /users` is open** only to feed the demo user switcher. In production it would be removed or protected.
- **Single instance.** The service runs as one instance, and health is the only actuator endpoint exposed.
- **No cleanup job for idempotency records.** An expired record is removed only when the same key is reused, so production needs a scheduled cleanup. Until a record is removed, its stored response keeps a copy of the request's reason text.
- **Postgres needs two changes.** Set `lock_timeout`, so that a retry of a call still in flight gets `409` instead of waiting. Replace the H2 generated column behind the one-pending rule with a partial unique index (H2 has no partial indexes).
- **Errors are Spring's built-in Problem Details only.** There is no catalogue of error types. This is by design.

## Time spent
