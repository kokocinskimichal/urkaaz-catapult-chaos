---
name: push
description: Validate and document Urkaaaz changes, commit them, and push develop.
---

# /push — commit and push Urkaaaz

Use this skill when the user invokes `/push` or explicitly says `wypchnij`.

## Scope

This skill applies only to the Urkaaaz repository:

```text
~/workspace/urkaaaz
```

The target branch is `develop`. The target repository is
`kokocinskimichal/urkaaz-catapult-chaos`.

## Required workflow

1. Confirm the current working directory is `~/workspace/urkaaaz`.
2. Inspect the worktree and changed files.
3. Review all changed code against `docs/architecture/architecture.md` and
   `AGENTS.md`:
   - single responsibility;
   - correct dependency direction;
   - no gameplay rules in Android UI;
   - no Android dependencies in domain or simulation;
   - immutable snapshots and explicit command/event boundaries;
   - no unrelated or accidental files.
4. Before creating the commit, update the relevant project knowledge Markdown
   files with the changes being pushed:
   - `docs/project/CHANGELOG.md` with a concise, user-visible summary under a
     `## YYYY-MM-DD` heading for the current date. Group entries under
     `### Added`, `### Changed`, `### Fixed` or `### Removed` as appropriate.
     Reuse the current date heading if it already exists; do not add empty
     categories or generic progress notes.
   - `AGENTS.md` for durable agent workflow or repository rules;
   - `docs/project/knowledge.md` for current architecture, screen structure,
     behavior, important files or known limitations;
   - `docs/architecture/architecture.md` for Clean Architecture decisions or
     boundaries;
   - `docs/reference/GLOSSARY.md` when terminology or domain concepts changed.

   Do not add a generic progress note. Record only durable, factual knowledge
   that will help future implementation work. If a knowledge file is already
   accurate, leave it unchanged.

5. Before staging, process `docs/project/idea-inbox.md`:
   - Move each captured idea to the relevant canonical topic document. Keep it
     clearly marked as a proposal unless the user explicitly approved it; do
     not implement ideas as part of this documentation step.
   - If an idea is cross-cutting or has no clear topic document, move it to
     `docs/project/ideas.md`.
   - Preserve the idea's wording, intent and status. Remove an inbox entry
     only after verifying that its destination contains the complete note.
     Leave the inbox's empty-state text when all entries have been moved.

6. Re-run the validation gate after updating documentation:

   ```bash
   ./gradlew test assembleDebug --quiet
   git diff --check
   git status --short
   ```

7. Stage all changes:

   ```bash
   git add -A
   ```

8. If staged changes exist, create a focused commit. Every commit must include:

   ```text
   Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>
   ```

9. Push `develop` to `origin`.

## GitHub account

Before creating a commit, use this repository-local Git identity:

```bash
git config user.name "Michał Kokociński"
git config user.email "kokocinm@gmail.com"
```

Always use the GitHub account `kokocinskimichal`. Before pushing, verify that
this account is authenticated and make it the active GitHub CLI account:

```bash
env -u GH_TOKEN gh auth switch --user kokocinskimichal
env -u GH_TOKEN gh auth status --active --hostname github.com
```

If the environment-level Git helper selects another account, override the
helper for this push with the active `gh` credential helper rather than
changing repository history or force-pushing:

```bash
env -u GH_TOKEN git \
  -c credential.helper= \
  -c credential.https://github.com.helper= \
  -c credential.https://github.com.helper='!env -u GH_TOKEN gh auth git-credential' \
  push origin develop
```

Never print, store, or commit access tokens.

## No-op and failure rules

- If the worktree is clean, do not create an empty commit; report that there
  is nothing to commit and push any local commits that are ahead of origin.
- Do not use `git reset --hard`, force-push, rebase, or discard user changes.
- If tests, build, architecture review, or `git diff --check` fail, stop before
  committing and report the failure.
- If GitHub rejects authentication or permissions, preserve the local commit
  and report the exact non-secret error.
- Do not push a branch other than `develop` unless the user explicitly names it.
