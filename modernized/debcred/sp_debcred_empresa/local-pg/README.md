# Local PostgreSQL test environment (profile `local-pg`)

Runs the Java service end to end on your machine without Sybase. **Not production:** production keeps
the COBIS procedures and tables in Sybase ASE and uses profile `ase` (INTENT.md, brief section 7). Here:

- Database `debcred_local` in the Docker container `seguridad-admin-pg` (PostgreSQL 17, `localhost:5434`),
  user `debcred_app`. Connection values in `%USERPROFILE%\.modernize\debcred\debcred-pg.env` (outside the
  repository, never printed).
- `debcred-local.sql`: one schema per COBIS database (`cobis`, `db_biz_pagos`, `db_sat_his`,
  `db_biz_admempresa`, `cob_virtuales`, `cob_cuentas`, `cob_ahorros`, `cob_internet`), the tables the service
  reads and writes (catalogue with the legacy column names, order detail, account masters), fictitious data,
  and the twelve called procedures **simulated** in PL/pgSQL (their source is not available, brief 7-A5),
  including the two notifiers of Phase 4 (`pa_sat_pnotificacion`, `sp_eventos`). The seed configures an SMS
  notification for TRANSCLI on channel DIR/SAT, so the sample request goes through `sp_eventos`.
- `PgAseSession` (package `localpg`): JDBC transactions and savepoints, with a per-statement savepoint so a
  failed statement behaves as in Sybase (PostgreSQL would otherwise abort the whole transaction).

## Run

```powershell
cd modernized\debcred\sp_debcred_empresa
powershell -ExecutionPolicy Bypass -File local-pg\run-local-pg.ps1          # reloads the data, then runs
```

Then, from another terminal (or Postman), post the sample request:

```bash
curl -H "Content-Type: application/json" --data @local-pg/request-transcli.json http://localhost:8080/debitos-empresa
```

## Simulate failures

Every simulated procedure answers `(0, 0)` unless `sim.falla` says otherwise; every call is logged in
`sim.llamada` inside the caller's transaction (a rollback removes it, as in ASE):

```sql
insert into sim.falla values ('sp_ndc_ahcc', 201045, 0);      -- exit A: debit fails
insert into sim.falla values ('sp_grb_comision', 0, 122010);  -- exit B: commission fails
insert into sim.falla values ('sp_eventos', 30001, 0);         -- notification fails: reverses the debit (exit A)
delete from sim.falla;                                         -- back to success
select * from sim.llamada order by id;                         -- what survived the transaction
select * from cobis.bp_total_orden;                            -- header states I / T
```

## Verified on 2026-09-27

| Scenario | Order | Reply | Database afterwards |
|---|---|---|---|
| Happy path (TRANSCLI, current account, commission) | 123456 | 200 `{0, 0}` | header `T`; comision, confcontable, ndc_ahcc, movement `P`, grb_comision committed |
| Exit A: `sp_ndc_ahcc` returns 201045 | 123457 | 200 `{201045, 201045}` | header stays `I`; debit gone (savepoint rollback); only movement `X` committed |
| Exit B: `sp_grb_comision` o_error 122010 | 123458 | 200 `{122010, 122010}` | header stays `I`; debit, movement `P` and commission gone (full rollback); only movement `X` (autocommit) |
| Unknown JSON key | - | 400 problem detail | nothing ran |
| Phase 4, happy path with notification (TRANSCLI, DIR) | 123456 | 200 `{0, 0}` | `sp_eventos` with value "12.10", cost "0.00", SMS service TRC, channel SAT, CTE -> AHO; header `T` |
| Phase 4, `sp_eventos` returns 30001 | 123457 | 200 `{30001, 30001}` | header stays `I`; debit and event gone (savepoint rollback); only movement `X` committed |
