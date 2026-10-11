# US-05 Read the approved discount

As the mortgage application process, I want to read the effective approved discount for an application, so that I price the mortgage with the latest approval and nothing else.

## Acceptance criteria

- **AC-1:** The application process, or any internal user, gets 200 with a list holding the latest approval for an application: `requestId`, `revision`, `discountBps`, `approvedBy`, `decidedAt`.
- **AC-2:** An application without an approval gets 200 with an empty list.
- **AC-3:** After an approval the RM can submit a new request. The existing approval stays effective until the new request is approved; then the lookup returns the newer approval.
- **AC-4:** After a decline the RM can resubmit. The new request is linked to the declined one by `previousRequestId`, and the declined request stays declined.

## Tests

End-to-end tests: `ApprovedDiscountE2ETest`. Every test name starts with the criterion it proves.

| Criterion | Proved by |
|-----------|-----------|
| AC-1 | `the application process reads the latest approval for an application`<br>`any internal user reads the same approval` |
| AC-2 | `an application without an approval gets an empty list` |
| AC-3 | `a new request after an approval leaves it effective until the new one is approved` |
| AC-4 | `a resubmit after a decline is a new request linked by previousRequestId` |

## Plan

The story is delivered in two thin slices. In each one the failing tests are committed before the code:

1. **The lookup.** `GET /applications/{applicationId}/approved-discounts` returns a list with the latest approval of that application, or an empty list. The answer is always 200 and always a list, so the application process has one shape to handle and never has to tell "no approval" apart from an error. The discount comes from the exact revision the reviewer approved. Any known user may call it, as with reading a request.
2. **Follow-up submissions.** After an approval the RM can submit a new request for the same application; the earlier approval stays effective until the new request is itself approved, and then the newer approval wins. After a decline the RM can submit again; the service links the new request to the declined one through `previousRequestId` and leaves the declined request as it is. The link is set by the service, not sent by the caller.

Product assumptions, in plain words: the lookup does not check that the application exists, so an unknown application id gets an empty list like any application without an approval; "latest" means the approval with the most recent decision time.

## Review notes

Not reviewed yet.

## Verification evidence

Not captured yet: the story is still open.
