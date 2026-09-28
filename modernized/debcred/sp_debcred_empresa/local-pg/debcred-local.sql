-- Local test environment for sp-debcred-empresa on PostgreSQL (profile local-pg). NOT production:
-- production keeps the COBIS procedures and tables in Sybase ASE (INTENT.md). One PostgreSQL schema per
-- COBIS database; the ten called procedures are SIMULATIONS (their source is not available, brief 7-A5):
-- each one records its call in sim.llamada and answers 0 unless a row in sim.falla says otherwise.
-- Re-runnable: drops and recreates everything. Fictitious data only.

drop schema if exists sim, cobis, db_biz_pagos, db_sat_his, db_biz_admempresa, cob_virtuales cascade;
create schema sim;
create schema cobis;
create schema db_biz_pagos;
create schema db_sat_his;
create schema db_biz_admempresa;
create schema cob_virtuales;

-- ---- tables the Java service reads or writes directly ------------------------------------------
create table db_biz_admempresa.ba_tabla (tb_codigo int primary key, tb_tabla varchar(30) not null);
create table db_biz_admempresa.ba_catalogo (
    ct_tabla int not null, ct_cod_catalogo varchar(10) not null, ct_otro_campo_catalogo varchar(30),
    ct_estado char(1) not null default 'A');
create table cob_virtuales.vi_cuenta (vi_cta_banco varchar(24) primary key, vi_prod_banc int not null);
create table db_biz_pagos.bp_orden (or_orden_banco int primary key, or_servicio varchar(10) not null);
create table db_sat_his.bp_orden_his (or_orden_banco int primary key, or_servicio varchar(10) not null);
create table cobis.bp_total_orden (
    te_orden_banco int not null, te_frm_pagcob char(3) not null, te_servicio varchar(10) not null,
    te_estado_proceso char(1), te_codigo_error int);
create table db_sat_his.bp_total_orden_his (like cobis.bp_total_orden);

-- ---- simulation control ---------------------------------------------------------------------------
-- A row here makes that procedure answer (return_code, o_error) instead of (0, 0).
create table sim.falla (procedimiento varchar(40) primary key, return_code int not null default 0,
                        o_error int not null default 0);
-- Every simulated call, inside the caller's transaction: a rollback removes it, as it would in ASE.
create table sim.llamada (id bigserial primary key, procedimiento varchar(40) not null, argumentos jsonb not null,
                          momento timestamptz not null default now());
create sequence sim.secuencia start 90001;

create function sim.simular(proc text, args jsonb) returns table (return_code int, o_error int)
language sql as $$
    insert into sim.llamada (procedimiento, argumentos) values (proc, args);
    select coalesce(f.return_code, 0), coalesce(f.o_error, 0)
      from (select 1) x left join sim.falla f on f.procedimiento = proc;
$$;

-- ---- the ten procedures, simulated (one jsonb argument: the command record the service sends) -----
create function db_biz_admempresa.sp_con_comision(args jsonb) returns table (return_code int, valor_por_tran numeric)
language sql as $$ select s.return_code, 0.25::numeric from sim.simular('sp_con_comision', args) s $$;

create function db_biz_admempresa.sp_con_confcontable(args jsonb) returns table (return_code int, trn int, causal varchar)
language sql as $$ select s.return_code, 2701, '0150'::varchar from sim.simular('sp_con_confcontable', args) s $$;

create function cobis.sp_ndc_ahcc(args jsonb) returns table (return_code int, transaccion int)
language sql as $$ select s.return_code, nextval('sim.secuencia')::int from sim.simular('sp_ndc_ahcc', args) s $$;

create function cob_virtuales.sp_vi_ndc_automatica(args jsonb) returns table (return_code int, error int, ssn_monet int)
language sql as $$ select s.return_code, s.o_error, nextval('sim.secuencia')::int from sim.simular('sp_vi_ndc_automatica', args) s $$;

create function db_biz_pagos.sp_graba_tran_servicio(args jsonb) returns table (return_code int)
language sql as $$ select s.return_code from sim.simular('sp_graba_tran_servicio', args) s $$;

create function cobis.sp_grb_mov_y_frmpgo(args jsonb) returns table (return_code int)
language sql as $$ select s.return_code from sim.simular('sp_grb_mov_y_frmpgo', args) s $$;

create function cobis.sp_grb_comision(args jsonb) returns table (return_code int, o_error int)
language sql as $$ select s.return_code, s.o_error from sim.simular('sp_grb_comision', args) s $$;

create function cobis.sp_cerror(args jsonb) returns table (return_code int)
language sql as $$ select s.return_code from sim.simular('sp_cerror', args) s $$;

-- ---- fictitious reference data ------------------------------------------------------------------
insert into db_biz_admempresa.ba_tabla values (1, 'ad_concepto_contable');
insert into db_biz_admempresa.ba_catalogo values (1, '77', 'NDCORPEI', 'A');
insert into cob_virtuales.vi_cuenta values ('0000099999', 13);
insert into db_biz_pagos.bp_orden values (123456, 'TRANSCLI'), (123457, 'TRANSCLI'), (123458, 'TRANSCLI');
-- Order headers in state I, ready to move to T (walkthrough 1 and the error scenarios)
insert into cobis.bp_total_orden values
    (123456, 'CUE', 'TRANSCLI', 'I', null),
    (123457, 'CUE', 'TRANSCLI', 'I', null),
    (123458, 'CUE', 'TRANSCLI', 'I', null);
