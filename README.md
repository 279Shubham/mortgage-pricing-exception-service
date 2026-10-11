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
2. **Architecture test (ArchUnit):** checks the layering (controllers call services, and only services use the Spring Data repositories) and bans reading the system time directly: any `java.time` `now()` without a clock argument (method references such as `Instant::now` included), `System.currentTimeMillis()`, and system `Clock` factories outside the clock config. Time always comes from the injected clock.
3. **End-to-end suite:** starts the full service on a random port and calls it over HTTP.

The tests are end-to-end only, and their names follow the story acceptance criteria (for example `US-01 AC-3: a seeded X-User-Id resolves to that user and roles on /me`), so the test report reads as a checklist of user stories.

## Explore the API

**Identity.** Every call says who the caller is through the `X-User-Id` header, using one of the seeded user ids (see [Seed data](#seed-data)). `/actuator/health` and `/users` are open. Every other endpoint needs a known id: one interceptor checks the caller before any controller runs, so a new endpoint is protected by default. A missing or unknown id gets `401` with an `application/problem+json` body:

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

## Stakeholder conflict: changing a submitted discount

## Scope choices

## Architecture and decisions

## How AI agents were used and verified

## Production readiness gaps

- **Identity is demo-only.** The `X-User-Id` header is a demo-only trust model: any caller can claim any seeded id. In production the `X-User-Id` lookup in `CallerInterceptor` is replaced by an OAuth2/OIDC resource server that takes the caller from a verified token.
- **Storage is in-memory.** H2 runs in memory and resets on every restart. The target is Postgres. The Flyway migrations already run in H2's PostgreSQL mode, so the swap is a datasource change plus a migration check.
- **`GET /users` is open** only to feed the demo user switcher. In production it would be removed or protected.
- **Single instance.** The service runs as one instance, and health is the only actuator endpoint exposed.

## Time spent
