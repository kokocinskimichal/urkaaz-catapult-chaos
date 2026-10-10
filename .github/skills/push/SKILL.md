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
3. Review all changed code against `architecture.md` and `AGENTS.md`:
   - single responsibility;
   - correct dependency direction;
   - no gameplay rules in Android UI;
   - no Android dependencies in domain or simulation;
   - immutable snapshots and explicit command/event boundaries;
   - no unrelated or accidental files.
4. Before creating the commit, update the relevant project knowledge Markdown
   files with the changes being pushed:
   - `AGENTS.md` for durable agent workflow or repository rules;
   - `docs/PROJECT_KNOWLEDGE.md` for current architecture, screen structure,
     behavior, important files or known limitations;
   - `architecture.md` for Clean Architecture decisions or boundaries;
   - `GLOSSARY.md` when terminology or domain concepts changed.

   Do not add a generic progress note. Record only durable, factual knowledge
   that will help future implementation work. If a knowledge file is already
   accurate, leave it unchanged.

5. Re-run the validation gate after updating the knowledge files:

   ```bash
   ./gradlew test assembleDebug --quiet
   git diff --check
   git status --short
   ```

6. Stage all changes:

   ```bash
   git add -A
   ```

7. If staged changes exist, create a focused commit. Every commit must include:

   ```text
   Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>
   ```

8. Push `develop` to `origin`.

## GitHub account

Always use the GitHub account `michalkokocinski`. Before pushing, verify that
this account is authenticated and make it the active GitHub CLI account:

```bash
gh auth switch --user michalkokocinski
gh auth status --user michalkokocinski
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
