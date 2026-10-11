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

A criterion gets its row in this table when its tests pass.

| Criterion | Proved by |
|-----------|-----------|

## Plan

One slice, with the failing tests committed before the code:

1. **The history view.** `GET /pricing-exceptions/{id}/history` reads the stored events of a request in insert order and puts the content and advice of the matching revision next to each one. Nothing new is stored: events, revisions and the decline note are already written by submit, revise, decide and withdraw.
2. **The publisher.** A plain class, `RequestEventPublisher`, takes over storing an event from the services. It stores the event in the caller's transaction and writes its log line only after that transaction commits, so a rolled-back change is never announced. In production the log line is replaced by a message to a broker, in the same place.

Not in this story: an outbox table. If the service stops between the commit and the log line, that one event is stored but not announced.

## Review notes

Not reviewed yet.

## Verification evidence

Not captured yet: the story is still open.
