# SanpoGuide Documentation Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to execute this plan task-by-task. The user prefers inline work without subagents.

**Goal:** Make the SanpoGuide repository's documentation entry points and ecosystem references consistent with the canonical yorisoi documentation.

**Architecture:** Keep the repository's app-specific `docs/` structure, add a reader-oriented index, and link common policy and contract topics to their canonical locations in `yorisoi-platform`. Do not relocate app documentation or change application behavior.

**Tech Stack:** Markdown, Git, PowerShell, Python standard library for temporary link validation.

**Spec:** `docs/superpowers/specs/2026-10-17-cross-repository-docs-alignment-design.md`

## Global Constraints

- Keep the SanpoGuide `docs/` structure and add an index rather than mirroring the platform tree.
- Use `yorisoi-platform` as the canonical source for ecosystem-wide policies, vocabulary, and contracts.
- Use app-specific wording such as 「散策者」 only when the text is specifically about SanpoGuide.
- Do not change application code, API formats, or agreed technical decisions.
- Verify variable app facts against source/configuration before changing them; date confirmed current-state facts.
- Do not test external URL availability; verify local Markdown paths and anchors.
- Do not change prompt source or generated prompt Markdown under `prompts/`.

---

## File Structure

- Create: `docs/README.md` — reader-oriented index for app documentation.
- Review and modify only where needed:
  - `README.md`
  - `docs/architecture.md`
  - `docs/channels.md`
  - `docs/play-console.md`
  - `docs/privacy-policy.md`
  - `docs/prompts.md`
  - `docs/release.md`
  - `docs/TODO.md`

## Tasks

### Task 1: Add the app documentation index

**Files:**
- Create: `docs/README.md`
- Modify: `README.md`
- Review: all eight Markdown files listed in File Structure

- [ ] Read each in-scope file. Classify its content as app behavior, app implementation, app operation/legal information, or ecosystem-level contract/policy; verify current app facts against the relevant source or configuration before changing them.
- [ ] Create `docs/README.md` with reader links to architecture, channel design, prompt design, Play Console declarations, privacy policy, release procedure, and app TODO. State that these are SanpoGuide-specific documents and link ecosystem-wide definitions to the platform canonical documentation.
- [ ] In the root `README.md`, replace the old platform manual URL with `https://github.com/duwenji/yorisoi-platform/blob/main/docs/apps/sanpoguide/manual/README.md`; add a relative link to the new app documentation index.
- [ ] Confirm the index does not present app behavior or current release status as an ecosystem-wide rule.

**Verification:**
- Open every index target and confirm it resolves to the intended file or heading.
- Review the diff to confirm only Markdown files are changed.
- Run `git diff --check`.

### Task 2: Align platform references and verify the app documentation

**Files:**
- Modify where required: `README.md`, `docs/architecture.md`, `docs/channels.md`, `docs/prompts.md`, `docs/release.md`, `docs/TODO.md`
- Review: `docs/play-console.md`, `docs/privacy-policy.md`

- [ ] Search all in-scope Markdown for `yorisoi-platform`, `ADR-00`, `API-00`, and old platform paths. Update old paths to canonical locations under `docs/decisions/`, `docs/contracts/`, `docs/overview/`, `docs/policies/`, `docs/work/`, or `docs/apps/sanpoguide/manual/`.
- [ ] Qualify overlapping identifiers with their source, for example `platform API-CHANNEL-PACKAGE`, and keep statements about SanpoGuide behavior app-specific.
- [ ] Do not alter privacy or store declarations unless the stated behavior is verified against the app and the change does not modify legal meaning.
- [ ] Run a temporary Python standard-library checker over `README.md` and `docs/**/*.md` to verify every relative Markdown file target and heading fragment. For sibling platform links, compare the URL path with the corresponding file under `C:\Dev\apps\yorisoi-platform`.
- [ ] Search again for old platform paths and confirm any remaining numeric IDs are code/compatibility references rather than stale links.
- [ ] Review the final `git diff --check` and `git diff --stat`; commit the repository changes as `docs: align SanpoGuide documentation`.
