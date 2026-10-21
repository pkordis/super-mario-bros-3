---
inclusion: auto
name: mark-ticket-as-done
description: The steps to follow when asked to "mark a ticket as done", close out a ticket, or ship completed ticket work - commit, push, merge to main and transition the Jira issue to Done.
---

# Mark ticket as done

Follow these steps, in order, when asked to mark a ticket as done (or to "close out",
"ship" or "finish" ticket work). Each step gates the next: do not proceed past a failure.

## 1. Verify the work is actually finished

- `.\mvnw.cmd clean test` must be **fully green**. A pre-existing failure is not an
  excuse — either fix it or stop and say why it cannot be fixed before going further.
- Re-read the ticket's acceptance criteria and confirm each one. Name any that are
  deliberately out of scope.
- Clean up scratch files (temp scripts, preview images) so they do not land in the commit.

## 2. Review what is about to be committed

- `git status --short` and `git diff --stat`, and read them. The index may already hold
  staged files from earlier operations (a `git stash` round-trip stages what it restores).
- Stage deliberately, by name. Do **not** `git add -A`.
- **Leave out anything that is not part of this ticket** — unused assets dropped in for a
  later ticket, experiments, unrelated fixes. Report what was excluded and why; untracked
  files are not lost by being left out.
- Flag anything that looks like a secret before it goes in.

## 3. Commit

- Title: `<TICKET-KEY> <short summary>`, matching the existing log
  (`git log --oneline -5`). One line, no trailing full stop.
- Description: a real explanation, not a file list. Cover what behaviour changed, the
  dasm sites it was ported from, any ROM finding that corrected the ticket, bugs fixed
  along the way, and what is deliberately out of scope. The repo's convention is a
  verbose, prose body — follow it.
- Never `--no-verify`. If a hook rewrites files, re-stage and make a **new** commit
  rather than amending.

## 4. Push the branch

- Confirm the local branch and its remote have not diverged
  (`git log --oneline --left-right HEAD...origin/<branch>`).
- `git push`. If a push would need `--force`, **stop and ask** — never force-push
  unprompted.

## 5. Merge to main

- `git checkout main`, `git pull`, then merge the feature branch.
- Use `--no-ff` so the feature branch stays visible in the history, matching the
  existing merge commits in the log.
- Resolve nothing blindly: if the merge conflicts, stop and report.
- Re-run `.\mvnw.cmd clean test` on main after the merge, before pushing it.
- `git push origin main`.
- Switch back to the feature branch afterwards so the working state is unchanged.

## 6. Transition the Jira ticket

- Read the available transitions first (`jira_get_transitions`) — the target state's
  name varies by board, and only some transitions are legal from the current status.
- Transition the issue to Done.
- Confirm the new status rather than assuming the call worked.

## 7. Report

State the commit title, the merge, the ticket's new status, and anything excluded or
left open. If any step was skipped, say which and why.
