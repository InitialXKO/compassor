# Fix bug

## Configuration
- **Artifacts Path**: {@artifacts_path} → `.zenflow/tasks/{task_id}`

---

## Workflow Steps

### [x] Step: Investigation and Planning
<!-- chat-id: 51cc4532-8f36-44d5-8138-f930a7add71f -->

Analyze the bug report and design a solution.

1. [x] Review the bug description, error messages, and logs
2. [x] Clarify reproduction steps with the user if unclear
3. [x] Check existing tests for clues about expected behavior
4. [x] Locate relevant code sections and identify root cause
5. [x] Propose a fix based on the investigation
6. [x] Consider edge cases and potential side effects

Save findings to `{@artifacts_path}/investigation.md` with:
- [x] Bug summary
- [x] Root cause analysis
- [x] Affected components
- [x] Proposed solution

### [x] Step: Implementation
Read `{@artifacts_path}/investigation.md`
Implement the bug fix.

1. [ ] Add/adjust regression test(s) that fail before the fix and pass after
2. [x] Implement the fix
3. [x] Run relevant tests
4. [x] Update `{@artifacts_path}/investigation.md` with implementation notes and test results

If blocked or uncertain, ask the user for direction.
