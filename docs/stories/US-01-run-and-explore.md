# US-01 Run and explore the service

As a Blue Harvest reviewer, I want to start the service and run its test suite with one command each against fictional seed data, so that I can explore and assess it without installing a JDK.

## Acceptance criteria

- **AC-1:** From a fresh clone, `make run` starts the service in Docker on arm64 and amd64 without a local JDK, and `./gradlew bootRun` starts it on a machine with JDK 21.
- **AC-2:** `make test` runs the e2e suite inside a container and passes, with no local JDK needed.
- **AC-3:** A call carrying a seeded user's `X-User-Id` resolves to that user and their roles (RM, reviewer, the labelled dual-role user, the `APPLICATION_PROCESS` service user), and a missing or unknown id gets 401. Seeded applications are fictional and each carries a standard rate in bps.
- **AC-4:** Errors use Spring's built-in Problem Details format with the correct status code (e.g. 401 for a missing or unknown user). There is no custom error-type scheme.
- **AC-5:** The build fails when a method exceeds cyclomatic or cognitive complexity 8, or when the ArchUnit test finds a layering violation or an `Instant.now()` outside the clock.

## Tests

End-to-end tests: `RunAndExploreE2ETest`. Build gates: `ArchitectureTest` and the PMD complexity check. Every test name starts with the criterion it proves.

| Criterion | Proved by |
|-----------|-----------|
| AC-1 | `the service reports UP on /actuator/health without X-User-Id`, plus the captured runs in evidence (c), (d) and (e) |
| AC-2 | The captured `make test` run in evidence (b) |
| AC-3 | `a seeded X-User-Id resolves to that user and roles on /me`<br>`each seeded X-User-Id resolves on /me to exactly its roles`<br>`anyone can list the six seeded users and their roles without X-User-Id`<br>`a known caller lists the six fictional seeded applications with integer standard rates in bps`<br>`exactly one seeded application has a standard rate below the 200 bps discount cap`<br>`every endpoint except the open ones is a 401 without X-User-Id`<br>`the actuator discovery page is closed and health is the only actuator endpoint` |
| AC-4 | `a missing X-User-Id is a 401 Problem Details response`<br>`an unknown X-User-Id is a 401 Problem Details response`<br>`an empty X-User-Id is a 401 Problem Details response`<br>`listing applications without X-User-Id is a 401 Problem Details response`<br>`an unknown path is a 404 Problem Details response even without X-User-Id` |
| AC-5 | The five `ArchitectureTest` rules and the PMD gate, each shown failing on a temporary probe in evidence (f) |

## Plan

The story was delivered in four thin slices, each one runnable and tested before the next started:

1. **Runnable, gated build.** A generated Spring Boot 4.1 build with the PMD complexity gate, Flyway schema and seed migrations, and the shared end-to-end harness (one Spring context on a random port, database reset before each test, an injectable clock). Proven on `/actuator/health` from a running service.
2. **Identity.** `GET /me` resolves the `X-User-Id` caller through `UserService` and the Spring Data `UserRepository`, with 401 Problem Details for a missing or unknown id. Then the ArchUnit architecture test (layering, no direct clock calls), with temporary probes proving that both gates fail the build.
3. **Exploring the seed.** `GET /applications` through `ApplicationService` and `MortgageApplicationRepository`, the open `GET /users`, all six seeded ids on `/me`, and the caller and error edge cases (unknown or empty id gives 401, an unknown path gives 404, both as Problem Details).
4. **One-command run and test.** A four-stage Dockerfile, `make run` / `make stop` / `make test`, an amd64 run under emulation, a check from a fresh clone, and this documentation.

**Follow-up (2026-10-11).** The code was reorganised from ports and adapters into a plain controller → service → repository layout, for simpler code. Only the Spring Data repositories are interfaces. Behaviour is unchanged: the same 16 end-to-end tests pass, and the architecture test now enforces the new layering.

### Decisions

What was decided while planning this story, and why:

- **Docker and Make first, Gradle as the fallback.** The reviewer's machine is unknown, so one command must work with only Docker installed. `./gradlew bootRun` stays for anyone who has JDK 21.
- **Spring Boot 4.1 instead of 3.5.** Open-source support for the 3.5 line ended on 2026-06-30, so the supported line was chosen, at the cost of some newer APIs.
- **Identity is a header checked against seeded users.** `X-User-Id` is enough to enforce the roles in a demo and costs almost nothing. Real authentication is a production gap, not part of this service.
- **Six seeded users, one of them with both roles.** A second relationship manager and a second reviewer make "not your request" visible, and the dual-role user makes the four-eyes rule testable. All names are fictional, and ids are readable (`rm-lana`, `MA-1001`) so that calls and tests read easily. The users were renamed on 2026-10-12; only the names and ids changed.
- **Errors stay minimal.** Spring's built-in Problem Details with correct status codes, and no catalogue of error types or per-field error lists.
- **End-to-end tests only.** Every test calls the running service over HTTP, so the test report reads as the list of acceptance criteria. The one exception is an architecture test that guards the layering and the injected clock.
- **Plain layers instead of ports and adapters.** Controller, service, repository. An interface exists only where a swap is realistic: the Spring Data repositories and, later, the review advisor.

## Review notes

The story's code was reviewed twice. Every warning was fixed in its own commit, and the build was green after each one.

**First review: six warnings, all fixed.**

| Finding | Resolution | Commit |
|---------|------------|--------|
| An endpoint was protected only if its handler happened to declare a caller parameter | One interceptor now checks the caller before any controller runs, with an explicit open list. A new test calls every mapped endpoint without the header and expects 401 | `6250506` |
| The 401 response had no `WWW-Authenticate` header | The header is sent and the test asserts its exact value | `6f65bb2` |
| The clock rule missed method references such as `Instant::now` and several other ways to read the system time | The rule now covers calls and method references, and system clock factories are allowed only in the clock config | `c0c3b48` |
| The test clock was written on the test thread and read on server threads with no visibility guarantee | The field is `volatile` | `0cfda5a` |
| Role values in the database were unconstrained, so one bad row would turn `/users` into a 500 | A new migration adds a `CHECK` constraint on the role column | `7db3291` |
| The layering rule did not stop classes outside the layers from using a repository | A new rule allows only services to depend on repositories | `66816f1` |

**Second review, after those fixes: two warnings, both fixed.**

| Finding | Resolution | Commit |
|---------|------------|--------|
| Actuator endpoints are served outside the caller check, so the health entry in the open list did nothing and the discovery page answered without a caller | Discovery is switched off, health is the only exposed actuator endpoint, and a test pins that down | `e28176b` |
| The clock rule still allowed `InstantSource.system()`, `new Date()` and `Calendar.getInstance()` | The rule covers every `InstantSource`, and a new rule bans the legacy date types and Hibernate's timestamp annotations | `1f05524` |

**Left open.** Ten minor notes were recorded and not fixed. The ones worth knowing: the caller check covers controller handlers only; the test that sweeps every endpoint keeps its own copy of the open-path list; the role `CHECK` list duplicates the `Role` enum; and the README's no-SDKMAN instruction still shows an SDKMAN command.

## Verification evidence

All evidence below is real captured output from 2026-10-11.

### (a) End-to-end tests, `./gradlew check` (commit `4a8af76`)

```
RunAndExploreE2ETest > US-01 AC-4: an empty X-User-Id is a 401 Problem Details response PASSED
RunAndExploreE2ETest > US-01 AC-3: exactly one seeded application has a standard rate below the 200 bps discount cap PASSED
RunAndExploreE2ETest > US-01 AC-3: a seeded X-User-Id resolves to that user and roles on /me PASSED
RunAndExploreE2ETest > US-01 AC-4: listing applications without X-User-Id is a 401 Problem Details response PASSED
RunAndExploreE2ETest > US-01 AC-4: an unknown X-User-Id is a 401 Problem Details response PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "rm-lana" -> "RM" PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "rm-chloe" -> "RM" PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "reviewer-lex" -> "REVIEWER" PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "reviewer-jonathan" -> "REVIEWER" PASSED
RunAndExploreE2ETest > US-01 AC-3: each seeded X-User-Id resolves on /me to exactly its roles > "dual-clark" -> "RM|REVIEWER" PASSED
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
$ curl -H 'X-User-Id: rm-lana' http://localhost:18080/me
{"id":"rm-lana","displayName":"Lana (Relationship Manager)","roles":["RM"]}
```

Container log: `Started PricingExceptionServiceApplication in 1.68 seconds`. `make stop` stopped and removed the container. The runtime image runs as `uid=10001(app)`, and `/app` holds only `application.jar` and `lib`.

### (d) amd64 under emulation on an arm64 host (commit `451d49b`)

`docker buildx build --platform linux/amd64` followed by `docker run --platform linux/amd64`:

```
$ docker exec pes-amd64 uname -m
x86_64
$ curl -H 'X-User-Id: rm-lana' http://localhost:18082/me
{"id":"rm-lana","displayName":"Lana (Relationship Manager)","roles":["RM"]}
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
$ curl -H 'X-User-Id: rm-lana' http://localhost:18081/me
{"id":"rm-lana","displayName":"Lana (Relationship Manager)","roles":["RM"]}
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
