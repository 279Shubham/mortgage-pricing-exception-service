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
| AC-2 to AC-6 | No automated test: the page only calls endpoints that the other stories test. Walked through in a browser; see Verification evidence |

## Plan

One static file, `src/main/resources/static/index.html`, with plain JavaScript and `fetch`. No build step and no framework. Every POST gets a fresh `Idempotency-Key`. Text from the service is put on the page as text, never as HTML.

Not in this story: styling beyond what makes it readable, paging, live refresh.

### Decisions

What was decided while planning this story, and why:

- **Built last, by hand, as one file.** The page is a way to try the service, not a product. No framework and no build step keeps it readable in one sitting and working offline.
- **The page does not hide actions by role.** Every action is offered to every user, so the person trying it can watch the service refuse.

## Review notes

Not reviewed yet.

## Verification evidence

**(a) Full build.** `./gradlew check` passed with the page in place: 143 tests, 0 failures, 0 errors.

**(b) Served by a running service.** `GET /` answered `200 text/html`, and the page's script passed a syntax check (`node --check`).

**(c) Walked through in Chrome** at `82ccb78`, by an AI agent driving the page, not by a person:

- The switcher listed the six seeded users, and the application list loaded.
- As `rm-lana`: submitted 77 bps on `MA-1002`, got "Submitted" and a pending card. Approving her own request showed the service's 403. Revising to 40 bps replaced the card.
- As `rm-chloe`: withdrawing Lana's request showed the 403.
- As `reviewer-lex`: a decline with an empty note showed the 400; a decline with a note moved the request to DECLINED, where the card shows the note and only the History button.
- History listed SUBMITTED, REVISED, DECLINED with the advice of each revision and the note.
- A new request on the same application showed "Follows declined request", was approved, and "Show approved discount" then reported 33 bps.
- A discount of 0, a five-character reason and a submit by a reviewer each showed the service's refusal in the red banner.
- A reason containing `<b>` and `<img onerror=...>` was shown as text. No element was created and no script ran.

A too-short reason shows only "Validation failure": the service does not say which field failed.
