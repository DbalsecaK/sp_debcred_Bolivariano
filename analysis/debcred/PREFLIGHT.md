# Preflight: debcred

Date: 2026-09-27. Code: `legacy/debcred` → `sp-banco-bolivariano/` in this workspace (a symlink; one file, `sp_debcred_empresa.sp`). Nothing under it was modified.

## Answers

Asked with a pop-up on 2026-09-27; the person's answers verbatim.

### 1. Scope: complete system or a slice? What outside depends on the inside, and may it break?

> Un trozo: es un SP de COBIS; otros SPs y canales lo invocan

Meaning: one stored procedure of the COBIS core banking framework. Other stored procedures and channels call it; breaking them is **not** acceptable (see Check 6).

### 2. Build and test locally? How long is the full CI pipeline?

> Sí, hay un ASE o SQL Server de pruebas accesible

Follow-up, where it is and what it holds:

> SQL Server local o Docker, solo con estas tablas

Corrected by the person right after the report:

> perdon usemos postgress y esta en el docker en mi maquina

Meaning: the engine is **PostgreSQL 17.6 in Docker on this machine** (container `seguridad-admin-pg`, 127.0.0.1:5434). **Caveat:** PostgreSQL cannot execute the legacy Sybase T-SQL procedure, so it is the **target** (PL/pgSQL) or test engine for new code, not the legacy running. No Sybase ASE or SQL Server exists here. No answer about pipeline duration (there is no pipeline: see 3).

### 3. Bespoke build infrastructure? Where documented?

> No, se instala con isql como cualquier SP

### 4. Prior attempts? What went wrong?

> No, es la primera vez

(Note for the record, from the assistant, not the person: NexTI's `modernize-platform` project has a T-SQL/Sybase analyzer and an "ASE-to-Java" synthetic case, but never processed this procedure.)

### 5. Off limits in this pass?

> Nada fuera de límites

## Check 6: scope boundary (the finding `brief` must read)

`debcred` is a **slice**: one procedure of a multi-database COBIS installation. Both directions cross the boundary.

**Outbound (inside depends on outside; the map will not see these):**

| Kind | Objects | Count |
|---|---|---|
| Stored procedures called | `db_biz_admempresa..sp_con_confcontable` (3 calls), `db_biz_admempresa..sp_con_comision`, `sp_grb_mov_y_frmpgo` (2), `sp_grb_comision` (2), `sp_ndc_ahcc`, `pa_sat_pnotificacion`, `db_biz_pagos..sp_graba_tran_servicio`, `cob_virtuales..sp_vi_ndc_automatica`, `cob_internet..sp_eventos`, `cobis..sp_cerror` | 10 procedures, **none in the tree** |
| Tables read or written | `db_biz_pagos..bp_orden`, `bp_detalle`, `bp_total_orden` (written), `db_sat_his..bp_orden_his`, `bp_detalle_his`, `bp_total_orden_his` (written), `db_biz_admempresa..ba_tabla`, `cob_ahorros..ah_cuenta`, `cob_cuentas..cc_ctacte`, `cob_virtuales..vi_cuenta` | 10 tables in 6 databases, **no DDL in the tree** |
| Catalog data | `ba_tabla` parameter rows (5 reads) drive branching | not in the tree |

Every rule that depends on what those procedures return (`@w_return` codes, commissions, accounting configuration) or on `ba_tabla` values will come out at Confidence Medium or Low until their source or a signed specification is available. This is the same gap that stopped the BI-cobol project at PRG016/PRG008: **get the source of the 10 procedures and the DDL of the 10 tables before `extract-rules`**, or accept assumed contracts in the brief's §7.

**Inbound (blast radius):** the person says other procedures and channels invoke `sp_debcred_empresa`, and that breaking them is not acceptable. The callers are not in the tree, so they cannot be listed here. `brief` must record, for the procedure's signature (36+ parameters with defaults, `@o_*` outputs, return code), one explicit decision: keep the T-SQL procedure callable during the transition (a façade that delegates to the new code), widen scope to the callers, or schedule the break. Ask the bank for the list of callers (`sp_depends` / `sysdepends` on the ASE, or a text search of the COBIS source tree for `sp_debcred_empresa`).

## Checks

| # | Check | Status | Found | Fix |
|---|---|---|---|---|
| 0 | Human answers | ✅ | All five answered (above). | Follow up on 2: the engine is planned, not present. |
| 1 | Stack | ✅ | Transact-SQL, Sybase ASE dialect (COBIS: `db..object` cross-database names, `@w_return` conventions, `cobis..sp_cerror`). One file, 2,173 lines (965 non-blank, 93 comment lines), 76 `if` blocks, 8 transaction statements (`begin tran`/`commit`/`rollback`), 40 distinct `@w_*` work variables, no cursors, no temp tables. UTF-8 with CRLF. | None. |
| 2 | Analysis tooling | ⚠️ | `python` 3.14.7 present (`python3` is the Windows Store stub: commands saying `python3` must use `python` or `py -3`). `scc`, `cloc`, `lizard` missing. | Optional: `winget install boyter.scc` (LOC and complexity become exact); `pip install lizard` (portfolio only). Without them assess falls back to `find` + `wc`. |
| 3a | Build definition | ⚠️ | No CI, pipeline, install script or DDL beside the source. Per answer 3, installation is `isql -i sp_debcred_empresa.sp` against the ASE. | Ask for the COBIS install order (this procedure must be created after the 10 it calls, or with deferred name resolution). |
| 3b | Legacy toolchain smoke test | ❌ | No Sybase ASE, no SQL Server, `isql`/`tsql` missing (`sqlcmd` 16.0 present, no server). The PostgreSQL the person named cannot parse or run Sybase T-SQL. Level 1 and Level 2 could not run. | The legacy cannot run here: equivalence will be **trace-based** (recorded outputs from the bank's ASE, or SME-confirmed examples) and the best verdict is PARTLY PROVEN. To lift that ceiling later, an ASE with the COBIS schema (SAP ASE Express in Docker, or the bank's test ASE) is needed; SQL Server would only approximate the dialect. |
| 3c | Target toolchain (PostgreSQL, then java-spring) | ✅ | **Proven on this machine:** in a throwaway database on `seguridad-admin-pg` (PostgreSQL 17.6, Docker), a schema, a table, a PL/pgSQL function with an insert/update path and a validation return code were created, and a `DO … ASSERT` test passed ("smoke test passed"); the database was dropped afterwards. Also present: Java 24, `.NET 10.0.400`, Maven at `~/tools/apache-maven-3.9.9` (not on PATH), Docker 28.5. | **java-spring re-check (2026-09-27, Phase 1 plan gate):** JDK 24 (compiling for release 21), Maven 3.9.9, Spring Boot 4.1.1 starters and the Sybase JDBC driver jTDS 1.3.1 resolve from the local Maven cache; the Java service can be built and tested offline here. No ASE to connect to (3b), so the Sybase ports are exercised against mocks. PostgreSQL (PL/pgSQL, or Java/.NET over PostgreSQL) is ready when it does. The COBIS `db..table` names map to PostgreSQL schemas (one schema per COBIS database) or to one schema with prefixes: a `brief` decision. |
| 4 | Source completeness | ❌ | Missing: 10 called procedures (see Check 6), DDL of 10 tables in 6 databases, `ba_tabla` parameter data, and the callers. No deployment descriptors (COBIS transaction catalog `cl_ttransaccion`/`cl_catalogo` entries that route to this procedure). No binary-only artifacts. | Ask the bank for: the 10 `.sp` files, `ddlgen`/DDL of the 10 tables, an export of the `ba_tabla` rows this procedure reads, and the callers list. Put them in `sp-banco-bolivariano/` (they are read, never edited). |
| 5 | Optional context | ⚠️ | No APM or batch logs. `sp-banco-bolivariano/` is in this repo with a single commit ("1"): no real history. | Optional: production execution logs of this procedure (volumes, error codes returned) sharpen risk ranking. |
| 6 | Scope boundary | ⚠️ | Slice with outbound and inbound crossings (detailed above). | Decisions recorded in `brief` §7. |
| 7 | Source protected from edits | ⚠️ | No `Edit` deny rule for `legacy/**` in `.claude/settings.json` (only `enabledPlugins`), none in `settings.local.json`, none for `legacy` in user settings. `legacy/debcred` is a symlink, so the target path needs its own rule. | Add to `.claude/settings.json`: `{ "permissions": { "deny": ["Edit(/legacy/**)", "Edit(//c/Users/david.balseca/OneDrive - NEXTI BUSINESS SOLUTIONS SAS/Escritorio/New NexTI/Nexti Modernization/plugin/sp-banco-bolivariano/**)"] } }`. A permission rule covers Claude's file tools and recognized shell commands, not a script that opens files itself; the hard guarantee is the operating system (read-only mount or sandbox). Managed settings are not read here. |

## Verdict per command

| Command | Verdict | Why |
|---|---|---|
| `assess` | **Ready-with-gaps** | Stack detected, python present; LOC/complexity coarse without `scc`; risk ranking blind to the 10 external procedures. |
| `map` | **Ready-with-gaps** | The call graph will have 10 unresolved procedure nodes and 10 tables with no columns (no DDL): data lineage stops at table names. |
| `extract-rules` | **Ready-with-gaps** | Rules that depend on `@w_return` of external procedures or on `ba_tabla` values will carry SME questions. Fewer gaps if Check 4's files arrive first. |
| `brief` | **Ready** | Needs only the discovery artifacts. Must record the Check 6 decisions and the missing-source strategy. |
| `transform` / `reimagine` | **Ready-with-gaps** (once `brief` names the target) | Target engine PostgreSQL proven (3c). Legacy toolchain red: equivalence is trace-based, ceiling PARTLY PROVEN, unless an ASE with the COBIS schema appears. Missing source (Check 4) is the bigger gap. |
| `uplift` | **Not ready** | Would mean ASE → SQL Server (or newer ASE); neither engine is here, no migration tool (SSMA for Sybase) installed, inbound consumers undecided. |
| `harden` | **Ready-with-gaps** | Python present; no T-SQL SAST tool found (a SQL injection review of dynamic SQL is manual: this file builds no dynamic SQL strings as far as grep shows). |

## Most important fix

Get the missing source before `extract-rules`: the 10 called procedures, the DDL of the 10 tables and the `ba_tabla` rows. Everything downstream is as good as what is in the tree. Second: recorded outputs of the procedure from the bank's test ASE (inputs, return codes, resulting rows in `bp_total_orden`, `bp_*_his`), because no engine here can run the Sybase original; PostgreSQL is the target side only.

Next step: `/code-modernization:modernize-assess debcred`
