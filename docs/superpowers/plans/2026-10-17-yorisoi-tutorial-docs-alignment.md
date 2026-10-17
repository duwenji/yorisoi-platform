# yorisoi-tutorial Documentation Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to execute this plan task-by-task. The user prefers inline work without subagents.

**Goal:** Keep the tutorial's existing curriculum while making its source-of-truth guidance and repository links accurate on GitHub.

**Architecture:** Retain all 86 Markdown lessons and their order. Clarify that the tutorial is explanatory, point to canonical platform documents, and convert sibling-repository Markdown links from local checkout-relative targets to GitHub-renderable links.

**Tech Stack:** Markdown, Git, PowerShell, Python standard library for byte-preserving link updates and temporary link validation.

**Spec:** `docs/superpowers/specs/2026-10-17-cross-repository-docs-alignment-design.md`

## Global Constraints

- Preserve the tutorial's chapter order, hands-on exercises, and learning objectives.
- Treat platform ADRs/contracts and repository-local design documents as sources of truth; the tutorial is explanatory, not normative.
- Use canonical platform document paths and qualify overlapping numeric IDs with their repository.
- Do not change application or console code, API formats, or agreed technical decisions.
- Convert only Markdown link destinations for sibling repositories; do not rewrite shell commands or local checkout instructions.
- Do not test external URL availability; verify sibling link targets against the local checkouts and all internal paths/fragments.

---

## File Structure

- Review all 86 Markdown files in the tutorial repository.
- Update repository guidance and source hierarchy in:
  - `README.md`
  - `MAINTENANCE.md`
  - `00-overview/01-what-we-build.md`
  - `00-overview/04-reading-the-repos.md`
- Update platform citations and related explanations where needed in:
  - `01-kotlin-android/02-gradle.md`
  - `04-outside/03-ai.md`
  - `05-station-format/02-package-format.md`
  - `05-station-format/04-materials-cues.md`
  - `06-trust/05-verify-in-app.md`
  - `09-console/05-docs.md`
  - `ref/openapi.md`
- Convert sibling repository Markdown links throughout `**/*.md` (currently present in 79 files) to GitHub-renderable URLs. Preserve source text and local shell commands.

## Tasks

### Task 1: Align tutorial entry points and source-of-truth guidance

**Files:**
- Review: all 86 Markdown files.
- Modify as needed: `README.md`, `MAINTENANCE.md`, `00-overview/01-what-we-build.md`, `00-overview/04-reading-the-repos.md`

- [ ] Read the README, maintenance guide, and repository-reading chapter. Inventory references to old platform paths, old stakeholder terminology, and numeric IDs that are ambiguous without a repository name.
- [ ] Update `README.md` only where it describes repository roles or points to moved platform documentation; preserve the curriculum, learning order, and hands-on descriptions.
- [ ] Update `MAINTENANCE.md` so periodic review follows the current platform canonical docs and shared vocabulary. Keep the existing local `../yorisoi-*` shell commands and commit-comparison workflow intact.
- [ ] Update `00-overview/01-what-we-build.md` and `00-overview/04-reading-the-repos.md` with the current repository roles, canonical platform categories/paths, and explicit `platform`/`console` ID qualification.
- [ ] Confirm no chapter treats a tutorial explanation as overriding a platform contract or a repository-local implementation decision.

### Task 2: Convert sibling-repository links and canonicalize platform destinations

**Files:**
- Modify Markdown link destinations in all files matching `**/*.md` (79 files currently contain sibling-repository links).
- Update platform contract/ADR/overview citations in `00-overview/01-what-we-build.md`, `00-overview/04-reading-the-repos.md`, `01-kotlin-android/02-gradle.md`, `04-outside/03-ai.md`, `05-station-format/02-package-format.md`, `05-station-format/04-materials-cues.md`, `06-trust/05-verify-in-app.md`, `09-console/05-docs.md`, `MAINTENANCE.md`, and `ref/openapi.md` where matches are found.

- [ ] Run a byte-preserving Python standard-library script from the tutorial root over `.md` files. For Markdown targets beginning with one or more `../` segments followed by `yorisoi-platform/`, `yorisoi-sanpoguide/`, or `yorisoi-console/`, replace only the link destination with `https://github.com/duwenji/<repo>/blob/main/<path>`; use `tree/main` for directory targets. Leave labels, prose, code, and shell commands unchanged.
- [ ] In platform URLs, map moved paths to canonical destinations: ADR files to `docs/decisions/`, API files to `docs/contracts/`, `docs/channel-review-policy.md` to `docs/policies/channel-review.md`, stakeholders to `docs/overview/roles-and-vocabulary.md`, TODO to `docs/work/ecosystem-todo.md`, and manuals to `docs/apps/sanpoguide/manual/`. Keep `docs/api/` references for the OpenAPI assets, which did not move.
- [ ] Replace any links to a directory of numbered ADRs with a direct link to the corresponding canonical ADR or `docs/decisions/README.md`.
- [ ] Search the full tutorial again for relative Markdown links to sibling repositories and old platform paths. Verify remaining relative `../yorisoi-*` strings are local shell commands or explanatory code, not Markdown link targets.
- [ ] Review the bulk diff to ensure only link destinations and directly related stale descriptions changed; confirm line endings and unrelated chapter text are unchanged.

### Task 3: Verify tutorial links and commit

**Files:**
- Review all 86 Markdown files and the complete tutorial diff.

- [ ] Run a temporary Python standard-library link checker over all tutorial Markdown files. Validate same-repository relative file targets and heading fragments.
- [ ] For GitHub links to `yorisoi-platform`, `yorisoi-sanpoguide`, and `yorisoi-console`, map the URL repository/path back to `C:\Dev\apps\<repo>` and verify that the target file or directory exists locally. Do not perform network requests.
- [ ] Search for `docs/adr/`, `docs/channel-review-policy.md`, `docs/secret-messages-api.md`, `docs/channel-list-api.md`, `docs/channel-package-format.md`, `docs/material-producer-api.md`, `docs/stakeholders.md`, `docs/TODO.md`, and `docs/manual/` in platform link targets; resolve all obsolete references.
- [ ] Confirm the README learning sequence and all lesson-to-lesson relative links remain unchanged and valid.
- [ ] Run `git diff --check`, review the full diff, and commit as `docs: align tutorial references with canonical docs`.
