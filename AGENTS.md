# Lirix — Agent Engineering Contract

> Scope: Repository-wide instructions for Codex, Antigravity and compatible coding agents.
>
> Project: Lirix  
> Process authority: `.sdd/WORKFLOW.md`  
> Development model: Risk-based planning, controlled autonomy, evidence-based verification.

## 1. Purpose and Core Principles

Agents are responsible for delivering correct, maintainable and verified software changes while preserving the integrity of the existing product.

Follow these principles:

1. **Understand before modifying.** Inspect existing behavior, implementation, architecture and affected contracts before editing.
2. **Preserve working behavior.** Do not introduce unrelated behavioral changes or regressions.
3. **Prefer simplicity.** Reuse existing components and conventions. Avoid unnecessary abstractions, frameworks and duplicate implementations.
4. **Respect architectural boundaries.** Maintain established responsibilities between UI, application logic, persistence, platform integrations and shared components.
5. **Verify outcomes, not appearances.** Successful compilation, green tests and plausible code are not sufficient evidence by themselves.
6. **Protect user data.** Prefer reversible changes and explicitly handle migrations, persistence failures and concurrency.
7. **Keep scope controlled.** Do not silently expand tasks or perform unrelated refactoring.
8. **Report truthfully.** Distinguish implemented, verified, unverified and incomplete work.

These rules apply regardless of which agent, model or teamwork configuration performs the task.

## 2. Project Knowledge and Instruction Routing

### 2.1 Sources of Truth

The project uses Specification-Driven Development (SDD).

The existing `.sdd/` structure is the primary location for development plans, specifications, architectural decisions, contracts and implementation records.

Relevant locations:

| Location | Responsibility |
|---|---|
| `.sdd/WORKFLOW.md` | Authoritative development and planning workflow |
| `.sdd/board.md` | Task status and ownership |
| `.sdd/decisions.md` | Accepted architectural and technical decisions |
| `.sdd/intake/` | Task intake and plans |
| `.sdd/specs/` | Subsystem specifications |
| `.sdd/contracts/` | Cross-component contracts |
| `.sdd/architecture/` | Architectural documentation |
| `.sdd/tasks/` | Independently assignable implementation tasks |
| `.sdd/backlog/` | Deferred work |
| `.sdd/reports/` | Verification and implementation reports |
| `.sdd/pr/` | Change review materials |
| `.sdd/releases/` | Release-related artifacts |

Existing architecture documents in the `.sdd/` root must also be considered when relevant.

Do not create competing documentation structures such as `docs/plans/` or independent architectural decision logs.

### 2.2 Context Discovery

Before implementing a change:

1. Locate and read applicable instructions.
2. Determine the affected subsystem and existing behavior.
3. Inspect relevant source code and tests.
4. Read relevant SDD specifications and architectural documents.
5. Check existing contracts and accepted decisions.
6. Identify compatibility requirements and affected consumers.
7. Determine the appropriate workflow level.

Read documents selectively according to the task.

Do not load unrelated specifications or large document collections merely because they exist.

Do not assume that documentation is correct solely because it is present. When documentation and implementation disagree, investigate and report the discrepancy.

### 2.3 Instruction Conflicts

Respect the applicable instruction hierarchy enforced by the execution environment.

Within the project's documentation:

- `AGENTS.md` defines repository-wide engineering rules.
- `.sdd/WORKFLOW.md` defines the development process.
- Accepted SDD decisions and contracts define established architectural commitments.
- Subsystem specifications define feature-specific requirements.
- Task prompts define the requested change and acceptance criteria.

More specific instructions may refine general rules.

Do not silently override established invariants, contracts or user-approved requirements.

If requirements conflict materially, identify the conflict and obtain clarification or approval before implementing the conflicting change.

Do not modify project instructions to justify a failing implementation.

## 3. Development Workflow

Follow `.sdd/WORKFLOW.md`.

Do not introduce an independent planning system.

### 3.1 Workflow Classification

Classify each task before implementation.

**Level 0 — Small Change**

Local, reversible changes with clearly understood behavior and limited impact.

Requirements:

- Understand the intended outcome.
- Make the smallest appropriate change.
- Run relevant verification.
- Review the diff.

Separate specifications and task cards are not required.

**Level 1 — Standard Task**

Changes across multiple files within an architectural area, or changes to user-visible behavior without substantial cross-zone or data-integrity risks.

Requirements:

- Follow the short planning process in `.sdd/WORKFLOW.md`.
- Use `.sdd/intake/<slug>.md` as specified by the workflow.
- Define observable acceptance criteria.
- Identify affected components.
- Validate relevant behavior.
- Review the plan when required by the workflow.

**Level 2 — Major or Risky Change**

Use Level 2 for changes involving:

- Database schemas, migrations or stored-data compatibility.
- Public APIs, persistence formats or cross-zone contracts.
- Multiple architectural areas.
- Android/Desktop behavior coordination.
- Data loss, privacy or security risks.
- Concurrency, synchronization or lifecycle risks.
- Device-dependent or externally dependent behavior.
- Complex architectural changes.

Requirements:

- Create or update the appropriate subsystem specification.
- Document affected cross-zone contracts.
- Define acceptance criteria and verification strategy.
- Record independent or parallel tasks when applicable.
- Submit the plan and critical decisions for review before implementation.
- Perform independent review before completion.

An explicitly approved specification and implementation plan may satisfy the planning review requirement. Do not require redundant approval for every implementation detail.

If classification is uncertain, temporarily select the higher-risk level until the uncertainty is resolved.

### 3.2 Implementation Discipline

- Implement changes in dependency order.
- Keep changes focused on the approved scope.
- Prefer incremental, verifiable steps.
- Preserve unrelated user and agent changes.
- Do not overwrite work without understanding its origin.
- Keep shared interfaces consistent with their consumers.
- Update relevant SDD artifacts when requirements or contracts materially change.
- Record deferred issues in the existing intake or backlog process.

Do not create a separate task card for every function or trivial implementation step.

### 3.3 Scope Changes

If implementation reveals a need to change approved acceptance criteria, persistence schemas, major contracts or task boundaries:

1. Stop the affected work.
2. Explain the discovered issue.
3. Propose the smallest viable adjustment.
4. Update the relevant plan or specification.
5. Obtain the required approval.
6. Continue within the updated scope.

Minor internal implementation refinements do not require restarting the approval cycle.

## 4. Controlled Autonomy and Permissions

Agents should work autonomously within agreed boundaries without repeatedly requesting permission for routine engineering decisions.

### 4.1 Autonomous Actions

Agents may independently:

- Inspect source code and documentation.
- Investigate bugs.
- Implement approved functionality.
- Refactor code within the task scope.
- Add or update relevant tests.
- Run local builds and checks.
- Improve error handling.
- Add small internal abstractions when justified.
- Fix directly related defects discovered during implementation.
- Update task-specific documentation.
- Delegate bounded research and review tasks when supported.

Autonomy does not remove the obligation to follow the SDD workflow.

### 4.2 Changes Requiring Approval

Obtain approval before making material changes not already authorized by an approved plan, including:

- Major architectural redesigns.
- Replacement of core frameworks or persistence technologies.
- Breaking public or cross-component contracts.
- Incompatible storage changes.
- Destructive or non-reversible migrations.
- Broad dependency replacements.
- Significant changes to product behavior or UX.
- Large unrelated refactors.
- Removal of established functionality.
- Material relaxation of quality or security requirements.

Do not request separate approval for a change already explicitly included in an approved specification unless new material risks are discovered.

### 4.3 Restricted Operations

Never independently:

- Delete valuable user data.
- Execute destructive production operations.
- Force-push or rewrite shared Git history.
- Remove unrelated files or changes.
- Expose or commit secrets.
- Bypass security protections.
- Deploy to production without authorization.
- Silently disable required tests or quality gates.

If a destructive action is necessary, explain its impact and recovery strategy before requesting authorization.

## 5. Architecture and Implementation Quality

### 5.1 Existing Architecture First

Before introducing a component, determine whether an existing component already fulfills the responsibility.

Prefer extending a suitable existing abstraction over building a competing implementation.

Do not create duplicate repositories, parsers, state-management pipelines or service layers merely to avoid understanding the existing design.

Preserve existing dependency boundaries.

If a boundary must change, document why and identify affected consumers.

### 5.2 Correctness

Implementation must account for:

- Valid inputs.
- Missing or empty inputs.
- Invalid inputs.
- Boundary conditions.
- Failure and cancellation.
- Relevant concurrency.
- Data integrity.
- Compatibility with existing consumers.

Use explicit error handling when failures have different meanings.

Do not silently convert all failures to successful empty results.

### 5.3 Resource Management

- Keep expensive operations off latency-sensitive UI threads.
- Use cancellation-aware asynchronous operations.
- Manage resources according to their lifecycle.
- Avoid unbounded retries, polling, allocations and concurrency.
- Release temporary resources reliably.
- Use appropriate timeouts for external operations.

### 5.4 Android, Desktop and Shared Code

Inspect the affected modules before deciding where implementation belongs.

- Keep platform-independent logic reusable where the architecture permits.
- Keep platform-specific lifecycle and UI operations within appropriate platform boundaries.
- Avoid accidental inconsistencies between Android and Desktop when a feature contract covers both.
- Test platform-dependent behavior in an appropriate execution environment.
- Do not infer Android runtime behavior solely from JVM unit tests.

### 5.5 Persistence and Data Integrity

Before changing stored data or persistence behavior:

- Identify the source of truth.
- Inspect existing repository and transaction semantics.
- Preserve stable entity identities.
- Understand migration and compatibility requirements.
- Consider concurrent writers and stale asynchronous results.
- Avoid duplicate writes and unintended overwrites.
- Handle failed persistence explicitly.

A successful UI action must not be reported before its required durable operation succeeds.

## 6. Verification Integrity — Anti-Green-Mirage

**Green tests do not prove correctness when the tests are tautological, incomplete or disconnected from production behavior.**

Every applicable test must provide meaningful evidence about the component's actual contract.

### 6.1 Independent Expected Results

- Derive expected behavior from specifications and requirements.
- Do not derive expected results by executing the production algorithm under test.
- Do not reproduce the implementation algorithm inside test assertions.
- Do not use production output as the sole test oracle.
- Avoid test fixtures designed only around hardcoded implementation shortcuts.

### 6.2 Mocking Policy

Mocks and fakes are allowed for external dependencies and nondeterministic boundaries.

Examples include:

- Network transport.
- External APIs.
- Time providers.
- Platform services.
- Database boundaries in appropriately scoped unit tests.

Do not mock the algorithm whose correctness the test claims to verify.

For integration tests, exercise the real components appropriate to the integration boundary.

Do not treat verifying a mock invocation as proof of a complete user-visible outcome.

### 6.3 Coverage Requirements

Choose tests according to the actual behavior being changed.

Include, where relevant:

- Positive cases.
- Negative cases.
- Boundary conditions.
- Empty and malformed inputs.
- Error propagation.
- Cancellation.
- Compatibility.
- State transitions.
- Concurrency and race conditions.
- Regression of previously working behavior.

When relevant to the algorithm, include out-of-vocabulary, unknown or previously unseen inputs.

Do not impose unrelated checks on components that cannot meaningfully satisfy them.

### 6.4 Golden Fixtures

For parsers, text processing, media processing and other data transformation pipelines, prefer realistic golden fixtures.

Golden fixtures must:

- Exercise the real production transformation.
- Contain meaningful structural complexity.
- Have independently defined expected results.
- Preserve relevant edge cases.
- Verify output integrity, not merely nonempty output.

Use authentic formats or representative structures.

Use legally permissible or synthetic content where copyrighted material would otherwise be required.

Do not require arbitrary minimum text lengths or exact completeness scores unless the component's contract explicitly requires them.

### 6.5 Outcome-Based Verification

Test observable results rather than only internal calls.

Examples:

**Persistence**

Verify committed state and relevant consumer observations, not merely repository method invocation.

**Android UI**

Verify rendered state and relevant user interactions, not merely internal state mutation.

**Parsing**

Verify extracted content, structure, order and absence of unwanted material, not merely selector matching.

**Media Processing**

Verify required semantic or structural properties of the output, not merely file existence or nonzero length.

**Context-Sensitive Logic**

Verify that applicable global and local context follow the documented precedence rules.

### 6.6 Anti-Gaming Rules

Never:

- Weaken assertions solely to make tests pass.
- Delete failing tests without justification.
- Replace meaningful tests with trivial mocks.
- Hardcode fixture-specific behavior into production code.
- Catch and discard failures to create artificial success.
- Claim unexecuted checks passed.
- Modify expected fixtures merely to match incorrect output.

If a test is incorrect, explain the contract mismatch and correct it with supporting evidence.

### 6.7 Verification Boundaries

A passing JVM unit test does not establish:

- Real Android WebView behavior.
- Device lifecycle correctness.
- UI rendering correctness.
- Hardware integration correctness.
- External website availability.
- Production deployment correctness.

Record unperformed physical or environmental checks explicitly.

## 7. Lirix Product Invariants

The following invariants apply when changing the corresponding functionality.

### 7.1 User Data and Manual Overrides

Explicit user actions and manually provided content must not be silently overwritten by lower-priority automatic processes.

Changes affecting such precedence must be intentional, documented and tested.

### 7.2 Track Identity

Track-related persistence operations must target the correct stable track identity.

Asynchronous operations must not accidentally redirect results to a different track because playback state changed during execution.

### 7.3 Manual Lyrics Binding

The existing direct track-binding workflow must remain independent of autonomous site-rule synthesis unless explicitly changed through an approved product decision.

Selecting content and binding it to a track must not require a successful automatic search or site-rule validation.

### 7.4 Automatic Lyrics Retrieval

Automatic retrieval must not silently treat an uncertain song match as a confirmed match.

Preserve existing user content when automatic retrieval fails.

### 7.5 Shared and Platform-Specific Behavior

When a behavior is intentionally shared between platforms, preserve its documented contract.

Do not introduce accidental behavioral divergence through platform-specific changes.

Additional subsystem invariants belong in their relevant SDD specifications and contracts rather than being duplicated throughout this file.

## 8. Multi-Agent and Teamwork Coordination

Subagents and parallel workers are permitted when useful and supported.

Use them for:

- Independent research.
- Clearly separable implementation tasks.
- Contract review.
- Test development.
- Independent QA.
- Isolated investigations.

Do not use multiple agents solely to increase agent count.

### 8.1 Ownership

Before parallel implementation:

1. Identify shared contracts.
2. Assign clear component or file ownership.
3. Define dependencies between tasks.
4. Agree on integration boundaries.
5. Establish acceptance criteria.

Avoid unsynchronized concurrent editing of the same files.

### 8.2 Team Lead Responsibilities

The coordinating agent must:

- Preserve the approved scope.
- Ensure agents understand shared contracts.
- Coordinate integration order.
- Resolve conflicting assumptions.
- Review combined changes.
- Verify the final integrated behavior.
- Consolidate actual verification evidence.

The Team Lead is accountable for integration, not merely collecting subagent summaries.

### 8.3 Independent QA

For Level 2 tasks, obtain independent review as required by `.sdd/WORKFLOW.md`.

QA should evaluate requirements and observable behavior independently of the implementation author's assumptions.

A subagent reporting completion does not establish that its work is correct or integrated.

### 8.4 Delegation Boundaries

Delegation does not grant additional permissions.

Subagents inherit the applicable project constraints and approved task scope.

Do not silently initiate a major parallel implementation when the user has requested planning or review only.

## 9. Security and External Integrations

Apply security controls appropriate to the affected components.

- Never expose credentials, tokens or private user data.
- Validate untrusted inputs.
- Treat externally retrieved HTML and documents as untrusted.
- Validate network destinations and redirect behavior.
- Use bounded external requests.
- Handle rate limits and access failures.
- Avoid insecure WebView settings.
- Do not expose privileged native interfaces to arbitrary websites.
- Do not bypass authentication, CAPTCHA or access restrictions.
- Do not silently introduce new data collection or external transmission.

Security-sensitive changes require the risk-based workflow and applicable review.

## 10. Definition of Done

A task is complete only when:

1. The requested behavior is implemented within the approved scope.
2. Applicable acceptance criteria are satisfied.
3. Relevant checks have been executed and their outcomes recorded.
4. The diff has been reviewed for unrelated changes.
5. Required compatibility and regression checks have been performed.
6. Applicable SDD artifacts have been updated.
7. Known limitations and unverified behaviors are explicitly reported.
8. Required independent review has been completed.

A task with mandatory unverified acceptance criteria must not be presented as fully verified.

### 10.1 Verification Statuses

Use clear statuses:

- `PASS` — Executed and passed.
- `FAIL` — Executed and failed.
- `NOT RUN` — Not executed.
- `BLOCKED` — Could not execute because of a dependency or environment.
- `NOT APPLICABLE` — Does not apply to the change.

Do not interpret missing verification evidence as success.

### 10.2 Final Report

For substantial tasks, provide:

**Summary**
- Implemented changes.
- Resulting behavior.

**Scope**
- Main files and components affected.
- Relevant contracts or migrations.

**Verification**
- Exact commands executed.
- Results and failure details.
- Manual or device verification performed.

**Remaining Issues**
- Known limitations.
- Unverified requirements.
- Deferred work.

**SDD Status**
- Tasks and plans updated.
- Decisions recorded where relevant.

Keep reports concise for small changes and detailed for high-risk tasks.

## 11. Maintaining This Contract

This file defines stable, repository-wide engineering behavior.

Do not turn it into a changelog, detailed feature specification or task backlog.

Place detailed knowledge in existing SDD locations.

Prefer linking to an authoritative document over duplicating its contents.

Update this contract only when project-wide agent behavior should change.

Do not silently relax quality requirements, architectural safeguards or approval boundaries to complete an individual task.

When proposing changes to this file, explain:

- The problem being addressed.
- The proposed rule.
- Its scope.
- Its effect on existing workflows.
- Any conflict with current instructions.

---

**Primary rule:** Deliver correct, maintainable and verified behavior. Respect the approved scope, preserve existing guarantees, and never substitute apparent success for evidence.