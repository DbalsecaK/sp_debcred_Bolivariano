# Data Objects — debcred

## DebitRequest (sp_debcred_empresa input parameter block)
Source: `legacy/debcred/sp_debcred_empresa.sp:4`

| Field | Type | Note |
|---|---|---|
| @s_ssn | int | session sequence; defaulted to 0 at :266 |
| @s_user | varchar(14) |  |
| @s_term | varchar(30) | for SPI it is repurposed as the basic-account concept carrier and blanked (:278-286) |
| @s_srv | varchar(30) |  |
| @s_ofi | smallint |  |
| @i_aplcobis | char(1) | 'S'/'N'; default 'N'; drives batch flag (:176-178) and error mode (:2148) |
| @i_sp_name | varchar(32) | calling SP name; used in SAT block list lookup (:1178) and sp_cerror (:2154) |
| @i_orden | int | bank order number (or_orden_banco) |
| @i_orden_empresa | int | company order number; default debit-note reference (:422,:448,:612) |
| @i_fecha_proceso | datetime |  |
| @i_nem_emp | char(15) | ordering company mnemonic |
| @i_empresa | int | company code; 1295 special-cased (:1276) |
| @i_producto | smallint |  |
| @i_servicio | varchar(10) | service code (TRANSWIFT, IMPADUAN, SPI, TRANSCLI, TARJCRED, COMEXT, TRANSQUICK, TRANSBIMO, PAGOPRV, ROLPAGO, PAGIESS); overwritten with origin service for SPI… |
| @i_frm_pagcob | char(3) | payment form; overwritten by @i_frm_pagcob_spi for TRANSQUICK before commission (:1502) |
| @i_frm_pagcob_spi | char(3) | nullable |
| @i_canal | char(3) | default 'DIR' |
| @i_tipo_afec | char(2) | affectation type, default '10'; '12' = SPI return |
| @i_referencia | varchar(100) |  |
| @i_mon_debito | smallint | currency |
| @i_pais_cta | smallint |  |
| @i_cod_banco_cta | smallint |  |
| @i_tipcta_emp | smallint | account type: 3 current, 4 savings, 9 accounting, 12 virtual |
| @i_numcta_emp | char(10) |  |
| @i_valor_ordenado | money |  |
| @i_valor_debito | money | mutated: replaced by ordered value for TRANSCLI/TARJCRED/COMEXT (:886); zeroed for 1295/SPI ledger debit (:1278) |
| @i_comision | money | bundled commission (single movement); normalized to 0 when it becomes separate (:188-196) |
| @i_valor_comision | money | separate commission |
| @i_tipo_referencia | char(1) | passed as @i_referencia to confcontable and as @i_tipo_horario to sp_grb_comision |
| @i_tarjeta | varchar(24) | nullable card ref |
| @i_ref_prov | varchar(24) | provider reference / detail |
| @i_opcion | char(2) | '01','02','03' select header payment-form set (:1884-2024) |
| @i_tipo_pagcob | char(1) |  |
| @i_localidad_orden | smallint |  |
| @i_tipo_proceso | char(1) |  |
| @i_frm_pagcob_deb | char(3) | defaults to @i_frm_pagcob (:262) |
| @i_valor2_swift | money | second SWIFT commission, default 0 |
| @i_secuencial | int | detail sequence, default 0 |
| @i_cod_swift | varchar(20) | nullable; 'CORPEI' sentinel for IMPADUAN (:464) |
| @i_valor_tarifa | money | REF33; set to @i_valor_comision for TRANSBIMO (:1508) |
| @i_valor_comision_cue | money | REF33 account-tariff breakdown |
| @i_valor_tarifa_efe | money | REF33 cash |
| @i_valor_comision_efe | money | REF33 cash |
| @i_valor_tarifa_che | money | REF33 cheque |
| @i_valor_comision_che | money | REF33 cheque |

Used by: Combined commission is normalized into a separate commission when no separate v…, Only current, savings, virtual or accounting company accounts may be debited, Only current, savings, virtual and accounting account types may be debited, IMPADUAN debits for CORPEI use the NDCORPEI accounting concept with fallback ca…, For TRANSCLI, TARJCRED and COMEXT the recorded debit value is replaced by the o…, Accounting-account debits for company 1295 on SPI are booked as balance instead…, Debit reference text depends on the service, Interbank services debit the ordered value and map beneficiary account type to…, Error reporting mode depends on COBIS invocation flag, TRANSQUICK orders with an SPI payment form use that form for accounting lookup…, TRANSBIMO tariff equals its separate commission, Debit payment form defaults to the order payment form, and TRANSQUICK commissio…, Virtual-account debit is flagged as batch unless invoked from COBIS online, SPI return (affectation 12) uses the company order number as the debit-note ref…, Invocation context defaults: direct channel, affectation 10, batch flag derived…, Cash/cheque/account tariff and commission breakdown is passed through to the se…, Virtual-account and ledger-account debits do not carry the bundled commission o…, Stray result set and debit-value override for TRANSCLI/TARJCRED/COMEXT

## DebitResult (sp_debcred_empresa output parameters and return code)
Source: `legacy/debcred/sp_debcred_empresa.sp:82`

| Field | Type | Note |
|---|---|---|
| @o_error | int output | 0 on success; debit-note error code (:1488,:1698,:1724,:1850) or @w_num_error when not COBIS (:2166) |
| @o_reg_a_proc | int output | declared but never assigned anywhere in the procedure (dead output) |
| return value | int | 0 normally; @o_error when debit note failed (:1490); @w_num_error only when @i_aplcobis='S' (:2158) |
| stray result set | 2-column row (money, money) | 'select @i_comision, @i_valor_ordenado' at :882 emits an unnamed result set for TRANSCLI/TARJCRED/COMEXT; 'select @o_error' at :1726 emits another on commissio… |

Used by: Debit outcome sets process status, rolls back on failure, and the movement is a…, A failed debit note is committed as a recorded failure and returned as an outpu…, Error reporting mode depends on COBIS invocation flag, Stray result set and debit-value override for TRANSCLI/TARJCRED/COMEXT, Second SWIFT commission failure aborts the whole debit; its 'record and return'…

## AccountingResolution (working set: transaction, causal, concept)
Source: `legacy/debcred/sp_debcred_empresa.sp:110`

| Field | Type | Note |
|---|---|---|
| @w_trn | int | accounting transaction code from db_biz_admempresa..sp_con_confcontable @o_trn (:342,:372,:522) |
| @w_causal | char(4) | causal from @o_cau; CORPEI fallback '512' (:528) |
| @w_concepto | varchar(10) | ba_catalogo.ct_cod_catalogo for ad_concepto_contable/NDCORPEI, default '99999' (:480-496) |
| @w_concepto_bas | varchar(5) | '0' default; '91' if virtual account is basic (vi_prod_banc=13); for SPI taken from @s_term (:282,:294,:306) |
| @w_moneda | char(2) | convert(char(2), @i_mon_debito) |
| @w_num_error | int | 120000 no accounting config; 122001 bad account type; 122002 movement write; 122003 commission debit; 122004 no header row |

Used by: Accounting transaction code and causal are resolved from the accounting configu…, IMPADUAN debits for CORPEI use the NDCORPEI accounting concept with fallback ca…, Accounting concept selected by service and basic-account status, TRANSQUICK orders with an SPI payment form use that form for accounting lookup…, No header row updated is an error except for exempted services

## CommissionTariff (working set: per-transaction unit commission and count)
Source: `legacy/debcred/sp_debcred_empresa.sp:144`

| Field | Type | Note |
|---|---|---|
| @w_val_por_tran | money | db_biz_admempresa..sp_con_comision @o_valor_por_tran (alcance 'E', tipo '01', secuencia 1); 0 on error (:204-226) |
| @w_comision_und | money | unit commission; ends equal to @w_val_por_tran (:248) |
| @w_cantidad | int | commission / per-transaction value (integer truncation) (:244); passed as @i_nchq to sp_ndc_ahcc (:726) |
| @w_tran_ncnd | int | monetary sequence returned by sp_ndc_ahcc @o_transaccion / sp_vi_ndc_automatica @o_ssn_monet; forwarded to sp_grb_mov_y_frmpgo @i_tran_ncnd (:1460) |
| @w_valor_comision | money | notification cost = separate commission (or bundled for TRANSCLI/TARJCRED/COMEXT) + @i_valor2_swift (:862,:884,:980) |

Used by: Unit commission and transaction count derived from company per-transaction tari…, Notification cost equals separate commission plus the second SWIFT value, Separate commission is debited as its own movement; failure rolls back the whol…

## DebitNoteContext (working set: reference text, savepoint, batch, status)
Source: `legacy/debcred/sp_debcred_empresa.sp:114`

| Field | Type | Note |
|---|---|---|
| @w_cadena | varchar(30) | debit-note reference: company order number by default (:422); 'COD:'+@i_cod_swift for TRANSWIFT (:438); company order number for SPI return (:612) |
| @w_batch | bit | 1 when @i_aplcobis='N', 0 when 'S' (:176-178); sent to sp_vi_ndc_automatica @i_batch |
| @w_savepoint | varchar(32) | 'sp_debito_empresa' (:406); forwarded to sp_grb_comision @i_savepoint |
| @w_cod_errord | int | debit-note error code from the account-type-specific debit SP; 122001 for unsupported type (:1316) |
| @w_sts_proc | char(1) | 'P' processed, 'X' failed (:1332-1340,:1620) |
| @w_serv_origen | varchar(10) | or_servicio of original order for SPI return (:1352-1368) |
| @w_act_totord | char(1) | 'S' default; 'N' for SPI return to skip header-row-count error (:258,:1378,:2080) |
| @w_empresa_str | varchar(10) | company as string; passed as @i_tipo_chequera to sp_graba_tran_servicio (:1274,:1296) |
| @v_saldo_ap | money | balance sent instead of value for company 1295 on SPI (:1278) |
| @w_nombre_cuenta | varchar(64) | declared, never assigned; sent as null @i_nombre_cuenta/@i_nombre_beneficiario to movement and commission SPs |

Used by: Debit reference text depends on the service, Debit outcome sets process status, rolls back on failure, and the movement is a…, SPI returns are posted under the original order's service and skip header update, SPI return (affectation 12) uses the company order number as the debit-note ref…, Virtual-account debit is flagged as batch unless invoked from COBIS online, Accounting-account debits for company 1295 on SPI are booked as balance instead…, A failed debit note is committed as a recorded failure and returned as an outpu…, Second SWIFT commission is debited as affectation type 16

## NotificationContext (working set for SMS / event notification)
Source: `legacy/debcred/sp_debcred_empresa.sp:752`

| Field | Type | Note |
|---|---|---|
| @w_canal_sms | char(3) | DIR/SAT -> 'SAT'; BNK -> 'IBK'; else unchanged (:760-794) |
| @w_desc_canal | varchar(16) | 'SAT', '24OnLine', 'Ventanilla' |
| @w_serv_sms | varchar(10) | prefix of ba_catalogo.ct_cod_catalogo before '-' from ad_servicios_sms matching service and channel suffix (:798-810) |
| @w_proddeb | varchar(3) | 'CTE' (3), 'AHO' (4), 'VIR'/'AHO' (12, AHO when vi_prod_banc=13) (:1102-1154) |
| @w_prodcre | varchar(3) | from beneficiary dt_tipo_cta: 3->CTE, 4->AHO, 9->CON, 8->ESP, 0->null (:944-974) |
| @w_tip_cta | smallint | beneficiary account type from bp_detalle.dt_tipo_cta |
| @w_ente | int | debtor client: cc_cliente / ah_cliente / vi_cliente |
| @w_emp_inst | varchar(32) | TRANSWIFT: dt_referencia_grupo; interbank: substring(ct_nom_catalogo,10,32) from ad_cuentas_bce |
| @w_cta_cre | varchar(30) | credit account: dt_nom_cuenta (TRANSWIFT) or dt_numero_cuenta |
| @w_valor_sms | varchar(11) | convert of @i_valor_debito |
| @w_costo_sms | varchar(11) | convert of @w_valor_comision |
| @w_serv_pago_dir | varchar(4) | ct_otro_campo_catalogo from ad_notificacion_basica; 'B' -> basic notifier; default 'OTRO' (:998-1014) |
| @w_nombre_cuenta_d | varchar(64) | dt_nombre_beneficiario (:1026) |
| @w_dt_referencia_grupo | varchar(20) | dt_referencia_grupo passed as @i_direccion_transf (:1064) |
| @w_bloqNoti | char(1) | 'S' when ba_bloqueaNotificacionSAT has service-'-'-serv_sms with @i_sp_name (:1162-1180) |
| @v_notiBCE | char(1) | 'S' when canal 'BCE' and ROLPAGO and null debit account (:1186-1196); note @w_canal_sms is never set to 'BCE' by mapping, so only reachable if @i_canal='BCE' |
| @w_msg | varchar(64) | pa_sat_pnotificacion @o_msg |

Used by: Successful debits trigger a customer notification event with channel and produc…, Interbank services debit the ordered value and map beneficiary account type to…, Notification channel and SMS service resolved from the order channel and catalo…, Notification is routed to basic-account notifier or event engine by service cla…, Event notification identifies debtor product and client, treating basic virtual…, Generic notification is suppressed by the SAT block list or for BCE payroll wit…, Notification cost equals separate commission plus the second SWIFT value, SWIFT notifications read the beneficiary institution and credit account from th…

## bp_orden / db_sat_his..bp_orden_his (payment order header)
Source: `legacy/debcred/sp_debcred_empresa.sp:830`

| Field | Type | Note |
|---|---|---|
| or_orden_banco | int (inferred from @i_orden) | key |
| or_ordenante | int (inferred from @i_empresa) |  |
| or_servicio | varchar(10) (inferred) | read at :1352/:1364 for SPI returns |
| or_fch_inicio_pagcob | datetime (inferred) | only in commented-out ROLPAGO update (:2098-2124) |

Used by: SPI returns are posted under the original order's service and skip header update, SWIFT notifications read the beneficiary institution and credit account from th…, Order data is read from the live payments database first and from the SAT histo…, Payroll (ROLPAGO) payment start date is no longer stamped on the order

## bp_detalle / db_sat_his..bp_detalle_his (payment order detail / beneficiary)
Source: `legacy/debcred/sp_debcred_empresa.sp:826`

| Field | Type | Note |
|---|---|---|
| dt_orden_banco | int (inferred) | FK to order |
| dt_secuencial | int (inferred) | matched to @i_secuencial for TRANSWIFT (:836) |
| dt_referencia_grupo | varchar(20) (inferred) | institution ref; joined to ad_cuentas_bce ct_nom_catalogo(1,9) (:902) |
| dt_nom_cuenta | varchar (inferred) | first 30 chars used as credit account for TRANSWIFT (:828) |
| dt_tipo_cta | smallint (inferred) | beneficiary account type (:894) |
| dt_numero_cuenta | varchar(30) (inferred) | credit account (:896) |
| dt_nombre_beneficiario | varchar(64) (inferred) | (:1026) read unqualified from bp_detalle in current DB |

Used by: Interbank services debit the ordered value and map beneficiary account type to…, SWIFT notifications read the beneficiary institution and credit account from th…, Notification is routed to basic-account notifier or event engine by service cla…, Order data is read from the live payments database first and from the SAT histo…

## bp_total_orden / db_sat_his..bp_total_orden_his (order header totals by payment form)
Source: `legacy/debcred/sp_debcred_empresa.sp:1886`

| Field | Type | Note |
|---|---|---|
| te_orden_banco | int (inferred) |  |
| te_frm_pagcob | char(3) (inferred) | filtered by @i_frm_pagcob_deb (opt 01), CUE/EFE/CHL (02), CUE/EFE/CHE (03), COB/TRC/CTB/CPD/TPD (non-direct channels) |
| te_servicio | varchar(10) (inferred) |  |
| te_estado_proceso | char(1) (inferred) | 'I' initial (null treated as 'I') -> 'T' transition |
| te_codigo_error | int (inferred) | set to @w_cod_errord |

Used by: Order header transitions from Initial to In-Transition by channel, option and p…, No header row updated is an error except for exempted services, Order header state update falls back to the historical order table and treats a…, SPI returns are posted under the original order's service and skip header update

## db_biz_admempresa..ba_tabla / ba_catalogo (parameter catalogue)
Source: `legacy/debcred/sp_debcred_empresa.sp:484`

| Field | Type | Note |
|---|---|---|
| tb_cod_tabla | int (inferred) |  |
| tb_nom_tabla | varchar (inferred) | tables used: ad_concepto_contable (:492), ad_servicios_sms (:804), ad_cuentas_bce (:904), ad_notificacion_basica (:1002), ba_bloqueaNotificacionSAT (:1174), ad… |
| tb_est_tabla | char(1) (inferred) | 'A' |
| ct_cod_tabla | int (inferred) |  |
| ct_cod_catalogo | varchar (inferred) | code; for ad_servicios_sms holds 'SERV-CHN' pattern |
| ct_nom_catalogo | varchar (inferred) | for ad_cuentas_bce holds 9-char ref + institution name |
| ct_otro_campo_catalogo | varchar (inferred) | 'NDCORPEI' marker; 'B'/'OTRO' notifier class; blocking SP name |
| ct_est_catalogo | char(1) (inferred) | 'A' |

Used by: IMPADUAN debits for CORPEI use the NDCORPEI accounting concept with fallback ca…, Notification channel and SMS service resolved from the order channel and catalo…, Interbank services debit the ordered value and map beneficiary account type to…, Notification is routed to basic-account notifier or event engine by service cla…, Generic notification is suppressed by the SAT block list or for BCE payroll wit…

## Debtor account master tables (cob_cuentas..cc_ctacte, cob_ahorros..ah_cuenta, cob_virtuales..vi_cue…
Source: `legacy/debcred/sp_debcred_empresa.sp:1110`

| Field | Type | Note |
|---|---|---|
| cc_cta_banco / cc_cliente | char(10) / int (inferred) | current account -> client (:1110-1114) |
| ah_cta_banco / ah_cliente | char(10) / int (inferred) | savings account -> client (:1128-1132) |
| vi_cta_banco / vi_cliente / vi_prod_banc | char(10) / int / int (inferred) | virtual account; vi_prod_banc=13 denotes basic account (:300-306,:1146-1152) |

Used by: Accounting concept selected by service and basic-account status, Event notification identifies debtor product and client, treating basic virtual…

## DebitNotePayload -> sp_ndc_ahcc (current/savings debit note)
Source: `legacy/debcred/sp_debcred_empresa.sp:676`

| Field | Type | Note |
|---|---|---|
| @t_trn / @i_causal | int / char(4) | from AccountingResolution |
| @i_cuenta / @i_tipo_cuenta | char(10) / smallint |  |
| @i_valor / @i_mon | money / smallint |  |
| @i_tarjeta / @i_servicio / @i_detalle | varchar(24) / varchar(10) / varchar(24) |  |
| @i_ref | varchar(30) | @w_cadena |
| @i_tcomision | money | bundled @i_comision |
| @i_frm_pagcob / @i_alterno_dos | char(3) / char(3) | @i_frm_pagcob_spi and @i_frm_pagcob respectively |
| @i_alt / @i_orden_banco / @i_secuencial | int / int / int | @i_orden, @i_orden, 0 |
| @i_nchq / @i_solca | int / money | transaction count and unit commission (repurposed parameters) |
| @o_transaccion | int output | -> @w_tran_ncnd |

Used by: Only current, savings, virtual or accounting company accounts may be debited, Unit commission and transaction count derived from company per-transaction tari…, Debit reference text depends on the service

## VirtualDebitPayload -> cob_virtuales..sp_vi_ndc_automatica
Source: `legacy/debcred/sp_debcred_empresa.sp:628`

| Field | Type | Note |
|---|---|---|
| @t_trn / @i_cau | int / char(4) |  |
| @i_cta / @i_val / @i_mon | char(10) / money / smallint |  |
| @i_empresa | int |  |
| @i_canal | literal 'SAT' |  |
| @i_verf_estado_cta | literal 'S' |  |
| @i_batch | bit | @w_batch |
| @i_ref / @i_alt | varchar(30) / int | @w_cadena, @i_orden |
| @o_error / @o_ssn_monet | int out / int out | @o_error overwritten by return value at :666 |

Used by: Only current, savings, virtual and accounting account types may be debited, Virtual-account debit is flagged as batch unless invoked from COBIS online, Virtual-account and ledger-account debits do not carry the bundled commission o…

## LedgerDebitPayload -> db_biz_pagos..sp_graba_tran_servicio (accounting account type 9)
Source: `legacy/debcred/sp_debcred_empresa.sp:1284`

| Field | Type | Note |
|---|---|---|
| @t_trn / @i_causa | int / char(4) |  |
| @i_fecha | datetime |  |
| @i_referencia | varchar(24) | @i_ref_prov |
| @i_cta_banco / @i_oficina / @i_oficina_cta | char(10) / smallint / 0 |  |
| @i_indicador | literal 1 |  |
| @i_moneda | smallint |  |
| @i_saldo / @i_valor | money / money | saldo populated only for company 1295 + SPI, in which case valor=0 |
| @i_tipo_chequera | varchar(10) | company code as string (repurposed) |
| @i_orden_banco / @i_secuencial | int / 0 |  |

Used by: Only current, savings, virtual or accounting company accounts may be debited, Accounting-account debits for company 1295 on SPI are booked as balance instead…, Virtual-account and ledger-account debits do not carry the bundled commission o…

## MovementRecord -> sp_grb_mov_y_frmpgo (movement + payment form)
Source: `legacy/debcred/sp_debcred_empresa.sp:1394`

| Field | Type | Note |
|---|---|---|
| @t_trn / @i_cau | int / char(4) |  |
| @i_tipo_proceso / @i_empresa / @i_producto / @i_orden_banco / @i_canal | char(1) / int / smallint / int / char(3) |  |
| @i_est_proceso | char(1) | 'P' or 'X' |
| @i_cod_error | int | @w_cod_errord |
| @i_frm_pagcob / @i_moneda_orden | char(3) / smallint |  |
| @i_valor_mov | money | @i_valor_debito on main path (:1424); @i_valor_comision on commission-failure path (:1658) |
| @i_tipo_afectacion | literal '1' |  |
| @i_fch_contab | datetime |  |
| @i_referencia | varchar(100) | @i_referencia or literal 'COBRO DE COMISION' (:1664) |
| @i_secuencial | literal 0 |  |
| @i_servicio / @i_tipo_pagcob / @i_pais_cta / @i_cod_banco_cta / @i_tipo_cta / @… | varchar(10) / char(1) / smallint / smallint / smallint / ch… |  |
| @i_valor_ordenado / @i_nem_ordenante / @i_localidad_pagcob / @i_orden_empresa | money / char(15) / smallint / int |  |
| @i_nombre_cuenta / @i_nombre_beneficiario | varchar(64) | always null (@w_nombre_cuenta never assigned) |
| @i_valor_comision | money | bundled @i_comision |
| @i_tran_ncnd | int | @w_tran_ncnd |

Used by: Debit outcome sets process status, rolls back on failure, and the movement is a…, A failed debit note is committed as a recorded failure and returned as an outpu…, Separate commission is debited as its own movement; failure rolls back the whol…, SPI returns are posted under the original order's service and skip header update

## CommissionDebitPayload -> sp_grb_comision (separate commission and second SWIFT commission)
Source: `legacy/debcred/sp_debcred_empresa.sp:1518`

| Field | Type | Note |
|---|---|---|
| @i_canal_comision / @i_empresa / @i_producto / @i_servicio / @i_tipo_proceso /… | char(3) / int / smallint / varchar(10) / char(1) / int |  |
| @i_valor_comision | money | @i_valor_comision (:1548) or @i_valor2_swift (:1776) |
| @i_cadena / @i_tarjeta / @i_detalle_ref | varchar(30) / varchar(24) / varchar(24) |  |
| @i_frm_pagcob / @i_moneda / @i_tipcta_emp / @i_numcta_emp | char(3) / smallint / smallint / char(10) | @i_frm_pagcob may have been replaced by SPI form for TRANSQUICK |
| @i_referencia | varchar | literal 'COBRO DE COMISION' for separate commission (:1562); @i_referencia for second SWIFT (:1790) |
| @i_tipo_pagcob / @i_pais_cta / @i_cod_banco_cta / @i_nem_ordenante / @i_localid… | char(1) / smallint / smallint / char(15) / smallint / int |  |
| @i_nombre_cuenta / @i_nombre_beneficiario | varchar(64) | always null |
| @i_tipo_horario | char(1) | @i_tipo_referencia |
| @i_tipoafec | char(2) | only on second SWIFT call, literal '16' (:1812); omitted on the first call |
| @i_savepoint / @i_secuencial | varchar(32) / 0 |  |
| @i_valor_tarifa, @i_valor_comision_cue, @i_valor_tarifa_efe, @i_valor_comision_… | money | REF33 breakdown passed only on the first call (:1590-1600); second call passes only @i_valor_tarifa=@i_valor2_swift (:1820) |
| @o_error | int output | -> @w_cod_errord |

Used by: Separate commission is debited as its own movement; failure rolls back the whol…, Second SWIFT commission is debited as affectation type 16, TRANSBIMO tariff equals its separate commission, TRANSQUICK orders with an SPI payment form use that form for accounting lookup…, Cash/cheque/account tariff and commission breakdown is passed through to the se…, Second SWIFT commission failure aborts the whole debit; its 'record and return'…

## EventNotificationPayload -> cob_internet..sp_eventos
Source: `legacy/debcred/sp_debcred_empresa.sp:1210`

| Field | Type | Note |
|---|---|---|
| @i_operacion | literal 'I' |  |
| @i_canal / @i_servicio | char(3) / varchar(10) | @w_canal_sms, @w_serv_sms |
| @i_producto / @i_cuenta / @i_cta_deb | smallint / char(10) / char(10) | @i_tipcta_emp, @i_numcta_emp x2 |
| @i_valor / @i_costo | varchar(11) / varchar(11) | string-converted amounts |
| @i_prod_deb / @i_prod_cre / @i_cta_cre | varchar(3) / varchar(3) / varchar(30) |  |
| @i_cliente | int | @w_ente |
| @i_empresa / @i_desc_canal | varchar(32) / varchar(16) |  |

Used by: Successful debits trigger a customer notification event with channel and produc…, Event notification identifies debtor product and client, treating basic virtual…, Generic notification is suppressed by the SAT block list or for BCE payroll wit…, Notification cost equals separate commission plus the second SWIFT value

## BasicNotificationPayload -> pa_sat_pnotificacion
Source: `legacy/debcred/sp_debcred_empresa.sp:1052`

| Field | Type | Note |
|---|---|---|
| @i_canal / @i_servicio / @i_orden_banco / @i_secuencial | char(3) / varchar(10) / int / int |  |
| @i_ctadebito / @i_tipctadeb | char(10) / smallint |  |
| @i_direccion_transf | varchar(20) | dt_referencia_grupo |
| @i_valor / @i_comision | money / money | @i_valor_ordenado, @i_valor_comision |
| @i_nombrecred / @i_ctacred / @i_prod_cre / @i_empresa | varchar(64) / varchar(30) / varchar(3) / varchar(32) |  |
| @o_error / @o_msg | int output / varchar(64) output | @o_error written directly into the procedure output parameter (:1080) |

Used by: Notification is routed to basic-account notifier or event engine by service cla…, SWIFT notifications read the beneficiary institution and credit account from th…
