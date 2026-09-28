-- Local test environment for sp-debcred-empresa on PostgreSQL (profile local-pg). NOT production:
-- production keeps the COBIS procedures and tables in Sybase ASE (INTENT.md). One PostgreSQL schema per
-- COBIS database; the twelve called procedures are SIMULATIONS (their source is not available, brief 7-A5):
-- each one records its call in sim.llamada and answers 0 unless a row in sim.falla says otherwise.
-- Re-runnable: drops and recreates everything. Fictitious data only.

-- no NOTICE on stderr ("schema ... does not exist, skipping"): PowerShell 5.1 would treat it as an error
set client_min_messages = warning;

drop schema if exists sim, cobis, db_biz_pagos, db_sat_his, db_biz_admempresa, cob_virtuales, cob_cuentas,
    cob_ahorros, cob_internet cascade;
create schema sim;
create schema cobis;
create schema db_biz_pagos;
create schema db_sat_his;
create schema db_biz_admempresa;
create schema cob_virtuales;
create schema cob_cuentas;
create schema cob_ahorros;
create schema cob_internet;

-- ---- tables the Java service reads or writes directly ------------------------------------------
-- The catalogue columns are the legacy ones (lines 484-496, 798-1180).
create table db_biz_admempresa.ba_tabla (
    tb_cod_tabla int primary key, tb_nom_tabla varchar(30) not null, tb_est_tabla char(1) not null default 'A');
create table db_biz_admempresa.ba_catalogo (
    ct_cod_tabla int not null, ct_cod_catalogo varchar(10) not null, ct_nom_catalogo varchar(64),
    ct_otro_campo_catalogo varchar(30), ct_est_catalogo char(1) not null default 'A');
create table cob_virtuales.vi_cuenta (vi_cta_banco varchar(24) primary key, vi_prod_banc int not null, vi_cliente int);
create table cob_cuentas.cc_ctacte (cc_cta_banco varchar(24) primary key, cc_cliente int);
create table cob_ahorros.ah_cuenta (ah_cta_banco varchar(24) primary key, ah_cliente int);
create table db_biz_pagos.bp_orden (or_orden_banco int primary key, or_servicio varchar(10) not null, or_ordenante int);
create table db_sat_his.bp_orden_his (like db_biz_pagos.bp_orden);
create table db_biz_pagos.bp_detalle (
    dt_orden_banco int not null, dt_secuencial int not null default 0, dt_referencia_grupo varchar(20),
    dt_nom_cuenta varchar(64), dt_tipo_cta int, dt_numero_cuenta varchar(30), dt_nombre_beneficiario varchar(64));
create table db_sat_his.bp_detalle_his (like db_biz_pagos.bp_detalle);
-- the unqualified bp_detalle of lines 1024-1030 resolves in the home database (brief 7-A8)
create table cobis.bp_detalle (like db_biz_pagos.bp_detalle);
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

-- ---- the called procedures, simulated (one jsonb argument: the command record the service sends) --
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

-- Phase 4 notifiers (B7): the basic notifier writes @o_error (brief 7-A15), the event engine only returns
create function cobis.pa_sat_pnotificacion(args jsonb) returns table (return_code int, o_error int, o_msg varchar)
language sql as $$ select s.return_code, s.o_error, null::varchar from sim.simular('pa_sat_pnotificacion', args) s $$;

create function cob_internet.sp_eventos(args jsonb) returns table (return_code int)
language sql as $$ select s.return_code from sim.simular('sp_eventos', args) s $$;

-- ---- fictitious reference data ------------------------------------------------------------------
insert into db_biz_admempresa.ba_tabla values
    (1, 'ad_concepto_contable', 'A'), (2, 'ad_servicios_sms', 'A'), (3, 'ad_cuentas_bce', 'A'),
    (4, 'ad_notificacion_basica', 'A'), (5, 'ba_bloqueaNotificacionSAT', 'A');
insert into db_biz_admempresa.ba_catalogo values
    (1, '77', null, 'NDCORPEI', 'A'),
    (2, 'TRC-SAT', 'NOTIFICA TRANSCLI', null, 'A'),              -- TRANSCLI on DIR/SAT notifies, SMS service TRC
    (3, 'BCE01', '000000017BANCO DESTINO FICTICIO', null, 'A');  -- beneficiary institution for group 000000017
insert into cob_virtuales.vi_cuenta values ('0000099999', 13, 700099);
insert into cob_cuentas.cc_ctacte values ('0000012345', 700001);
insert into db_biz_pagos.bp_orden values (123456, 'TRANSCLI', 500), (123457, 'TRANSCLI', 500), (123458, 'TRANSCLI', 500);
insert into db_biz_pagos.bp_detalle values
    (123456, 0, '000000017', 'CUENTA DESTINO FICTICIA', 4, '2200334455', 'BENEFICIARIO FICTICIO'),
    (123457, 0, '000000017', 'CUENTA DESTINO FICTICIA', 4, '2200334455', 'BENEFICIARIO FICTICIO'),
    (123458, 0, '000000017', 'CUENTA DESTINO FICTICIA', 4, '2200334455', 'BENEFICIARIO FICTICIO');
-- Order headers in state I, ready to move to T (walkthrough 1 and the error scenarios)
insert into cobis.bp_total_orden values
    (123456, 'CUE', 'TRANSCLI', 'I', null),
    (123457, 'CUE', 'TRANSCLI', 'I', null),
    (123458, 'CUE', 'TRANSCLI', 'I', null);
