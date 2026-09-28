# Verification: debcred

Written by `scripts/proof_pack.py` on 2026-09-28 05:03 UTC. Each module's verdict is computed from the evidence files by the fixed rules at the end of this page. No model's opinion is part of it.

**Overall: PARTLY PROVEN** (1 partly proven)


You asked about `sp_debcred_empresa`. Every built module is judged from its current evidence files on every run, so no verdict here depends on an earlier run.

Legacy source: `legacy/debcred`. Could it run here: no. The source was checked by modification time, and no version-control tool was run.

| Module | Track | Verdict | Tests executed | Failed | P0 rules tested | Same behavior | Fresh inputs |
|---|---|---|---|---|---|---|---|
| sp_debcred_empresa | rewrite | **PARTLY PROVEN** | 499 | 0 | 6 of 6 | not proven | not proven |

## sp_debcred_empresa: PARTLY PROVEN

Track: rewrite. Folder: `modernized/debcred/sp_debcred_empresa`. Checked 2026-09-28 05:03 UTC.

**What is missing or wrong**

- Same behavior: No equivalence cases were recorded: the new code was never compared with the legacy output.
- Fresh inputs: The legacy could not run here (The legacy is a Sybase ASE / COBIS T-SQL stored procedure; no ASE (nor SQL Server) exists on this machine and the bank's test ASE is not reachable (PREFLIGHT 3b, brief section 7 A4). The local PostgreSQL environment runs the Java service against simulated procedures, not the legacy. The proof is sp…), so the proof is trace-based: no fresh-input compari…

**What passed**

- Tests ran: 499 test(s) executed, 0 failed, 0 skipped. Note: Phases 1-4 in scope (all code blocks of the procedure). Called procedures are in-memory doubles; JDBC adapter tests use mocks, not a real ASE.
- Rules traced: All 6 P0 rule(s) this module answers for are backed by a test that ran and passed.
- Canary: 6 canary run(s) shown by a result file or log; the first (verify's own canary (Phase 4), in a scratch copy: beneficiary account type 8 mapped to 'CON' instead of 'ESP' (RULE-004/RULE-005, lines 968-972)) made 1 more test(s) fail than the clean run (analysis/debcred/equivalence/canary/sp_debcred_empresa/verify-prodcre8). 6 more are only claimed.
- Source untouched: legacy/debcred is untouched: no file changed after the analysis started (1 files compared). Checked by modification time, and no version-control tool was run.

| Check | Result | Detail |
|---|---|---|
| Tests ran | pass | 499 test(s) executed, 0 failed, 0 skipped. Note: Phases 1-4 in scope (all code blocks of the procedure). Called procedures are in-memory doubles; JDBC adapter tests use mocks, not a real ASE. |
| Rules traced | pass | All 6 P0 rule(s) this module answers for are backed by a test that ran and passed. |
| Same behavior | not proven | No equivalence cases were recorded: the new code was never compared with the legacy output. |
| Fresh inputs | not proven | The legacy could not run here (The legacy is a Sybase ASE / COBIS T-SQL stored procedure; no ASE (nor SQL Server) exists on this machine and the bank's test ASE is not reachable (PREFLIGHT 3b, brief section 7 A4). The local PostgreSQL environment runs the Java service against simulated procedures, not the legacy. The proof is sp…), so the proof is trace-based: no fresh-input comparison was possib… |
| Canary | pass | 6 canary run(s) shown by a result file or log; the first (verify's own canary (Phase 4), in a scratch copy: beneficiary account type 8 mapped to 'CON' instead of 'ESP' (RULE-004/RULE-005, lines 968-972)) made 1 more test(s) fail than the clean run (analysis/debcred/equivalence/canary/sp_debcred_empresa/verify-prodcre8). 6 more are only claimed. |
| Source untouched | pass | legacy/debcred is untouched: no file changed after the analysis started (1 files compared). Checked by modification time, and no version-control tool was run. |

tests executed: 499, failed 0, skipped 0

- unit and characterization tests (JUnit 5, jqwik, MockMvc): `cd modernized/debcred/sp_debcred_empresa && rm -rf target .jqwik-database && ~/tools/apache-maven-3.9.9/bin/mvn -o test -Dmaven.test.failure.ignore=true` (from result files): 499 executed, 0 failed, 0 skipped, 32 result files written 2026-09-28 05:02 UTC. Note: Phases 1-4 in scope (all code blocks of the procedure). Called procedures are in-memory doubles; JDBC adapter tests use mocks, not a real ASE.

### P0 business rules

| Rule | Name | Confidence | Result | Tests | Code | A test that names it |
|---|---|---|---|---|---|---|
| RULE-007 | Accounting transaction code and causal are resolved from the accounting configuration; missing configuration aborts the… | High | tested | 20 | 2 | `modernized/debcred/sp_debcred_empresa/src/test/java/com/nexti/debcred/Rule007Rule029Rule030AccountingConfigurationTest.java:0` |
| RULE-008 | Only current, savings, virtual or accounting company accounts may be debited | High | tested | 8 | 2 | `modernized/debcred/sp_debcred_empresa/src/test/java/com/nexti/debcred/Rule009Rule008Rule033Rule026DebitRoutingTest.java:19` |
| RULE-009 | Only current, savings, virtual and accounting account types may be debited | High | tested | 28 | 4 | `modernized/debcred/sp_debcred_empresa/src/test/java/com/nexti/debcred/Rule009Rule008Rule033Rule026DebitRoutingTest.java:0` |
| RULE-012 | Debit outcome sets process status, rolls back on failure, and the movement is always recorded | High | tested | 43 | 8 | `modernized/debcred/sp_debcred_empresa/src/test/java/com/nexti/debcred/NotificationStubTest.java:18` |
| RULE-013 | Separate commission is debited as its own movement; failure rolls back the whole debit | Medium | tested | 81 | 3 | `modernized/debcred/sp_debcred_empresa/src/test/java/com/nexti/debcred/Rule010NoHeaderUpdatedTest.java:71` |
| RULE-014 | Order header transitions from Initial to In-Transition by channel, option and payment form | High | tested | 70 | 2 | `modernized/debcred/sp_debcred_empresa/src/test/java/com/nexti/debcred/AbortedTransactionTest.java:14` |

Inputs left out of the fresh check: Fresh-input check not run: the legacy cannot execute here, so no legacy output exists to compare against; the 56 golden cases in the module's test resources are development cases, not a legacy oracle.; Malformed records not tried for the same reason (INTENT.md asks for exact parity, quirks included, so they must be tried once the bank's test ASE is reachable).

### Waiting for a person

Claude never ticks these. A person decides them.

- [ ] `/code-modernization:modernize-harden debcred` run on the Java service: no open Critical or High finding that is not an accepted parity risk listed in §7 A9. [Phase 5 exit criterion]
- [ ] UAT by the §7 A7 SME on walkthroughs 1–4, recorded in `analysis/debcred/UAT.md`. [Phase 5 exit criterion]
- [ ] Coexistence runbook written: how the façade and the service run side by side, how to roll back to the T-SQL procedure. [Phase 5 exit criterion]
- [ ] `KNOWN_DIFFERENCES.md` lists every observable difference a person approved (expected: none, by the parity decision; any that appear are listed with the approver's reason). [Phase 5 exit criterion]
- [ ] A20: To confirm on the bank's test ASE (from the Phase 4 review, 2026-09-27): the COBIS `raiserror` + return-code convention through jTDS (the notifiers already carry on; exits A and B would need the same), `convert(varchar(11), money)` over 100,000,000.00, jTDS `prepareSQL` inside `begin tran` and… [open question, section 7]

### What this does not prove

- It does not prove behavior on inputs nobody tried: the equivalence cases and the fresh inputs are samples, not the whole input space.
- The legacy could not run here, so the comparison rests on recorded outputs and traces. It is only as good as that recording.
- It does not cover speed, capacity, security, concurrency, or anything the tests and cases do not exercise.
- It traces only the rules that were extracted: behavior nobody wrote down as a rule has no rule to trace.
- A test that names a rule shows the rule is mentioned, not that the test is a good one. The canary shows only that some test can fail.
- It does not replace the review and sign-off of the people who own the system.

## Sign-off

A person fills this in. Claude leaves it blank.

| | |
|---|---|
| Name | ________________ |
| Role | ________________ |
| Date | __________ |
| Decision | accept / accept with conditions / reject |

## How each verdict is computed

PROVEN needs every check to pass: the six below, and for an uplift three more (7 to 9). NOT PROVEN when any check fails. PARTLY PROVEN when nothing failed but a check could not pass.

1. Tests ran: at least one test executed in a fresh run, none failed, none was skipped without a reason, and every result file is newer than the code. The counts must be read by this script from result files or a saved raw runner log; counts only typed in cannot reach PROVEN.
2. Rules traced (rewrite and reimagine): every P0 rule the module answers for is backed by a test that ran and passed: its id is in the test or class name of a result this script parsed, or a test file names it on a line not marked skipped or pending and that file's class ran and passed. A rule named only by skipped, pending or failing tests is named, not run; one only the notes name is claimed. Neither is tested.
3. Same behavior: the development cases, judged again by compare.py, executed at least one case and none differs or is missing (a difference a person approved is allowed and listed); for an uplift, no regression, no new failure, no missing test and no drop in executed tests against BASELINE.md.
4. Fresh inputs: at least 10 new inputs, none with the same output as a development case, ran on the legacy and the new code with no difference. Needed whenever the legacy could run here; when it could not, the proof is trace-based and the best verdict is PARTLY PROVEN.
5. Canary: a deliberate one-line break whose own result file or saved runner log shows failing tests. A line in the notes is a claim.
6. Source untouched: no file under legacy/<system> is newer than the analysis (PREFLIGHT.md), by a plain file walk; no version-control tool is run.
7. Baseline measured (uplift): the old version's numbers in BASELINE.md come from per-test result files, a per-test results file or a raw runner log that this script parsed (in analysis/<system>/baseline/, or on a Recorded: or Machine-readable: line of BASELINE.md), and the typed table agrees with them. A table typed by hand, or sources that disagree, cannot reach PROVEN.
8. Tests kept (uplift): no test file of the untouched legacy tree is missing from the working copy, no more than 25% of them changed, and the legacy tree has test files to compare against (walked by file, no process is run). The list is for a person to review; weakened assertions cannot be detected, only that files changed.
9. Deltas covered (uplift): every Behavioral-silent delta in DELTA_CATALOG.md has its site's file named, as a whole word, by some test file of the working copy. A name is not proof that the test exercises the change.
Open questions, unticked criteria and the sign-off are for a person. They never change the verdict.
A folder that holds only test code and the files that build it is test tooling: it is listed, not judged, and not counted.
