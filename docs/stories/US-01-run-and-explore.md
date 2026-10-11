# US-01 Run and explore the service

As a Blue Harvest reviewer, I want to start the service and run its test suite with one command each against fictional seed data, so that I can explore and assess it without installing a JDK.

## Acceptance criteria

- **AC-1:** From a fresh clone, `make run` starts the service in Docker on arm64 and amd64 without a local JDK, and `./gradlew bootRun` starts it on a machine with JDK 21.
- **AC-2:** `make test` runs the e2e suite inside a container and passes, with no local JDK needed.
- **AC-3:** A call carrying a seeded user's `X-User-Id` resolves to that user and their roles (RM, reviewer, the labelled dual-role user, the `APPLICATION_PROCESS` service user), and a missing or unknown id gets 401. Seeded applications are fictional and each carries a standard rate in bps.
- **AC-4:** Errors use Spring's built-in Problem Details format with the correct status code (e.g. 401 for a missing or unknown user). There is no custom error-type scheme.
- **AC-5:** The build fails when a method exceeds cyclomatic or cognitive complexity 8, or when the ArchUnit test finds a layering violation or an `Instant.now()` outside the clock.

## Plan

The story was delivered in four thin slices, each one runnable and tested before the next started:

1. **Runnable, gated build.** A generated Spring Boot 4.1 build with the PMD complexity gate, Flyway schema and seed migrations, and the shared end-to-end harness (one Spring context on a random port, database reset before each test, an injectable clock). Proven on `/actuator/health` from a running service.
2. **Identity.** `GET /me` resolves the `X-User-Id` caller through `UserService` and the Spring Data `UserRepository`, with 401 Problem Details for a missing or unknown id. Then the ArchUnit architecture test (layering, no direct clock calls), with temporary probes proving that both gates fail the build.
3. **Exploring the seed.** `GET /applications` through `ApplicationService` and `MortgageApplicationRepository`, the open `GET /users`, all six seeded ids on `/me`, and the caller and error edge cases (unknown or empty id gives 401, an unknown path gives 404, both as Problem Details).
4. **One-command run and test.** A four-stage Dockerfile, `make run` / `make stop` / `make test`, an amd64 run under emulation, a check from a fresh clone, and this documentation.

**Follow-up (2026-10-11).** The code was reorganised from ports and adapters into a plain controller → service → repository layout, for simpler code. Only the Spring Data repositories are interfaces. Behaviour is unchanged: the same 16 end-to-end tests pass, and the architecture test now enforces the new layering.

## Review notes

Code-review findings for this story, and how each was resolved, are appended here when the story closes.

## Verification evidence

All evidence below is real captured output from 2026-10-11.

### (a) End-to-end tests, `./gradlew check` (commit `4a8af76`)

```
RunAndExploreE2ETest > US-01 AC-4: an empty X-User-Id is a 401 Problem Details response PASSED
RunAndExploreE2ETest > US-01 AC-3: exactly one seeded application has a standard rate below the 200 bps discount cap PASSED
RunAndExploreE2ETest > US-01 AC-3: a seeded X-User-Id resolves to that user and roles on /me PASSED
RunAndExploreE2ETest > US-01 AC-4: listing applications without X-User-Id is a 401 Problem Details response PASSED
RunAndExploreE2ETest > US-01 AC-4: an unknown X-User-Id is a 401 Problem Details response PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "rm-anna" -> "RM" PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "rm-carl" -> "RM" PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "reviewer-ben" -> "REVIEWER" PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "reviewer-dana" -> "REVIEWER" PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "dual-erin" -> "RM|REVIEWER" PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "application-process" -> "APPLICATION_PROCESS" PASSED
RunAndExploreE2ETest > US-01 AC-4: an unknown path is a 404 Problem Details response even without X-User-Id PASSED
RunAndExploreE2ETest > US-01 AC-3: anyone can list the six seeded users and their roles without X-User-Id PASSED
RunAndExploreE2ETest > US-01 AC-4: a missing X-User-Id is a 401 Problem Details response PASSED
RunAndExploreE2ETest > US-01 AC-1: the service reports UP on /actuator/health without X-User-Id PASSED
RunAndExploreE2ETest > US-01 AC-3: a known caller lists the six fictional seeded applications with integer standard rates in bps PASSED
```

### (b) `make test` in a container (commit `d5ee30e`)

The same 16 end-to-end tests and the 3 architecture tests passed inside `eclipse-temurin:21-jdk-noble`, after `pmdMain`:

```
> Task :pmdMain
ArchitectureTest > timeComesFromInjectedClock PASSED
ArchitectureTest > systemClockOnlyInConfig PASSED
ArchitectureTest > controllerServiceRepositoryLayers PASSED
RunAndExploreE2ETest > US-01 AC-4: an empty X-User-Id is a 401 Problem Details response PASSED
...
BUILD SUCCESSFUL in 39s
```

### (c) `make run PORT=18080` and `/me` (commit `451d49b`)

```
$ curl -H 'X-User-Id: rm-anna' http://localhost:18080/me
{"id":"rm-anna","displayName":"Anna (Relationship Manager)","roles":["RM"]}
```

Container log: `Started PricingExceptionServiceApplication in 1.68 seconds`. `make stop` stopped and removed the container. The runtime image runs as `uid=10001(app)`, and `/app` holds only `application.jar` and `lib`.

### (d) amd64 under emulation on an arm64 host (commit `451d49b`)

`docker buildx build --platform linux/amd64` followed by `docker run --platform linux/amd64`:

```
$ docker exec pes-amd64 uname -m
x86_64
$ curl -H 'X-User-Id: rm-anna' http://localhost:18082/me
{"id":"rm-anna","displayName":"Anna (Relationship Manager)","roles":["RM"]}
```

Container log: `Started PricingExceptionServiceApplication in 19.761 seconds` (emulated).

### (e) `./gradlew bootRun` with JDK 21 (commit `451d49b`)

`PORT=18081 ./gradlew bootRun` with JDK 21.0.10 (Temurin):

```
o.f.core.internal.command.DbMigrate      : Successfully applied 2 migrations to schema "public", now at version v2 (execution time 00:00.002s)
o.s.boot.tomcat.TomcatWebServer          : Tomcat started on port 18081 (http) with context path '/'
c.b.p.PricingExceptionServiceApplication : Started PricingExceptionServiceApplication in 1.551 seconds (process running for 1.663)

$ curl http://localhost:18081/actuator/health
{"groups":["liveness","readiness"],"status":"UP"}
$ curl -H 'X-User-Id: rm-anna' http://localhost:18081/me
{"id":"rm-anna","displayName":"Anna (Relationship Manager)","roles":["RM"]}
```

The same smokes also passed earlier in the story, when the health endpoint and `/me` were first built.

### (f) The gates fail the build (commits `de7d41c` and `d5ee30e`)

Each line is the failing output from a temporary probe file. The probes were deleted straight after each run and never committed.

- PMD cyclomatic, at the boundary (a method with complexity 8 passed, 9 failed):
  `GateProbe.java:3:	CyclomaticComplexity:	The method 'nine(int)' has a cyclomatic complexity of 9.`
- PMD cognitive:
  `GateProbe.java:3:	CognitiveComplexity:	The method 'nested(int, int, int, int)' has a cognitive complexity of 10, current threshold is 9`
- ArchUnit clock rule:
  `Method <com.blueharvest.pricingexception.ClockProbe.leak()> calls method <java.time.Instant.now()> in (ClockProbe.java:4)`
- ArchUnit layering rule (a temporary controller class holding a `UserRepository` field):
  `Field <com.blueharvest.pricingexception.controller.LayerProbe.users> has type <com.blueharvest.pricingexception.repository.UserRepository> in (LayerProbe.java:0)`
