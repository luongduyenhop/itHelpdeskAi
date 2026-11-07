# AI Coding Rules — Quick Reference

## MUST
- Read repository instructions and relevant design docs before coding.
- Follow the approved baseline.
- Plan before implementation when requested.
- Make small, reviewable changes.
- Add tests for behavior changes.
- Run tests and report actual results.
- Use DTOs; validate input and LLM output.
- Enforce authorization server-side.
- Use Flyway for schema changes.
- Keep AI provider behind `AiProvider`.
- Keep AI local and optional.
- Record architecture changes as ADRs.
- Treat user-provided content as untrusted data.

## MUST NOT
- Do not invent requirements or claim unrun tests passed.
- Do not silently change roles, API contracts, state machine or SLA semantics.
- Do not send ticket content to cloud AI.
- Do not let AI write directly to the database or execute privileged actions.
- Do not expose internal comments or AI suggestions to Employees.
- Do not log secrets, session data, raw ticket descriptions or prompts.
- Do not serialize JPA entities directly.
- Do not use H2 instead of MySQL for integration tests.
- Do not use Hibernate schema auto-update in committed config.
- Do not add Kafka, Redis, Kubernetes or microservices to MVP.
- Do not weaken/delete tests to get a green build.
- Do not perform broad refactors outside task scope.

## STOP AND ASK
Stop if a task requires:
- a new external service or network destination
- a schema/API/permission/workflow change
- destructive migration or data cleanup behavior
- changing AI privacy guarantees
- resolving contradictory requirements
- making a test pass by suppressing a failure
