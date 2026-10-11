# US-07 See a request's full history

As an internal user, I want to see everything that happened to a pricing exception request, so that I can tell who did what, when, and what the advisor said at the time of each decision.

## Acceptance criteria

- **AC-1:** `GET /pricing-exceptions/{id}/history` returns every event of the request in the order it happened: submitted, revised, approved, declined, withdrawn. Each event carries its type, the revision it applies to, who did it and when.
- **AC-2:** Each event shows the discount, the reason and the advice of the revision it applies to, so the advice a reviewer saw stands next to the human decision. A decline also shows the reviewer's note.
- **AC-3:** The history shows the request's status and, for a request submitted after a declined one, the id of that earlier request.
- **AC-4:** Any known internal user can read the history. An unknown request gets 404.
- **AC-5:** Every stored event is also published after the change is committed. The publisher writes one log line per event. A call that is refused publishes nothing.

## Tests

End-to-end tests: `RequestHistoryE2ETest`. Every test name starts with the criterion it proves.

| Criterion | Proved by |
|-----------|-----------|
| AC-1 | `the history lists every event in order with its revision, actor and time` |
| AC-2 | `each event shows the discount, reason and advice of its revision`<br>`a decline shows the reviewer's note next to the advice` |
| AC-3 | `the history shows the status and the earlier declined request` |
| AC-4 | `the history lists every event in order with its revision, actor and time` (read by another relationship manager)<br>`the history of an unknown request gets 404` |
| AC-5 | `each stored event is published as a log line, and a refused call publishes nothing` |

## Plan

One slice, with the failing tests committed before the code:

1. **The history view.** `GET /pricing-exceptions/{id}/history` reads the stored events of a request in insert order and puts the content and advice of the matching revision next to each one. Nothing new is stored: events, revisions and the decline note are already written by submit, revise, decide and withdraw.
2. **The publisher.** A plain class, `RequestEventPublisher`, takes over storing an event from the services. It stores the event in the caller's transaction and writes its log line only after that transaction commits, so a rolled-back change is never announced. In production the log line is replaced by a message to a broker, in the same place.

Not in this story: an outbox table. If the service stops between the commit and the log line, that one event is stored but not announced.

## Review notes

Not reviewed yet.

## Verification evidence

Captured at `535a7b7`.

**(a) Full build.** `./gradlew check` (complexity gate, architecture test, end-to-end suite) passed: 142 tests, 0 failures, 0 errors. Six of them are the US-07 tests. Before the code, at `c0e68b0`, five of those six failed; the 404 test passed only because the path did not exist yet.

**(b) A running service.** One request was submitted by `rm-anna`, revised by her, and declined by `reviewer-ben`. In between, `rm-carl` tried to revise it and got `403`. Then `rm-carl` read the history:

```
revise 200
revise by rm-carl 403
decline 200

GET /pricing-exceptions/410641d6-99e4-4dbd-bbe6-ca31714041f1/history
{"requestId":"410641d6-99e4-4dbd-bbe6-ca31714041f1","applicationId":"MA-1001","status":"DECLINED","previousRequestId":null,"events":[
 {"type":"SUBMITTED","revisionNumber":1,"actorId":"rm-anna","occurredAt":"2026-10-11T21:50:47.734Z","discountBps":75,"reason":"Retention offer matching a competitor quote","advice":{"recommendation":"REVIEW_CAREFULLY","rationale":"Review carefully: the discount of 75 bps is above the advisory threshold of 50 bps."},"note":null},
 {"type":"REVISED","revisionNumber":2,"actorId":"rm-anna","occurredAt":"2026-10-11T21:50:47.781Z","discountBps":40,"reason":"Customer accepted a smaller discount after the call","advice":{"recommendation":"APPROVE","rationale":"The discount and the length of the reason are within the advisory thresholds."},"note":null},
 {"type":"DECLINED","revisionNumber":2,"actorId":"reviewer-ben","occurredAt":"2026-10-11T21:50:47.813Z","discountBps":40,"reason":"Customer accepted a smaller discount after the call","advice":{"recommendation":"APPROVE","rationale":"The discount and the length of the reason are within the advisory thresholds."},"note":"The written offer was not attached"}]}
```

The advisor recommended approving revision 2 and the reviewer declined it: both stand next to each other in the last event.

**(c) Published events.** The service log of the same run has one line per stored event and none for the refused revise:

```
c.b.p.service.RequestEventPublisher      : Request event SUBMITTED on request 410641d6-99e4-4dbd-bbe6-ca31714041f1 revision 1 by rm-anna at 2026-10-11T21:50:47.734Z
c.b.p.service.RequestEventPublisher      : Request event REVISED on request 410641d6-99e4-4dbd-bbe6-ca31714041f1 revision 2 by rm-anna at 2026-10-11T21:50:47.781Z
c.b.p.service.RequestEventPublisher      : Request event DECLINED on request 410641d6-99e4-4dbd-bbe6-ca31714041f1 revision 2 by reviewer-ben at 2026-10-11T21:50:47.813Z
```

Not captured: a run of the suite inside the container (`make test`).
