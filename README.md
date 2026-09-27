# sp_debcred_Bolivariano

Modernization of the Sybase ASE / COBIS stored procedure `dbo.sp_debcred_empresa` (Banco Bolivariano)
into a Java 21 / Spring Boot microservice, done with the Claude Code `code-modernization` plugin.

| Folder | Content |
|---|---|
| `legacy/sp-banco-bolivariano/` | The original procedure (read-only input; never modified) |
| `analysis/debcred/` | Preflight, assessment, topology map (`TOPOLOGY.html`), business rules (39, 6 P0), rule reviews, the approved modernization brief, the one-page report (`REPORT.html`) |
| `modernized/debcred/sp_debcred_empresa/` | The Java service (Phase 1: debit core and transaction contract), its characterization tests and `TRANSFORMATION_NOTES.md` |

Business logic is preserved as-is (INTENT.md); the ten called procedures stay in Sybase and are invoked
over one JDBC connection. Equivalence is spec-based: no ASE is available where the work is done, so the
verdict ceiling is PARTLY PROVEN until the bank's test ASE is reachable.

Build and test: `cd modernized/debcred/sp_debcred_empresa && mvn test` (offline: `mvn -o test`).

This repository is a published copy; the working copy is the plugin workspace. `sync-from-plugin.sh` refreshes it.
