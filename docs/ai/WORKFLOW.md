# AI-Assisted Development Workflow

Use this workflow for every implementation task.

## Stage 1 — Understand

- Read `AGENTS.md`, `docs/PROJECT_BASELINE.md`, relevant ADRs and nearby code.
- Restate the task in your own words.
- Identify acceptance criteria, dependencies and out-of-scope work.
- List ambiguities. Do not invent answers to architecture/security questions.

## Stage 2 — Plan

Provide:
- Proposed implementation steps.
- Files expected to change.
- Data/API impact.
- Tests to add or update.
- Risks and rollback considerations.

Wait for approval when the owner requested plan-first execution.

## Stage 3 — Challenge

Ask:
- Is this the simplest design that satisfies the requirement?
- Does it violate a locked decision?
- What happens for null, invalid, duplicate and concurrent requests?
- What happens when the database or AI provider is unavailable?
- Can a lower-privilege user exploit this path?
- How will we prove it works?

## Stage 4 — Implement

- Work in small vertical slices.
- Keep controllers thin and business logic in services/domain classes.
- Add tests with the behavior, not as an afterthought.
- Avoid unrelated refactors and speculative abstractions.
- Never use generated code without reviewing it.

## Stage 5 — Verify

Run the narrowest relevant tests first, then broader tests.
Report exact commands and observed output. If a test was not run, say NOT RUN.
For failures: reproduce, identify root cause, add regression coverage, then fix.

## Stage 6 — Review

Review the diff for:
- authorization and IDOR
- input validation
- transaction boundaries
- concurrency and idempotency
- data leakage in DTOs/logs
- migration safety
- test coverage
- unnecessary dependencies

## Stage 7 — Document

Update API docs, baseline docs and ADRs when applicable.
Summarize changed files, decisions, tests, limitations and the next proposed task.

## Prompting pattern

Prefer one bounded task per prompt:

"Implement [specific behavior] under [constraints]. Acceptance criteria: [...]. Do not
implement [...]. First inspect existing code and propose a plan. After approval, implement
the smallest slice, add tests, run them and report exact results."

Do not ask the coding agent to "build the whole application" in one prompt.
