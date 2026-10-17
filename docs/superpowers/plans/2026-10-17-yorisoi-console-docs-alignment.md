# yorisoi-console Documentation Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to execute this plan task-by-task. The user prefers inline work without subagents.

**Goal:** Make console documentation discoverable and distinguish its implementation decisions from yorisoi ecosystem policy and contracts.

**Architecture:** Retain the established `adr/`, `design/`, `guide/`, and `operations/` categories and all console-local identifiers. Add a docs index and update cross-repository references to the canonical platform documents.

**Tech Stack:** Markdown, Git, PowerShell, Python standard library for temporary link validation.

**Spec:** `docs/superpowers/specs/2026-10-17-cross-repository-docs-alignment-design.md`

## Global Constraints

- Keep the console's established documentation categories and local IDs.
- Identify overlapping IDs by repository, such as `console ADR-001` and `platform ADR-CHANNEL-CURATION`.
- Use `yorisoi-platform` as the canonical source for ecosystem-wide policies, vocabulary, and contracts.
- Do not change console code, API formats, OpenAPI YAML, or agreed technical decisions.
- Verify variable system facts against code/configuration before changing them; date confirmed current-state facts.
- Do not test external URL availability; verify local Markdown paths and anchors.

---

## File Structure

- Create: `docs/README.md` — console documentation index.
- Review all 25 Markdown files under `docs/` and modify as needed:
  - `docs/architecture.md`
  - `docs/console-api.md`
  - `docs/data-model.md`
  - `docs/TODO.md`
  - `docs/adr/ADR-001-aws-architecture.md`
  - `docs/design/DES-001-publisher.md`
  - `docs/design/DES-002-operator-console.md`
  - `docs/design/DES-003-publisher-console.md`
  - `docs/design/DES-004-machine-review.md`
  - `docs/design/DES-005-review.md`
  - `docs/design/DES-006-transfer-notify.md`
  - `docs/design/DES-007-materials-review.md`
  - `docs/design/DES-008-materials-api.md`
  - `docs/design/DES-009-materials-publish.md`
  - `docs/design/DES-010-materials-screens.md`
  - `docs/design/DES-011-ecosystem.md`
  - `docs/guide/README.md`
  - `docs/guide/concepts.md`
  - `docs/guide/maintainer.md`
  - `docs/guide/operator.md`
  - `docs/guide/producer.md`
  - `docs/guide/publisher.md`
  - `docs/operations/cost.md`
  - `docs/operations/deploy.md`
  - `docs/operations/keys.md`
- Review and modify the repository root `README.md` where needed.

## Tasks

### Task 1: Review console documentation and add the docs index

**Files:**
- Create: `docs/README.md`
- Review: `README.md` and all 25 files under `docs/`

- [ ] Read the in-scope documents and classify each as a console decision/design, console API/data model, user guide, operations procedure, or ecosystem-level rule. Check current operational facts against code/configuration before changing them.
- [ ] Create `docs/README.md` with reader-oriented entries for the architecture, console ADR and DES records, console API/data model, guides, operations, and console TODO.
- [ ] In the index, explain that platform ADRs/contracts govern shared ecosystem behavior, while console documents explain the management system's implementation and operations.
- [ ] Review root `README.md` links and summary text; point platform contract references to canonical `docs/contracts/` paths and qualify colliding IDs.

**Verification:**
- Resolve every index link to a file or heading in the console repository.
- Confirm no API or infrastructure YAML is changed.
- Run `git diff --check`.

### Task 2: Update platform links and make identifiers unambiguous

**Files:**
- Review all 25 Markdown files under `docs/`.
- Modify old platform references in the files found by the repository-wide search, including `README.md`, `docs/architecture.md`, `docs/console-api.md`, `docs/data-model.md`, `docs/TODO.md`, `docs/adr/ADR-001-aws-architecture.md`, `docs/design/*.md`, `docs/guide/*.md`, and `docs/operations/*.md`.

- [ ] Search for all platform document URLs and legacy paths (`docs/adr/`, `docs/channel-list-api.md`, `docs/channel-package-format.md`, `docs/material-producer-api.md`, `docs/stakeholders.md`, and `docs/manual/`). Replace old destinations with canonical paths under `docs/decisions/`, `docs/contracts/`, `docs/overview/`, and `docs/apps/sanpoguide/manual/`.
- [ ] Update platform references to canonical document titles/IDs while preserving console `ADR-001`, `DES-001`–`DES-011`, console `API-001`, and console `DM-001`.
- [ ] Where both systems are discussed, explicitly label the ID with `platform` or `console`; do not rename either repository's identifiers.
- [ ] Recheck DES-011 and related ecosystem text to ensure platform policy is cited rather than restated as a separate console decision.
- [ ] Run a temporary Python standard-library checker over root `README.md` and `docs/**/*.md` for relative paths and heading fragments. For sibling platform links, resolve each canonical URL path against `C:\Dev\apps\yorisoi-platform`.
- [ ] Search again for old platform paths and unqualified overlapping IDs. Resolve stale links; preserve numeric IDs only where required for console-local traceability.
- [ ] Review `git diff --check` and `git diff --stat`; commit the repository changes as `docs: align yorisoi-console documentation`.
