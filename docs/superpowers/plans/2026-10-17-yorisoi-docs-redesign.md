# yorisoi Documentation Redesign Implementation Plan

> The user chose in-place execution without subagents. Work through the tasks sequentially in the current checkout; use checkbox (`- [x]`) syntax for tracking.
>
> Tasks 3–6 share migrated paths and cross-references, so commit them together after the final link audit.

**Goal:** Reorganize every Markdown document in `yorisoi-platform/docs/` so ecosystem-wide rules, technical contracts, policies, and SanpoGuide-specific operating instructions are clearly separated and consistent.

**Architecture:** Create reader-oriented overview and vocabulary pages, move decision records and contracts into canonical topic-based locations with semantic IDs, and isolate SanpoGuide manuals under an app-specific subtree. Keep old Markdown paths as concise compatibility pages, keep machine-readable OpenAPI assets at their current path unless their consumers are verified and migrated, and update all in-scope cross-references.

**Tech Stack:** Markdown, Git, PowerShell, Python standard library for a temporary local-link validation script, existing Redocly CLI configuration for the OpenAPI asset.

**Spec:** `docs/superpowers/specs/2026-10-17-yorisoi-docs-information-architecture-design.md`

## Global Constraints

- Review every Markdown document in `yorisoi-platform/docs/`.
- Use the approved document categories: `overview/`, `decisions/`, `contracts/`, `policies/`, `apps/sanpoguide/`, and `work/`.
- Use semantic canonical IDs for ADR/API documents and include a complete old-ID-to-new-ID mapping.
- Keep old Markdown paths as compatibility pages that link to the canonical page.
- Do not edit `yorisoi-sanpoguide`, `yorisoi-console`, or `yorisoi-tutorial`.
- Do not change code, API formats, or agreed technical decisions.
- Keep OpenAPI validation assets and their existing command working.
- Use ecosystem-neutral wording for shared documents and label SanpoGuide-only facts and procedures explicitly.
- Do not turn unapproved stakeholder proposals or unresolved decisions into approved policy.
- Verify Markdown paths and fragments, document migration mappings, and `git diff --check`.

---

## File Structure

### Canonical pages to create

- `docs/overview/yorisoi-ecosystem.md` — ecosystem scope, layers, connected repositories, and reader navigation.
- `docs/overview/roles-and-vocabulary.md` — canonical actors, ecosystem concepts, and shared vocabulary.
- `docs/overview/stakeholder-needs.md` — preserve the needs/proposals from the current stakeholder document, with approved decisions distinguished from unapproved proposals.
- `docs/decisions/README.md` — ADR catalog with canonical IDs and old ID/path mappings.
- `docs/decisions/ADR-CHANNEL-CURATION.md` — current ADR-001 content.
- `docs/decisions/ADR-MATERIAL-PRODUCERS.md` — current ADR-002 content.
- `docs/decisions/ADR-ECOSYSTEM-PLATFORM.md` — current ADR-003 content.
- `docs/decisions/ADR-AI-ROLES.md` — current ADR-004 content.
- `docs/contracts/API-SECRET-MESSAGES.md` — current API-001 content.
- `docs/contracts/API-CHANNEL-LIST.md` — current API-002 content.
- `docs/contracts/API-CHANNEL-PACKAGE.md` — current API-003 content.
- `docs/contracts/API-MATERIAL-PRODUCER.md` — current API-004 content.
- `docs/contracts/README.md` — contract catalog, canonical IDs, and OpenAPI asset location.
- `docs/policies/channel-review.md` — current channel review policy, with ecosystem-wide baseline and SanpoGuide-specific criteria clearly scoped.
- `docs/apps/sanpoguide/manual/README.md` — SanpoGuide manual entry and scope.
- `docs/apps/sanpoguide/manual/walker.md` — SanpoGuide walker guide.
- `docs/apps/sanpoguide/manual/publisher.md` — SanpoGuide publisher guide.
- `docs/apps/sanpoguide/manual/producer.md` — SanpoGuide producer guide.
- `docs/apps/sanpoguide/manual/operator.md` — SanpoGuide operator guide.
- `docs/work/ecosystem-todo.md` — current ecosystem TODO content.

### Existing paths to update or retain as compatibility pages

- Update `docs/README.md` as the root landing page for all canonical documentation categories.
- Convert `docs/stakeholders.md`, `docs/TODO.md`, `docs/channel-review-policy.md`, `docs/secret-messages-api.md`, `docs/channel-list-api.md`, `docs/channel-package-format.md`, and `docs/material-producer-api.md` into short compatibility pages.
- Convert `docs/adr/ADR-001-third-party-channel-curation.md`, `docs/adr/ADR-002-material-producers.md`, `docs/adr/ADR-003-companion-app-platform.md`, and `docs/adr/ADR-004-roles-of-ai-and-stakeholders.md` into compatibility pages.
- Convert `docs/manual/README.md`, `docs/manual/walker.md`, `docs/manual/publisher.md`, `docs/manual/producer.md`, and `docs/manual/operator.md` into compatibility pages.
- Keep `docs/api/sanpo-messages.openapi.yaml` and `docs/api/redocly.yaml` at their current paths unless repository-wide searches prove every consumer can be migrated safely.
- Keep `docs/api/README.md` at its existing path as an OpenAPI usage page that links to the canonical contract catalog.
- Keep the approved design spec and this plan under `docs/superpowers/`.

### Canonical ID migration table

| Old ID | Canonical ID | Canonical path |
|---|---|---|
| ADR-001 | `ADR-CHANNEL-CURATION` | `docs/decisions/ADR-CHANNEL-CURATION.md` |
| ADR-002 | `ADR-MATERIAL-PRODUCERS` | `docs/decisions/ADR-MATERIAL-PRODUCERS.md` |
| ADR-003 | `ADR-ECOSYSTEM-PLATFORM` | `docs/decisions/ADR-ECOSYSTEM-PLATFORM.md` |
| ADR-004 | `ADR-AI-ROLES` | `docs/decisions/ADR-AI-ROLES.md` |
| API-001 | `API-SECRET-MESSAGES` | `docs/contracts/API-SECRET-MESSAGES.md` |
| API-002 | `API-CHANNEL-LIST` | `docs/contracts/API-CHANNEL-LIST.md` |
| API-003 | `API-CHANNEL-PACKAGE` | `docs/contracts/API-CHANNEL-PACKAGE.md` |
| API-004 | `API-MATERIAL-PRODUCER` | `docs/contracts/API-MATERIAL-PRODUCER.md` |

## Tasks

### Task 1: Create the ecosystem overview and canonical vocabulary

**Files:**
- Create: `docs/overview/yorisoi-ecosystem.md`
- Create: `docs/overview/roles-and-vocabulary.md`
- Create: `docs/overview/stakeholder-needs.md`
- Modify: `docs/README.md`
- Modify: `docs/stakeholders.md` (compatibility page)

- [x] Read the full current `docs/stakeholders.md` and separate its material into (a) canonical actor names/roles, (b) channel concepts and glossary terms, and (c) stakeholder needs and proposed responses.
- [x] Create `docs/overview/yorisoi-ecosystem.md` explaining yorisoi as the ecosystem, yorisoi-platform as shared libraries, SanpoGuide as the first app, yorisoi-console as the management system, and where app/tutorial documentation lives.
- [x] Create `docs/overview/roles-and-vocabulary.md` with shared actor names, code identifiers, the five responsibility layers from ADR-004, and terminology that is valid across apps.
- [x] Create `docs/overview/stakeholder-needs.md` by preserving each current concern and response from `stakeholders.md`; label decisions already approved by ADR/API and leave other proposals explicitly unapproved.
- [x] Rewrite `docs/README.md` as a reader-oriented catalog linking to the overview, decisions, contracts, policies, SanpoGuide manual, work list, and OpenAPI assets.
- [x] Replace `docs/stakeholders.md` with a compatibility page that identifies the former document and links to the canonical overview pages.
- [x] Check the three new overview pages for conflicting definitions of yorisoi, yorisoi-platform, app, console, app user, and SanpoGuide.

**Verification:**
- Confirm every section in the former `stakeholders.md` is either represented in one canonical overview page or explicitly excluded with a reason.
- Run `git diff --check`.
- Commit this batch as `docs: establish yorisoi documentation overview`, including the repository's required `Co-authored-by` trailer.

### Task 2: Migrate and normalize the ADR decision records

**Files:**
- Create: `docs/decisions/ADR-CHANNEL-CURATION.md`
- Create: `docs/decisions/ADR-MATERIAL-PRODUCERS.md`
- Create: `docs/decisions/ADR-ECOSYSTEM-PLATFORM.md`
- Create: `docs/decisions/ADR-AI-ROLES.md`
- Modify: `docs/adr/ADR-001-third-party-channel-curation.md` (compatibility page)
- Modify: `docs/adr/ADR-002-material-producers.md` (compatibility page)
- Modify: `docs/adr/ADR-003-companion-app-platform.md` (compatibility page)
- Modify: `docs/adr/ADR-004-roles-of-ai-and-stakeholders.md` (compatibility page)
- Modify: `docs/README.md`
- Modify: overview and any other in-scope pages that cite ADR IDs

- [x] Copy each ADR to its canonical path without changing its approved decisions, decision tables, traceability rows, or unresolved items.
- [x] Replace each document's canonical ID with the semantic ID from the migration table and retain the former numeric ID as a legacy identifier in its document metadata.
- [x] Add scope notes where an ADR describes SanpoGuide as the current implementation but its decision applies to the ecosystem; do not rewrite implementation examples as universal behavior.
- [x] Create one compatibility page at each old `docs/adr/` path with the old ID, new ID, canonical title, and relative link.
- [x] Search all Markdown under `docs/` for `ADR-001`, `ADR-002`, `ADR-003`, and `ADR-004`; update normative cross-references to canonical IDs and keep old identifiers only where they are explicitly described as legacy mappings.
- [x] Update the decisions catalog in `docs/README.md` to use semantic IDs and canonical paths.
- [x] Check the four canonical ADRs against each other for changed meanings, unresolved cross-document contradictions, and SanpoGuide-only assumptions; record unresolved issues rather than choosing a side.
- [x] Create `docs/decisions/README.md` with each ADR title, canonical ID/path, old numeric ID, and old path; link it from `docs/README.md`.

**Verification:**
- Compare every canonical ADR against its old version to confirm that all approved decisions and open items remain present.
- Confirm the decisions catalog and all four old paths map to the correct new files.
- Run `git diff --check`.
- Commit this batch as `docs: reorganize ecosystem decision records`, including the repository's required `Co-authored-by` trailer.

### Task 3: Migrate the API contracts and preserve the OpenAPI workflow

**Files:**
- Create: `docs/contracts/API-SECRET-MESSAGES.md`
- Create: `docs/contracts/API-CHANNEL-LIST.md`
- Create: `docs/contracts/API-CHANNEL-PACKAGE.md`
- Create: `docs/contracts/API-MATERIAL-PRODUCER.md`
- Create: `docs/contracts/README.md`
- Modify: `docs/secret-messages-api.md` (compatibility page)
- Modify: `docs/channel-list-api.md` (compatibility page)
- Modify: `docs/channel-package-format.md` (compatibility page)
- Modify: `docs/material-producer-api.md` (compatibility page)
- Modify: `docs/api/README.md`
- Keep: `docs/api/sanpo-messages.openapi.yaml`
- Keep: `docs/api/redocly.yaml`
- Modify: every in-scope ADR, policy, manual, and overview page that links to an API contract

- [x] Copy each API contract to its canonical path, retaining normative field names, constraints, error behavior, examples, and validation criteria.
- [x] Replace the canonical ID with the semantic ID from the migration table and record the former numeric API ID as a legacy identifier.
- [x] Review each contract's target-system metadata and introduction against ADR-003; generalize only what is actually shared, and label SanpoGuide as the first/current app where behavior is app-specific.
- [x] Create compatibility pages at the four old API Markdown paths with old ID, new ID, canonical title, and relative link.
- [x] Create `docs/contracts/README.md` with each API title, canonical ID/path, old numeric ID, old path, and links to all four contracts plus the OpenAPI asset location.
- [x] Update `docs/api/README.md` so the existing lint command remains `npx @redocly/cli lint sanpo-messages.openapi.yaml` when run from `docs/api`, and link to `../contracts/README.md`.
- [x] Search all Markdown in `docs/` for the old API IDs and old contract paths; update active cross-references to canonical IDs and paths while retaining legacy mappings.
- [x] Confirm the OpenAPI YAML's references and Redocly configuration still resolve without moving either machine-readable file.

**Verification:**
- Run `npx @redocly/cli lint sanpo-messages.openapi.yaml` from `docs/api` only if the CLI is already available; if it is unavailable, report that limitation without installing dependencies.
- Compare the old and canonical contract text for loss or accidental changes to normative requirements.
- Run `git diff --check`.
- Include this batch in the final integrated documentation commit after Tasks 4–6 pass verification.

### Task 4: Separate review policy and ecosystem work tracking

**Files:**
- Create: `docs/policies/channel-review.md`
- Create: `docs/work/ecosystem-todo.md`
- Modify: `docs/channel-review-policy.md` (compatibility page)
- Modify: `docs/TODO.md` (compatibility page)
- Modify: `docs/README.md`
- Modify: decision, contract, overview, and manual cross-references to the policy or TODO

- [x] Copy the review policy to `docs/policies/channel-review.md` and preserve its approved status, review criteria, AI-assistance limits, decision flow, response periods, and unresolved items.
- [x] State explicitly which review requirements are ecosystem-wide and which examples or criteria currently describe SanpoGuide; do not infer unapproved multi-app review rules.
- [x] Replace references to `SanpoGuide のアプリが取り込む` with scoped language that distinguishes the ecosystem baseline from SanpoGuide's current behavior.
- [x] Copy `docs/TODO.md` to `docs/work/ecosystem-todo.md`, preserving each open item and its ownership/context while ensuring it does not duplicate app-specific or console-specific TODOs.
- [x] Replace the old policy and TODO files with compatibility pages linking to their canonical locations.
- [x] Update the overview and `docs/README.md` catalogs to point to the canonical policy and work list.
- [x] Check that no unapproved item in `stakeholder-needs.md` is presented as a confirmed policy or completed task.

**Verification:**
- Compare old and canonical policy/TODO sections to ensure no open item or criterion was silently dropped.
- Confirm the existing Redocly command and its asset locations remain untouched.
- Run `git diff --check`.
- Include this batch in the final integrated documentation commit after Tasks 5–6 pass verification.

### Task 5: Isolate SanpoGuide manuals and label current product state

**Files:**
- Create: `docs/apps/sanpoguide/manual/README.md`
- Create: `docs/apps/sanpoguide/manual/walker.md`
- Create: `docs/apps/sanpoguide/manual/publisher.md`
- Create: `docs/apps/sanpoguide/manual/producer.md`
- Create: `docs/apps/sanpoguide/manual/operator.md`
- Modify: `docs/manual/README.md` (compatibility page)
- Modify: `docs/manual/walker.md` (compatibility page)
- Modify: `docs/manual/publisher.md` (compatibility page)
- Modify: `docs/manual/producer.md` (compatibility page)
- Modify: `docs/manual/operator.md` (compatibility page)
- Modify: `docs/README.md`

- [x] Move each manual to `docs/apps/sanpoguide/manual/` without removing its actual SanpoGuide instructions, UI labels, or cross-links to app-specific external documentation.
- [x] Rename the manual entry heading and opening metadata so the audience can tell immediately that these instructions apply to SanpoGuide, not every yorisoi app.
- [x] Add a “current as of” date to release state, app version, built-in channel count, management URLs, and other volatile operational facts; do not update such values unless verified from the referenced source.
- [x] Change only shared conceptual definitions that conflict with the approved ecosystem vocabulary; keep “散策者” for the SanpoGuide walker guide and clearly distinguish the code term `user`.
- [x] Update intra-manual links to the new relative paths and ensure each table of contents still points to a real heading.
- [x] Replace each old `docs/manual/` page with a compatibility page linking to the new SanpoGuide-specific page.
- [x] Update `docs/README.md` to list the manual as SanpoGuide-specific and to distinguish it from the yorisoi-wide reference documents.

**Verification:**
- Follow every intra-manual link and check each section anchor after moving the files.
- Confirm the five old manual paths each state their SanpoGuide scope and link to the matching canonical page.
- Run `git diff --check`.
- Include this batch in the final integrated documentation commit after the link audit passes.

### Task 6: Complete the cross-document consistency and link audit

**Files:**
- Review: every Markdown file under `docs/`
- Modify: `docs/README.md`, `docs/decisions/README.md`, `docs/contracts/README.md`, all canonical pages, and all compatibility pages

- [x] Search `docs/` for the terms `散策者`, `散策ガイド`, `SanpoGuide`, `yorisoi`, `付き添いアプリ`, and the old ADR/API IDs; review each match in context and retain app-specific terms only where appropriately scoped.
- [x] Search for duplicate descriptions of actor roles, the AI responsibility layers, publication status, review process, and manual scope; replace redundant normative text with links to the canonical page.
- [x] Search for Markdown links in every `.md` file under `docs/`, including compatibility pages and the design spec.
- [x] Run a temporary Python standard-library link checker from repository root. It must scan all Markdown files below `docs/`, skip absolute web/mail links, resolve relative file targets and directory `README.md` targets, decode percent-escaped paths, verify target files exist, and verify local fragments against normalized Markdown heading slugs. Keep this checker outside the repository and report any links that cannot be checked because they target external repositories.
- [x] Resolve every broken internal path or fragment; if a source heading was renamed, update the link or add an explicit compatibility heading.
- [x] Compare every migrated file with its old path, verify all old ID/path aliases appear in the mapping and compatibility pages, then inspect `git diff --check` and `git status --short`.
- [x] Confirm no files outside `yorisoi-platform` were changed and no API semantics, application code, or OpenAPI assets were modified.

**Verification:**
- The temporary link checker reports zero broken in-repository Markdown file links and zero unresolved in-repository fragments.
- `git diff --check` reports no whitespace errors.
- The final `docs/README.md` links to every canonical document group and the retained OpenAPI assets.
- Review `git diff --stat` and the complete diff to confirm the changes are limited to documentation restructuring and compatibility pages.
- Commit the integrated Tasks 3–6 changes as `docs: complete yorisoi documentation redesign`, including the repository's required `Co-authored-by` trailer.
