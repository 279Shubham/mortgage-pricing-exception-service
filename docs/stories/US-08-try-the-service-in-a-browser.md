# US-08 Try the service in a browser

As someone assessing the service, I want one page where I can act as each seeded user, so that I can walk the whole flow without writing curl commands.

## Acceptance criteria

- **AC-1:** The page is served by the service itself at `/` and works offline: one HTML file, no external scripts, fonts or styles.
- **AC-2:** A user switcher lists the seeded users. Every call the page makes is sent as the chosen user.
- **AC-3:** A relationship manager can submit a request for an application, and revise or withdraw their own pending request.
- **AC-4:** A reviewer sees the requests by status with the advice, and can approve or decline the current revision. A decline asks for a note.
- **AC-5:** Anyone can open a request's full history and read the approved discount of an application.
- **AC-6:** The page does not decide who may do what. It offers the actions and shows the service's answer, including a refusal, as the service sent it.

## Tests

End-to-end test: `BrowserPageE2ETest`.

| Criterion | Proved by |
|-----------|-----------|
| AC-1 | `the page is served at the root and loads nothing from another host` |
| AC-2 to AC-6 | No automated test: the page only calls endpoints that the other stories test. It was walked through by hand; see Verification evidence |

## Plan

One static file, `src/main/resources/static/index.html`, with plain JavaScript and `fetch`. No build step and no framework. Every POST gets a fresh `Idempotency-Key`. Text from the service is put on the page as text, never as HTML.

Not in this story: styling beyond what makes it readable, paging, live refresh.

## Review notes

Not reviewed yet.

## Verification evidence

Not captured yet: the story is still open.
