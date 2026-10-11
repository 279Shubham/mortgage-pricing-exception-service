# US-03 Retry safely

As an upstream caller submitting on an RM's behalf, I want to retry a submit safely with the same Idempotency-Key, so that a network retry never creates a duplicate request.

## Acceptance criteria

- **AC-1:** Retrying a submit with the same `Idempotency-Key` and payload returns the original status, body and `Location`, and creates nothing new.
- **AC-2:** The same key with a different payload gets 422.
- **AC-3:** A POST without a usable `Idempotency-Key` header gets 400.
- **AC-4:** A retry while the original is still in flight gets 409; the key claim is made in the same transaction as the business write.
- **AC-5:** The same key used by another user or on another route is independent. After the configured TTL (default 24h, advanced with the test clock), the key can be reused.

## Plan

The story was delivered in three thin slices, each one tested before the next started:

1. **Replay and 422** (commits `502fe2a` for the table, `2103215` for the code). A plain `IdempotencyService` wraps the submit. It claims the key, runs the submit and stores the response in one transaction, so a crash can never leave a key claimed without its request, or the other way round. A retry with the same body gets the stored response back byte for byte; a different body gets 422.
2. **In-flight retries** (failing tests `dd8bad7`, then code `8dcee28`). A retry that meets an uncommitted claim waits for the database lock and, when the wait times out, gets 409. Five identical retries sent at once share one request and one `Location`.
3. **Key rules, scope and expiry** (failing tests `d73b159`, then code `dd8532a`). A missing, empty, blank or over-long key gets 400. A key belongs to one caller and one route. A key can be reused once `pricing.idempotency.ttl` (default 24h) has passed on the injected clock; expired records are removed when the same key comes back.

The behaviour follows the convention of the expired IETF Idempotency-Key draft and common industry practice. It is not a ratified standard.

## Review notes

Code-review findings for this story, and how each was resolved, are appended here when the story closes.

## Verification evidence

All evidence below is real captured output from 2026-10-11.

### (a) End-to-end tests, `./gradlew check --rerun-tasks` (commit `1f05524`)

All 12 US-03 tests passed. The in-flight test took 2.036 s, which is the database's 2-second lock timeout doing its job:

```
RetrySafelyE2ETest > US-03 AC-4: five concurrent identical retries create one request PASSED
RetrySafelyE2ETest > US-03 AC-5: a key can be reused once the 24h TTL has passed PASSED
RetrySafelyE2ETest > US-03 AC-3: keys are used verbatim, so a key that differs only by case is a different key PASSED
RetrySafelyE2ETest > US-03 AC-3: a submit without a usable Idempotency-Key gets 400 > US-03 AC-3: a "missing" Idempotency-Key gets 400 PASSED
RetrySafelyE2ETest > US-03 AC-3: a submit without a usable Idempotency-Key gets 400 > US-03 AC-3: a "empty" Idempotency-Key gets 400 PASSED
RetrySafelyE2ETest > US-03 AC-3: a submit without a usable Idempotency-Key gets 400 > US-03 AC-3: a "blank" Idempotency-Key gets 400 PASSED
RetrySafelyE2ETest > US-03 AC-3: a submit without a usable Idempotency-Key gets 400 > US-03 AC-3: a "256-character" Idempotency-Key gets 400 PASSED
RetrySafelyE2ETest > US-03 AC-3: a 255-character Idempotency-Key is accepted PASSED
RetrySafelyE2ETest > US-03 AC-5: the same key from another user or on another route is independent PASSED
RetrySafelyE2ETest > US-03 AC-4: a retry while the original is still in flight gets 409 PASSED
RetrySafelyE2ETest > US-03 AC-1: a retry with the same key and payload replays the original status, body and Location PASSED
RetrySafelyE2ETest > US-03 AC-2: the same key with a different payload gets 422 PASSED
```

Both concurrency tests also passed on three back-to-back reruns when they were first written.

### (b) `make test` in a container (commit `1f05524`)

The whole suite (5 architecture rules, 18 US-01, 41 US-02 and 12 US-03 tests) passed inside `eclipse-temurin:21-jdk-noble`:

```
#13 43.13 RetrySafelyE2ETest > US-03 AC-4: a retry while the original is still in flight gets 409 PASSED
#13 43.23 RetrySafelyE2ETest > US-03 AC-1: a retry with the same key and payload replays the original status, body and Location PASSED
#13 43.33 RetrySafelyE2ETest > US-03 AC-2: the same key with a different payload gets 422 PASSED
...
#13 45.53 BUILD SUCCESSFUL in 45s
```

### (c) Replay, 422 and 400 on a running service (commit `1f05524`)

`PORT=18083 ./gradlew bootRun` with JDK 21 ("Successfully applied 5 migrations"). The first submit with key `readme-demo-1` got 201 (see the US-02 trail). Then:

```
# the identical retry, same key and body
HTTP/1.1 201
Location: /pricing-exceptions/49222df3-2378-49eb-9019-be689e685e21
Content-Type: application/json
Content-Length: 297

{"id":"49222df3-2378-49eb-9019-be689e685e21","applicationId":"MA-1001","status":"PENDING","requestedBy":"rm-anna","submittedAt":"2026-10-11T15:32:18.613Z","currentRevision":{"number":1,"discountBps":25,"reason":"Retention offer matching a competitor quote","createdAt":"2026-10-11T15:32:18.613Z"}}

# the same key with "discountBps":30
HTTP/1.1 422
Content-Type: application/problem+json

{"detail":"This Idempotency-Key was already used with a different request","instance":"/applications/MA-1001/pricing-exceptions","status":422,"title":"Unprocessable Content"}

# no Idempotency-Key header
HTTP/1.1 400
Content-Type: application/problem+json

{"detail":"Required header 'Idempotency-Key' is not present.","instance":"/applications/MA-1001/pricing-exceptions","status":400,"title":"Bad Request"}
```

`cmp` confirmed that the replayed body is byte-identical to the first one. The service was stopped after the calls.
