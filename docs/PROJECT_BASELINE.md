# AI Helpdesk API — Project Baseline v2.1

Status: **Approved baseline**  
Architecture: **Modular monolith**  
Scope: **MVP**

## Product goal

An internal IT Helpdesk REST API. Employees submit and track tickets. IT Support processes
assigned tickets. Admin manages users, configuration and assignment. AI runs locally to
suggest ticket classification and priority; humans approve suggestions.

## Actors and roles

- `EMPLOYEE`: register as pending, create and view own tickets, edit own ticket while OPEN,
  add public comments, cancel/reopen/close own ticket where workflow permits, view own notifications.
- `IT_SUPPORT`: view assigned tickets and unassigned classification queue, start/process tickets,
  comment publicly/internally, change category/priority with reason, review AI suggestions,
  request draft replies.
- `ADMIN`: manage users, allowed domains, categories, SLA policies, business hours and holidays;
  assign/reassign tickets; perform administrative actions and review queues.

Registration always assigns `EMPLOYEE`; users cannot choose their own role. Admin approval
activates accounts. Only active users can authenticate.

## Ticket status model

Statuses:
`OPEN`, `IN_PROGRESS`, `WAITING_FOR_USER`, `RESOLVED`, `CLOSED`, `CANCELLED`.

Allowed transitions:
- `OPEN -> IN_PROGRESS`
- `OPEN -> CANCELLED`
- `IN_PROGRESS -> WAITING_FOR_USER`
- `WAITING_FOR_USER -> IN_PROGRESS`
- `IN_PROGRESS -> RESOLVED`
- `RESOLVED -> IN_PROGRESS` (reopen)
- `RESOLVED -> CLOSED`

`CLOSED` and `CANCELLED` are terminal. An assignee does not change `OPEN`; IT must explicitly
start work. A transition must be validated in the backend and written to status history.

Additional workflow rules:
- Resolving requires a resolution note.
- Moving to `WAITING_FOR_USER` requires a public comment.
- Employee public comment while `WAITING_FOR_USER` returns ticket to `IN_PROGRESS`.
- Employee cannot comment on `CLOSED` or `CANCELLED`.
- Reassignment does not change status and is recorded as an event.
- Employee may edit own title/description only while status is `OPEN`. Editing records old
  values, marks existing pending AI suggestion `STALE` and schedules AI re-analysis.
- Employee cannot set priority.

## Priority and SLA

Priorities: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`.

Working-time targets:
- CRITICAL: 1 working hour
- HIGH: 4 working hours
- MEDIUM: 8 working hours
- LOW: 24 working hours

Business calendar:
- Time zone: `Asia/Ho_Chi_Minh`
- Monday–Friday, 08:00–17:30
- Admin-managed holidays are excluded.
- Outside business hours, SLA begins at the next business opening.
- SLA pauses in `WAITING_FOR_USER`.
- SLA stops at `RESOLVED`; reopening resumes remaining time.
- On priority escalation, recalculate from `created_at`, accounting for paused time.
- Priority downgrade must not extend an existing deadline.
- Every priority change requires a reason and an event record.
- Priority defaults to `MEDIUM` while `priority_confirmed = false`; IT/Admin confirms
  classification. The ticket remains in the unclassified queue until confirmed.

## Assignment

Automatic assignment chooses an active IT Support user with the fewest open tickets.
Open statuses for load are `OPEN`, `IN_PROGRESS`, `WAITING_FOR_USER`. Ties are broken by
oldest `last_assigned_at`. Assignment and load selection must be protected against races
within a transaction. Admin can manually assign/reassign. IT cannot self-claim in MVP.
If no eligible IT user exists, assignee remains null and Admin is notified.

## AI behavior and privacy

- Local Ollama via Spring AI; no external model provider or cloud fallback.
- AI starts asynchronously after ticket creation commits.
- Ticket creation must not wait for AI and must succeed when AI is unavailable.
- AI only creates suggestions; Java services validate and apply approved changes.
- Retry maximum: 3 attempts with bounded backoff; then `ai_status = FAILED`.
- AI suggestion statuses: `PENDING`, `ACCEPTED`, `REJECTED`, `STALE`.
- Ticket AI statuses: `PENDING`, `DONE`, `FAILED`, `SKIPPED`.
- Validate active category, enum priority and confidence range [0, 1].
- IT/Admin only can view/review suggestions. Employee responses never expose them.
- Draft reply is generated only on explicit IT request and is never auto-posted.
- Send only title, description and valid category choices when needed. Avoid user identifiers
  and internal notes. Do not log raw ticket content or prompts.
- `AiProvider` interface isolates Spring AI/Ollama implementation. Use a mock provider in tests.

## Notifications

In-app notifications only for MVP. Warn at 80% of SLA and notify on breach. Each milestone
is sent once. Recipients are assignee and all Admin users; if unassigned, Admin only.
Notification content contains ticket code and title, not ticket description.

## Authentication and account lifecycle

- Spring Security server-side session with Spring Session JDBC.
- CSRF enabled for cookie-authenticated write requests.
- Session fixation protection; rotate session ID after login.
- Idle timeout: 30 minutes.
- Disabling a user invalidates their active sessions.
- Registration allowed only for Admin-approved email domains; account is `PENDING` until
  Admin approval. Admin-created users are also supported.
- Secure cookies in deployment: HttpOnly, Secure, appropriate SameSite.

## Retention and audit

- Ticket soft delete by Admin using `deleted_at`.
- Permanent cleanup after 90 days only for `CANCELLED` or soft-deleted tickets.
- Do not cleanup `CLOSED` tickets in MVP.
- Cleanup writes a separate audit record with ticket identifier, actor, timestamp, reason
  and counts of removed child records; never preserve ticket description/content in audit.

## Technical constraints

- Java 21, Spring Boot, Maven, MySQL, Flyway.
- MySQL `utf8mb4`.
- Modular monolith, DTOs, service-layer business logic, constructor injection.
- MySQL Testcontainers for database integration tests; no H2 substitute.
- Docker Compose, OpenAPI, GitHub Actions.
- One application instance in MVP.
- No Kafka, Kubernetes, Redis, microservices or distributed scheduler lock in MVP.
