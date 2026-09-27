# Intent: debcred

- **Date:** 2026-09-27
- **System:** `debcred` = `sp-banco-bolivariano/sp_debcred_empresa.sp` (Sybase/COBIS T-SQL stored procedure `dbo.sp_debcred_empresa`, 2,173 lines, one file). Linked as `legacy/debcred`.
- **Goal:** **understand first** ("Entenderlo primero: mapa y lista de lo que hace"), then **transform**. Decision of 2026-09-27 after the assessment (verbatim below): a technology migration from Sybase to a **Java microservice**, business logic unchanged.
- **From:** Sybase ASE T-SQL (COBIS framework: `cobis..sp_cerror`, databases `db_biz_pagos`, `db_sat_his`, `cob_ahorros`, `cob_cuentas`, `cob_virtuales`, `db_biz_admempresa`).
- **To:** **Java microservice** (decided 2026-09-27). Engine proven on this machine for the new side: **PostgreSQL 17.6 in Docker** ("perdon usemos postgress y esta en el docker en mi maquina"). **Open for `brief`:** what PostgreSQL holds, given that the ten called procedures and their tables stay in Sybase (see constraints): the service's own state only, a replica of the tables it reads, or nothing yet.

## What must stay true

Decision of 2026-09-27, verbatim:

> se debe mantener la logica actual no cambiar reglas de negocio, porque estamos haciendo una migracion de tecnologia sysbase a java microservicio, el sp debe seguir invocando a los otros sp que existen y no son migrados todavia

Consequences every later command must obey:

1. **No business-rule change.** The three transaction outcomes, the notification-reverses-debit behavior (`:1248`), company `'1295'` (`:1276`), the exempt-service list (`:2080`), the return-code conventions: all are **preserved as-is** unless a person rules otherwise in the brief's §7. The assessment's security findings SEC-001..013 are **recorded, not fixed** in this migration (parity), like JSEC-012..015 in BI-cobol; each needs an explicit ruling.
2. **The ten called procedures stay in Sybase ASE** and are invoked from the Java service unchanged (`sp_con_comision`, `sp_con_confcontable` ×3, `sp_vi_ndc_automatica`, `sp_ndc_ahcc`, `sp_graba_tran_servicio`, `pa_sat_pnotificacion`, `sp_eventos`, `sp_grb_mov_y_frmpgo` ×2, `sp_grb_comision` ×2, `sp_cerror`). This means a JDBC connection to ASE (jConnect or jTDS) and one **transaction spanning the Java service and the ASE calls**: today's `begin tran` / `save tran` / `rollback tran @w_savepoint` semantics must be reproduced over that connection. This is the central design risk for `brief`.
3. **The direct table access stays too** (reads of `ba_tabla`, `bp_orden`, `bp_detalle`, `*_his`, `cc_ctacte`, `ah_cuenta`, `vi_cuenta`; writes to `bp_total_orden` / `bp_total_orden_his`) unless the brief moves a table.
4. **The COBIS callers are not migrated**: `dbo.sp_debcred_empresa` keeps its 47-parameter signature and return conventions as a façade that delegates to the Java service (or the callers are re-pointed; brief decides).
   **Superseded by brief §7 A2 (David Balseca, 2026-09-27):** "el sp_debcred_empresas debe quedar como microservicio api rest": the procedure becomes a REST API and its callers are re-pointed to it; no T-SQL façade. The 47-parameter contract and both error channels (return code and `@o_error`, A10) are kept in the API. Observable leftovers (the line-882 result set, `@o_reg_a_proc`) are decided at the Phase 1 plan gate (A11).

- "El comportamiento debe coincidir exactamente, incluidas rarezas conocidas": behavior must match exactly, including known quirks. Verification tries malformed and edge inputs; no deviation is accepted without a person's written approval.

## Answers, word for word

- Objetivo: "Entenderlo primero: mapa y lista de lo que hace"
- Restricciones: "El comportamiento debe coincidir exactamente, incluidas rarezas conocidas"
