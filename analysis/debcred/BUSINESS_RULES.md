# Business Rules — debcred

At extraction: 39 confirmed rules (6 P0); later steps may add or correct rules below. Each citation was checked by a second agent that read the cited lines; 0 candidate rules were refuted and left out.

| ID | Name | Category | Priority | Source | Confidence |
|---|---|---|---|---|---|
| RULE-001 | TRANSBIMO tariff equals its separate commission | Calculation | P1 | `sp_debcred_empresa.sp:1506-1508` | Medium |
| RULE-002 | Unit commission and transaction count derived from company per-transaction tari… | Calculation | P1 | `sp_debcred_empresa.sp:204-248` | Medium |
| RULE-003 | For TRANSCLI, TARJCRED and COMEXT the recorded debit value is replaced by the o… | Calculation | P1 | `sp_debcred_empresa.sp:878-886` | Medium |
| RULE-004 | Interbank services debit the ordered value and map beneficiary account type to… | Calculation | P1 | `sp_debcred_empresa.sp:878-976` | Medium |
| RULE-005 | Event notification identifies debtor product and client, treating basic virtual… | Calculation | P2 | `sp_debcred_empresa.sp:1102-1154` | High |
| RULE-006 | Notification cost equals separate commission plus the second SWIFT value | Calculation | P2 | `sp_debcred_empresa.sp:980-986` | High |
| RULE-007 | Accounting transaction code and causal are resolved from the accounting configu… | Validation | P0 | `sp_debcred_empresa.sp:278-394` | High |
| RULE-008 | Only current, savings, virtual or accounting company accounts may be debited | Validation | P0 | `sp_debcred_empresa.sp:414-416` | High |
| RULE-009 | Only current, savings, virtual and accounting account types may be debited | Validation | P0 | `sp_debcred_empresa.sp:414-740` | High |
| RULE-010 | No header row updated is an error except for exempted services | Validation | P1 | `sp_debcred_empresa.sp:2076-2090` | High |
| RULE-011 | Stray result set and debit-value override for TRANSCLI/TARJCRED/COMEXT | Validation | P2 | `sp_debcred_empresa.sp:882-886` | High |
| RULE-012 | Debit outcome sets process status, rolls back on failure, and the movement is a… | Lifecycle | P0 | `sp_debcred_empresa.sp:1332-1492` | High |
| RULE-013 | Separate commission is debited as its own movement; failure rolls back the whol… | Lifecycle | P0 | `sp_debcred_empresa.sp:1500-1732` | Medium |
| RULE-014 | Order header transitions from Initial to In-Transition by channel, option and p… | Lifecycle | P0 | `sp_debcred_empresa.sp:1876-2090` | High |
| RULE-015 | A failed debit note is committed as a recorded failure and returned as an outpu… | Lifecycle | P1 | `sp_debcred_empresa.sp:1480-1492` | High |
| RULE-016 | Second SWIFT commission is debited as affectation type 16 | Lifecycle | P1 | `sp_debcred_empresa.sp:1742-1856` | High |
| RULE-017 | Second SWIFT commission failure aborts the whole debit; its 'record and return'… | Lifecycle | P1 | `sp_debcred_empresa.sp:1824-1854` | High |
| RULE-018 | Order header state update falls back to the historical order table and treats a… | Lifecycle | P1 | `sp_debcred_empresa.sp:1898-1924` | High |
| RULE-019 | SWIFT notifications read the beneficiary institution and credit account from th… | Lifecycle | P1 | `sp_debcred_empresa.sp:820-874` | High |
| RULE-020 | Payroll (ROLPAGO) payment start date is no longer stamped on the order | Lifecycle | P2 | `sp_debcred_empresa.sp:2094-2124` | Medium |
| RULE-021 | Generic notification is suppressed by the SAT block list or for BCE payroll wit… | Policy | P1 | `sp_debcred_empresa.sp:1162-1208` | Medium |
| RULE-022 | Accounting-account debits for company 1295 on SPI are booked as balance instead… | Policy | P1 | `sp_debcred_empresa.sp:1264-1300` | Low |
| RULE-023 | SPI returns are posted under the original order's service and skip header update | Policy | P1 | `sp_debcred_empresa.sp:1348-1380` | High |
| RULE-024 | Order data is read from the live payments database first and from the SAT histo… | Policy | P1 | `sp_debcred_empresa.sp:1352-1370` | High |
| RULE-025 | Debit payment form defaults to the order payment form, and TRANSQUICK commissio… | Policy | P1 | `sp_debcred_empresa.sp:1500-1502` | Medium |
| RULE-026 | Virtual-account debit is flagged as batch unless invoked from COBIS online | Policy | P1 | `sp_debcred_empresa.sp:174-180` | Medium |
| RULE-027 | Combined commission is normalized into a separate commission when no separate v… | Policy | P1 | `sp_debcred_empresa.sp:188-196` | High |
| RULE-028 | Error reporting mode depends on COBIS invocation flag | Policy | P1 | `sp_debcred_empresa.sp:2140-2170` | High |
| RULE-029 | Accounting concept selected by service and basic-account status | Policy | P1 | `sp_debcred_empresa.sp:278-312` | Medium |
| RULE-030 | TRANSQUICK orders with an SPI payment form use that form for accounting lookup… | Policy | P1 | `sp_debcred_empresa.sp:320-346` | High |
| RULE-031 | IMPADUAN debits for CORPEI use the NDCORPEI accounting concept with fallback ca… | Policy | P1 | `sp_debcred_empresa.sp:456-550` | High |
| RULE-032 | SPI return (affectation 12) uses the company order number as the debit-note ref… | Policy | P1 | `sp_debcred_empresa.sp:558-614` | High |
| RULE-033 | Virtual-account and ledger-account debits do not carry the bundled commission o… | Policy | P1 | `sp_debcred_empresa.sp:624-740` | Medium |
| RULE-034 | Successful debits trigger a customer notification event with channel and produc… | Policy | P1 | `sp_debcred_empresa.sp:746-1254` | Medium |
| RULE-035 | Notification channel and SMS service resolved from the order channel and catalo… | Policy | P1 | `sp_debcred_empresa.sp:760-814` | High |
| RULE-036 | Notification is routed to basic-account notifier or event engine by service cla… | Policy | P1 | `sp_debcred_empresa.sp:994-1094` | High |
| RULE-037 | Invocation context defaults: direct channel, affectation 10, batch flag derived… | Policy | P2 | `sp_debcred_empresa.sp:14-38` | Medium |
| RULE-038 | Debit reference text depends on the service | Policy | P2 | `sp_debcred_empresa.sp:422-450` | High |
| RULE-039 | Cash/cheque/account tariff and commission breakdown is passed through to the se… | Policy | P2 | `sp_debcred_empresa.sp:86-96` | Medium |

## Calculation

### RULE-001: TRANSBIMO tariff equals its separate commission
**Category:** Calculation
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:1506-1508`
**Plain English:** For the TRANSBIMO service the tariff value sent to the commission routine is forced to the separate commission amount, ignoring any tariff supplied by the caller.
**Specification:**
  Given Service TRANSBIMO with @i_valor_comision = 2.50 and @i_valor_tarifa = 1.00
  When  The separate commission is about to be charged
  Then  sp_grb_comision receives @i_valor_tarifa = 2.50 along with the per-form tariff/commission pairs (cue/efe/che) unchanged
**Parameters:** Service = 'TRANSBIMO'; REF33 per-payment-form tariff/commission inputs: tarifa, comision_cue, tarifa_efe, comision_efe, tarifa_che, comision_che are passed through untouched
**Edge cases handled:** Other services pass the caller's tariff as-is (may be null)
**Confidence:** Medium — Why does TRANSBIMO override the tariff with the commission value, and should the per-form (cue/efe/che) tariff pairs also be derived for it?

### RULE-002: Unit commission and transaction count derived from company per-transaction tariff
**Category:** Calculation
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:204-248`
**Also cited:** sp_debcred_empresa.sp:204-248; sp_debcred_empresa.sp:204-248
**Plain English:** The company's configured per-transaction commission (scope 'E', type '01', sequence 1) is looked up; the commission charged is divided by that unit price to obtain the number of transactions being charged, and the unit price is passed as the unit commission on the debit note.
**Specification:**
  Given Company 100, product 1, service TRANSCLI, channel DIR with configured per-transaction commission 0.50 and a total commission of 2.50
  When  the debit note is prepared
  Then  @w_cantidad = 5 (2.50 / 0.50) and @w_comision_und = 0.50 are passed to sp_ndc_ahcc as @i_nchq and @i_solca
**Parameters:** sp_con_comision inputs: @i_cod_alcance='E', @i_tip_comision='01', @i_secuencia=1; on lookup failure tariff treated as 0
**Edge cases handled:** If the tariff lookup fails or returns 0, quantity stays 0 and unit commission becomes 0 regardless of the commission actually charged; @i_valor_comision takes precedence over @i_comision when both > 0 as the numerator; @w_cantidad is int: 2.75 / 0.50 = 5.5 is truncated to 5
**Suspected defect:** Line 248 unconditionally sets @w_comision_und = @w_val_por_tran, so when no tariff is configured the debit note receives unit commission 0 even though a commission was charged. | Line 248 sets the unit commission to the configured fee even when the lookup failed (fee = 0), discarding the caller's commission value for the debit-note detail; integer division at 244 silently truncates.
**Confidence:** Medium — Is truncating the transaction count (int) intended when the commission is not an exact multiple of the per-transaction tariff, and should the unit commission really be reported as 0 when no tariff is configured? / Is the unconditional overwrite @w_comision_und = @w_val_por_tran at line 248 intentional (unit commission always equals the configured fee, even when the caller-supplied commission diff…

### RULE-003: For TRANSCLI, TARJCRED and COMEXT the recorded debit value is replaced by the ordered value
**Category:** Calculation
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:878-886`
**Plain English:** For client transfers, credit-card and foreign-trade services, after the debit note has already been issued the working debit value is overwritten with the ordered value and the commission used for the notification is the bundled commission.
**Specification:**
  Given Service TRANSCLI, @i_valor_debito = 100.50 (already debited) and @i_valor_ordenado = 100.00, @i_comision = 0.50
  When  The notification block runs
  Then  @i_valor_debito becomes 100.00 and @w_valor_comision = 0.50; the subsequent movement record (sp_grb_mov_y_frmpgo @i_valor_mov) and notification value use 100.00
**Parameters:** Services: 'TRANSCLI', 'TARJCRED', 'COMEXT'. Only executes when an SMS service was found (@w_serv_sms not null) and the debit succeeded.
**Edge cases handled:** If no ad_servicios_sms entry exists the override does not run, so the movement is recorded with the original debit value - behavior differs by notification configuration; Line 882 'select @i_comision, @i_valor_ordenado' returns a stray result set to the caller (debug leftover)
**Suspected defect:** Debit value used for the accounting movement depends on whether an SMS catalog row exists; the debug 'select' at line 882 emits an unexpected result set.
**Confidence:** Medium — Is it intended that the recorded movement value for TRANSCLI/TARJCRED/COMEXT equals the ordered value (excluding commission) only when an SMS configuration exists, and should the stray result set at line 882 be removed?

### RULE-004: Interbank services debit the ordered value and map beneficiary account type to credit product
**Category:** Calculation
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:878-976`
**Plain English:** For TRANSCLI, TARJCRED and COMEXT the debit value is replaced by the ordered value, the commission is the combined commission field, and the beneficiary's account type is translated to a credit product code for notification.
**Specification:**
  Given Service TRANSCLI, ordered value 250.00, debit value 251.20, beneficiary account type 4
  When  notification data is prepared
  Then  @i_valor_debito = 250.00, @w_valor_comision = @i_comision, credit product 'AHO', beneficiary bank taken from catalog ad_cuentas_bce
**Parameters:** Account type to product: 0 -> null, 3 -> CTE, 4 -> AHO, 8 -> ESP, 9 -> CON; services TRANSCLI, TARJCRED, COMEXT
**Edge cases handled:** Because @i_valor_debito is overwritten here, the movement recorded at line 1424 uses the ordered value rather than the original debit value for these services; Types 8/9 mapping applies to any service (outside the if-block)
**Confidence:** Medium — Is it intended that for TRANSCLI/TARJCRED/COMEXT the recorded movement value becomes the ordered value (excluding commission) rather than the debited value passed in?

### RULE-005: Event notification identifies debtor product and client, treating basic virtual accounts as savings
**Category:** Calculation
**Priority:** P2
**Source:** `sp_debcred_empresa.sp:1102-1154`
**Also cited:** sp_debcred_empresa.sp:1102-1154
**Plain English:** For the event-engine notification the debtor product is CTE for current, AHO for savings, and for virtual accounts VIR unless the account is a basic account (product 13) which is reported as AHO; the client id is read from the respective account master.
**Specification:**
  Given Debit from virtual account type 12 with vi_prod_banc = 13
  When  The sp_eventos notification is built
  Then  @i_prod_deb = 'AHO' and @i_cliente = vi_cliente of that account
**Parameters:** Type 3 -> 'CTE' (cc_ctacte); type 4 -> 'AHO' (ah_cuenta); type 12 -> 'VIR' or 'AHO' when vi_prod_banc = 13; credit product from beneficiary type: 3 CTE, 4 AHO, 8 ESP, 9 CON, 0 none (lines 944-974)
**Edge cases handled:** Credit product mapping for types 8/9 is evaluated for every service, but @w_tip_cta is only populated in the TRANSCLI/TARJCRED/COMEXT branch
**Confidence:** High

### RULE-006: Notification cost equals separate commission plus the second SWIFT value
**Category:** Calculation
**Priority:** P2
**Source:** `sp_debcred_empresa.sp:980-986`
**Plain English:** The cost reported in the customer notification is the commission attributable to the order plus the second SWIFT commission, and the notified amount is the debit value.
**Specification:**
  Given A TRANSWIFT order with separate commission 12.00 and second SWIFT value 5.00, debit value 1,000.00
  When  The notification amounts are prepared
  Then  Notified value = 1000.00 and notified cost = 17.00
**Parameters:** cost = isnull(w_valor_comision,0) + isnull(i_valor2_swift,0); for TRANSWIFT w_valor_comision = i_valor_comision, for TRANSCLI/TARJCRED/COMEXT it is i_comision, otherwise null (0)
**Edge cases handled:** For services outside TRANSWIFT/TRANSCLI/TARJCRED/COMEXT the commission component is 0 and only valor2_swift contributes; Values are converted to varchar(11): amounts of 100,000,000.00 or more would be truncated
**Confidence:** High

## Validation

### RULE-007: Accounting transaction code and causal are resolved from the accounting configuration; missing configuration aborts the…
**Category:** Validation
**Priority:** P0
**Source:** `sp_debcred_empresa.sp:278-394`
**Also cited:** sp_debcred_empresa.sp:350-394; sp_debcred_empresa.sp:278-394; sp_debcred_empresa.sp:278-394
**Plain English:** Before any money moves, the transaction code and causal must be found in the accounting configuration for the product/service/affectation type/payment form/channel/account type/currency/reference type/company; the 'basic account' concept modifies the lookup, and if no configuration exists the whole operation fails with error 120000.
**Specification:**
  Given Service TRANSCLI, product 1, affectation '10', payment form 'CUE', channel 'DIR', company account type 12 that is a basic account (vi_prod_banc = 13)
  When  sp_con_confcontable is called
  Then  Concept '91' is passed, and the returned trn/causal are used for the debit; if the call returns > 0 the procedure rolls back and raises 120000 ('No existe configuracion contable')
**Parameters:** Concept default '0'; basic account concept '91' when @i_tipcta_emp = 12 and cob_virtuales..vi_cuenta.vi_prod_banc = 13; for service 'SPI' the concept is taken from @s_term (which is then blanked); for 'TRANSQUICK' with @i_frm_pagcob_spi not null the SPI payment form is used instead of @i_frm_pagcob and no concept is passed. Error code 120000.
**Edge cases handled:** SPI service: @s_term is reused as the concept carrier and reset to ' ' (ref41); TRANSQUICK with SPI payment form: lookup uses @i_frm_pagcob_spi and omits @i_concepto; @@error <> 0 after call also triggers 120000
**Confidence:** High

### RULE-008: Only current, savings, virtual or accounting company accounts may be debited
**Category:** Validation
**Priority:** P0
**Source:** `sp_debcred_empresa.sp:414-416`
**Also cited:** sp_debcred_empresa.sp:1308-1322
**Plain English:** The company account being debited must be a current account (3), savings (4), virtual account (12) or accounting account (9); any other type is rejected with error 122001 and no debit is attempted.
**Specification:**
  Given A debit order whose company account type is 7
  When  The debit note step is reached
  Then  No debit note is issued; @w_cod_errord = 122001 ('El tipo de cuenta debe ser Corriente (3), Ahorros(4) o Contable (9)'), the movement is still recorded with status 'X' and the procedure returns 122001
**Parameters:** Allowed types: 3 (Corriente), 4 (Ahorros), 12 (Virtual, ref31), 9 (Contable). Error 122001. Rejection branch at lines 1308-1318.
**Edge cases handled:** Type 12 was added later (ref31) but the error message text still only names 3, 4 and 9
**Suspected defect:** Error message omits virtual account type 12 that the code accepts.
**Confidence:** High

### RULE-009: Only current, savings, virtual and accounting account types may be debited
**Category:** Validation
**Priority:** P0
**Source:** `sp_debcred_empresa.sp:414-740`
**Also cited:** sp_debcred_empresa.sp:414-1322; sp_debcred_empresa.sp:624-740
**Plain English:** The company account is debited through a debit note for current (3), savings (4) or virtual (12) accounts, through an accounting service transaction for type 9, and any other account type is rejected with error 122001.
**Specification:**
  Given An order debiting account type 12 (virtual) for 100.00
  When  the debit is executed
  Then  cob_virtuales..sp_vi_ndc_automatica is called with account-state verification 'S', channel 'SAT' and batch flag; for types 3/4 sp_ndc_ahcc is called; for type 9 db_biz_pagos..sp_graba_tran_servicio is called (lines 1264-1300); for any other type @w_cod_errord = 122001
**Parameters:** Allowed types: 3, 4, 12 (debit note), 9 (accounting); error 122001; virtual account debit forces @i_canal='SAT' and @i_verf_estado_cta='S'; batch flag = 1 when @i_aplcobis='N', 0 when 'S'
**Edge cases handled:** Type 9 accounting debit sends the company id as @i_tipo_chequera and indicator 1; Comment at 1314 says allowed types are 3, 4, 9 but code also allows 12
**Confidence:** High

### RULE-010: No header row updated is an error except for exempted services
**Category:** Validation
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:2076-2090`
**Plain English:** If the header update touched no row, the order fails with 122004 unless the service is one that is not tracked in the order header or it is an SPI return.
**Specification:**
  Given Service ROLPAGO, header update affected 0 rows in both live and history tables
  When  the post-update check runs
  Then  error 122004 is raised and the whole transaction is rolled back; for TRANSWIFT, IMPADUAN, PAGIESS, TRANSQUICK, TRANSBIMO, PAGOPRV, or when @w_act_totord = 'N', processing commits normally
**Parameters:** Exempt services: TRANSWIFT, IMPADUAN, PAGIESS, TRANSQUICK, TRANSBIMO, PAGOPRV; error 122004
**Confidence:** High

### RULE-011: Stray result set and debit-value override for TRANSCLI/TARJCRED/COMEXT
**Category:** Validation
**Priority:** P2
**Source:** `sp_debcred_empresa.sp:882-886`
**Plain English:** The code emits an unnamed result set of commission and ordered value before overriding the debit value; this appears to be leftover debugging output.
**Specification:**
  Given Service TRANSCLI called from an application expecting only output parameters
  When  The notification step for TRANSCLI runs
  Then  A two-column result set (@i_comision, @i_valor_ordenado) is returned to the caller in addition to the normal outputs
**Parameters:** Services: TRANSCLI, TARJCRED, COMEXT
**Suspected defect:** Line 882 'select @i_comision, @i_valor_ordenado' is a bare SELECT that returns a result set to the caller; likely debug leftover that may break callers that read result sets.
**Confidence:** High

## Lifecycle

### RULE-012: Debit outcome sets process status, rolls back on failure, and the movement is always recorded
**Category:** Lifecycle
**Priority:** P0
**Source:** `sp_debcred_empresa.sp:1332-1492`
**Also cited:** sp_debcred_empresa.sp:1332-1492; sp_debcred_empresa.sp:1332-1492
**Plain English:** If the debit note failed, the debit is rolled back to the savepoint and the status becomes 'X' (failed); otherwise 'P' (processed). In both cases a movement/payment-form record is written with the status and error code; if writing that record fails the whole thing aborts with 122002, and if the debit had failed the transaction is committed and the error returned.
**Specification:**
  Given Debit note returned error 201045
  When  The post-debit step runs
  Then  rollback to savepoint 'sp_debito_empresa', status 'X', sp_grb_mov_y_frmpgo called with @i_est_proceso='X' and @i_cod_error=201045, then commit and return 201045
**Parameters:** Status codes: 'P' processed, 'X' failed. Savepoint name 'sp_debito_empresa'. Movement affectation type '1'. Error 122002 when the movement record fails. @i_valor_comision sent to movement = bundled @i_comision.
**Edge cases handled:** SPI returns (affectation '12'): service on the movement is replaced by the original order's service (bp_orden.or_servicio, fallback history) and total-order update is disabled (@w_act_totord='N') (li…; Successful debit continues to commission step without committing yet
**Suspected defect:** Line 1248 assigns @w_cod_errord = @w_return after the notification block (sp_eventos / pa_sat_pnotificacion); a notification error therefore reverses a successful debit. Also lines 882-886 change @i_valor_debito after the debit has already been posted, so the movement record may not match the amount actually debited.
**Confidence:** High

### RULE-013: Separate commission is debited as its own movement; failure rolls back the whole debit
**Category:** Lifecycle
**Priority:** P0
**Source:** `sp_debcred_empresa.sp:1500-1732`
**Also cited:** sp_debcred_empresa.sp:1500-1732; sp_debcred_empresa.sp:1500-1732
**Plain English:** When a separate commission value exists it is debited via sp_grb_comision with reference 'COBRO DE COMISION'; if that fails the entire transaction (including the service debit) is rolled back, a failed commission movement is recorded, and the error is returned.
**Specification:**
  Given Service debit succeeded, @i_valor_comision = 1.20
  When  The commission step runs
  Then  sp_grb_comision is invoked with 1.20 and reference 'COBRO DE COMISION'; on error the full transaction rolls back, sp_grb_mov_y_frmpgo is written with status 'X', value 1.20 and the error code, and the procedure returns that code
**Parameters:** Trigger: @i_valor_comision > 0. Reference literal 'COBRO DE COMISION'. TRANSBIMO: @i_valor_tarifa = @i_valor_comision. TRANSQUICK with SPI payment form: @i_frm_pagcob replaced by @i_frm_pagcob_spi for the commission. Error 122003 generic, replaced by the returned code if non-zero. Tariff/commission splits per payment form (CUE/EFE/CHE) passed through (ref33).
**Edge cases handled:** Commission of 0 or NULL: step skipped entirely; Lines 1716-1730 (commit and return on @w_cod_errord != 0) are unreachable because the preceding block already returns on any @w_cod_errord != 0
**Suspected defect:** Lines 1716-1730 are dead code after REF44; the failed-commission movement at 1628-1694 is written after the rollback and is therefore auto-committed independently.
**Confidence:** Medium — P0 panel doubts spec fidelity: Read legacy/debcred/sp_debcred_empresa.sp:1500-1732. The core Given/When/Then is largely correct: TRANSQUICK swaps @i_frm_pagcob for @i_frm_pagcob_spi when not null (1500-1502); TRANSBIMO sets @i_valor_tarifa=@i_valor_comision (1506-1508); when @i_valor_comision > 0, sp_grb_comision is called with the commission value and literal reference 'COBRO DE COMISION' (1512-…

### RULE-014: Order header transitions from Initial to In-Transition by channel, option and payment form
**Category:** Lifecycle
**Priority:** P0
**Source:** `sp_debcred_empresa.sp:1876-2090`
**Also cited:** sp_debcred_empresa.sp:1876-2090; sp_debcred_empresa.sp:1876-2070
**Plain English:** After a successful debit and commission, the order's total record moves from state 'I' to 'T' for the payment forms implied by the channel and option; if no row is updated, the operation fails with 122004 unless the service is exempt or it is an SPI return.
**Specification:**
  Given Channel 'DIR', option '02', service ROLPAGO, order 555 with bp_total_orden rows in state 'I' for payment forms CUE/EFE
  When  The header update runs
  Then  Rows for order 555 with te_frm_pagcob in ('CUE','EFE','CHL') and te_estado_proceso 'I' (or NULL) are set to 'T' with the error code; if none updated (live or history), error 122004
**Parameters:** Direct channels: 'DIR','SFR','FR2','BTH','VEN'. Option '01' -> payment form = @i_frm_pagcob_deb (defaults to @i_frm_pagcob); '02' -> ('CUE','EFE','CHL'); '03' -> ('CUE','EFE','CHE'). Other channels -> ('COB','TRC','CTB','CPD','TPD'). Exempt from 122004: 'TRANSWIFT','IMPADUAN','PAGIESS','TRANSQUICK','TRANSBIMO','PAGOPRV' and SPI returns (@w_act_totord='N'). Falls back to db_sat_his..bp_total_orden_his.
**Edge cases handled:** Direct channel with option not in 01/02/03: no update is attempted and @wRowdbBiz retains its prior value, so 122004 may not fire; NULL te_estado_proceso is treated as 'I'
**Confidence:** High

### RULE-015: A failed debit note is committed as a recorded failure and returned as an output error code, not as a procedure error
**Category:** Lifecycle
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:1480-1492`
**Plain English:** If the debit note itself failed, the order's movement is still persisted with status X and the error code, the transaction is committed, and the debit-note error code is returned to the caller in the output parameter, bypassing the general error handler (so no rollback and no COBIS error message even when invoked from COBIS).
**Specification:**
  Given A debit on account type 5 (not allowed) producing debit error 122001
  When  The movement with status 'X' has been recorded successfully
  Then  The transaction is committed, @o_error = 122001 and the procedure returns 122001 without calling the COBIS error routine; if instead recording the movement fails, error 122002 goes through the rollback path
**Parameters:** success status = 'P'; failure status = 'X'; account-type error = 122001; movement-recording error = 122002
**Edge cases handled:** Return value equals the error code on this path, whereas the normal error path returns 0 when @i_aplcobis = 'N' — callers must handle both conventions; Comment at line 1314 says allowed types are 3, 4 or 9 while the code at 414 allows 3, 4 and 12
**Suspected defect:** Inconsistent return convention: this path returns the error code as the procedure's return value while the lbl_error path returns 0 with @o_error set when not invoked from COBIS.
**Confidence:** High

### RULE-016: Second SWIFT commission is debited as affectation type 16
**Category:** Lifecycle
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:1742-1856`
**Also cited:** sp_debcred_empresa.sp:1742-1856; sp_debcred_empresa.sp:1742-1856
**Plain English:** For SWIFT transfers with a second commission value, an additional commission debit is generated with affectation type '16' using the original order reference; failure aborts with the returned error or 122003.
**Specification:**
  Given Service TRANSWIFT, @i_valor2_swift = 15.00
  When  After the main commission step
  Then  sp_grb_comision is called with 15.00, @i_tipoafec='16', @i_valor_tarifa=15.00; error -> rollback and 122003 (or the routine's own code)
**Parameters:** Trigger: isnull(@i_valor2_swift,0) > 0 AND service = 'TRANSWIFT'. Affectation '16'. Error 122003.
**Edge cases handled:** Non-TRANSWIFT services ignore @i_valor2_swift for debiting but still add it to the notification cost (line 980)
**Confidence:** High

### RULE-017: Second SWIFT commission failure aborts the whole debit; its 'record and return' branch is unreachable
**Category:** Lifecycle
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:1824-1854`
**Plain English:** If the second SWIFT commission debit fails, the entire transaction is rolled back and error 122003 (or the engine's own error) is raised; the code path that would commit and return the commission error as an output code can never run.
**Specification:**
  Given A TRANSWIFT debit with @i_valor2_swift = $15.00 whose sp_grb_comision call returns @w_cod_errord = 122010
  When  The result is checked
  Then  Error 122010 is raised via lbl_error, the full transaction is rolled back and the main debit plus the first commission are reversed; the 'commit and return @o_error' branch at 1844-1854 is skipped because the goto fires first
**Parameters:** Default error 122003 'Error al generar debito por comision'; affectation '16'; tariff passed as @i_valor2_swift; beneficiary name = account name; @i_tipo_horario = @i_tipo_referencia
**Edge cases handled:** Unlike the main debit (which keeps a status-X movement), a second-commission failure leaves no recorded movement in this procedure (sp_grb_comision may record its own before the rollback discards it)
**Suspected defect:** Lines 1844-1854 are dead code: the preceding check (line 1824) already jumps to lbl_error whenever @w_cod_errord != 0, so the intended 'reverse, record commission movement with error, return' behaviour documented in the comment at 1840-1842 does not occur.
**Confidence:** High

### RULE-018: Order header state update falls back to the historical order table and treats a null state as Initial
**Category:** Lifecycle
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:1898-1924`
**Plain English:** When moving an order header from Initial to In-Transition, a row whose process state is null is treated as Initial, and if no matching row exists in the live order table the same transition is attempted on the historical (SAT) order table before deciding that nothing was updated.
**Specification:**
  Given Order 5001 whose header row has already been archived to db_sat_his..bp_total_orden_his with te_estado_proceso null
  When  The debit succeeds and the header transition runs
  Then  The live update affects 0 rows, the historical row is updated to state 'T' with the debit error code, and the 'no header updated' error 122004 is not raised
**Parameters:** Initial state = 'I' (null coerced to 'I'); target state = 'T'; live table = bp_total_orden; fallback table = db_sat_his..bp_total_orden_his
**Edge cases handled:** A header already in state 'T' or any non-Initial state is not updated (idempotence guard) and, if nothing else matches, triggers error 122004 unless the service is exempted; The same live-then-history fallback is used to find the originating service of an SPI refund (lines 1352-1370); Channel list for the option-based branch includes DIR, SFR, FR2, BTH and VEN; other channels use the collection payment-form set COB/TRC/CTB/CPD/TPD
**Confidence:** High

### RULE-019: SWIFT notifications read the beneficiary institution and credit account from the order detail, falling back to history
**Category:** Lifecycle
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:820-874`
**Plain English:** For TRANSWIFT the notification data (beneficiary institution and credit account name, first 30 chars) is read from the live order detail matched by order, sequence and ordering company; if the order was already archived the SAT history tables are used; for all other services these fields are empty.
**Specification:**
  Given A TRANSWIFT debit for bank order 12345, detail sequence 2, company 777, already archived to db_sat_his
  When  Notification data is gathered after a successful debit
  Then  @w_emp_inst = dt_referencia_grupo and @w_cta_cre = first 30 characters of dt_nom_cuenta from bp_orden_his/bp_detalle_his; notification commission = @i_valor_comision
**Parameters:** Match keys: or_orden_banco = @i_orden, dt_orden_banco, dt_secuencial = @i_secuencial (default 0), or_ordenante = @i_empresa; account name truncated to 30 chars
**Edge cases handled:** Non-TRANSWIFT services: institution and credit account set to NULL; If neither live nor history row matches, values remain NULL and notification proceeds anyway
**Confidence:** High

### RULE-020: Payroll (ROLPAGO) payment start date is no longer stamped on the order
**Category:** Lifecycle
**Priority:** P2
**Source:** `sp_debcred_empresa.sp:2094-2124`
**Plain English:** An earlier rule that set the order's payment start date (or_fch_inicio_pagcob) to the process date for ROLPAGO orders after a successful debit has been disabled (commented out, ref20) because virtual accounts are not in SAT; the procedure now commits without stamping it.
**Specification:**
  Given A ROLPAGO order successfully debited on process date 2026-09-27
  When  The debit completes and the transaction is committed
  Then  bp_orden.or_fch_inicio_pagcob is NOT updated by this procedure
**Parameters:** Service = 'ROLPAGO'; former error code 122005 on update failure (no longer raised)
**Edge cases handled:** Any consumer that relied on this start date being set here must obtain it elsewhere
**Suspected defect:** Dead code retained in the procedure; behaviour documented only by a comment
**Confidence:** Medium — Is the payment start date for ROLPAGO orders now set by another process, or is the field simply unused? Should the modernized service keep it disabled?

## Policy

### RULE-021: Generic notification is suppressed by the SAT block list or for BCE payroll without a debit account
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:1162-1208`
**Also cited:** sp_debcred_empresa.sp:1162-1208
**Plain English:** The generic event notification is not raised when an active entry in 'ba_bloqueaNotificacionSAT' matches 'service-smsService' and the calling procedure name, nor when the channel is BCE, the service is ROLPAGO and no debit account is present.
**Specification:**
  Given A ROLPAGO debit with SMS service 'ROL' invoked from procedure 'sp_x', and an active block-list entry named 'ROLPAGO-ROL' with other-field 'sp_x'
  When  The generic notification step evaluates the block list
  Then  The event is not created; the debit is still recorded as successful
**Parameters:** Catalogue 'ba_bloqueaNotificacionSAT'; key = rtrim(service) + '-' + rtrim(smsService); other field = calling SP name; status 'A'; BCE exception requires channel 'BCE', service 'ROLPAGO' and null account
**Edge cases handled:** Block-list match is per calling procedure, so the same service may notify from one caller and not from another; BCE exception can never trigger via the normalised channel values (DIR/SAT/BNK/VEN) — only when the raw channel is literally 'BCE'; Debit account null with tipcta 3/4/12 would already have failed the debit, so the BCE exception is practically limited to ledger-account (type 9) flows — SME to confirm
**Confidence:** Medium — Is the BCE/ROLPAGO/no-account exception still reachable in production, and is the block list intended to be keyed by the calling procedure name?

### RULE-022: Accounting-account debits for company 1295 on SPI are booked as balance instead of value
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:1264-1300`
**Also cited:** sp_debcred_empresa.sp:1270-1280; sp_debcred_empresa.sp:1264-1300
**Plain English:** When the company account is an accounting account (type 9) the debit is recorded via sp_graba_tran_servicio; for company 1295 on the SPI service the amount is sent as balance (@i_saldo) and the debit value is forced to 0.
**Specification:**
  Given Company 1295, service SPI, account type 9, debit 250.00
  When  The accounting debit is recorded
  Then  sp_graba_tran_servicio receives @i_saldo = 250.00 and @i_valor = 0, indicator 1, chequebook-type = '1295'; for any other company/service @i_saldo is NULL and @i_valor = 250.00
**Parameters:** Hardcoded company id '1295' and service 'SPI'; @i_indicador = 1; @i_oficina_cta = 0; @i_secuencial = 0; @i_tipo_chequera = company id as string.
**Edge cases handled:** @i_valor_debito remains 0 afterwards, so the movement record (line 1424) is also written with value 0 for this case
**Confidence:** Low — What business arrangement does company 1295 on SPI represent (ref45), and should the zeroed debit value also flow into the recorded movement? / Who is company 1295 and why are its SPI accounting debits recorded as balance with a zero transaction value? Should this be a configurable list rather than a hardcoded company? / Who is company 1295 and why is its SPI debit on ledger accounts recorded as…

### RULE-023: SPI returns are posted under the original order's service and skip header update
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:1348-1380`
**Also cited:** sp_debcred_empresa.sp:1348-1380
**Plain English:** For an SPI return (affectation '12'), the movement is recorded under the service of the original order (looked up from bp_orden or its history) and the order-header total is not required to be updated.
**Specification:**
  Given Service SPI, affectation '12', original order 777 whose service was TRANSCLI
  When  the movement is about to be recorded
  Then  @i_servicio becomes 'TRANSCLI' and @w_act_totord = 'N' so no 122004 error is raised if no header row changes
**Parameters:** Affectation '12' = SPI return; falls back to db_sat_his..bp_orden_his when not in bp_orden
**Confidence:** High

### RULE-024: Order data is read from the live payments database first and from the SAT history database when the order has already b…
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:1352-1370`
**Plain English:** Whenever the procedure needs order header or detail data (originating service of an SPI return, beneficiary/institution for notifications, order-header state update), it looks in the live db_biz_pagos tables and, if no row is found, repeats the lookup or update against the db_sat_his history tables.
**Specification:**
  Given An SPI return (affectation 12) for order 5001 which has been moved to bp_orden_his
  When  The originating service is resolved
  Then  or_servicio is taken from db_sat_his..bp_orden_his and the movement is attributed to that service
**Parameters:** Live: db_biz_pagos..bp_orden / bp_detalle / bp_total_orden; history: db_sat_his..bp_orden_his / bp_detalle_his / bp_total_orden_his; same pattern at 824-860, 890-938, 1026-1048, 1902-1924, 1952-1974, 2000-2022, 2046-2068
**Edge cases handled:** If the order exists in neither, the SPI return keeps service null and the header update affects zero rows (which is tolerated for SPI returns because act_totord = 'N'); History header update is attempted only when the live update touched zero rows, so an order present in both is updated once (live)
**Confidence:** High

### RULE-025: Debit payment form defaults to the order payment form, and TRANSQUICK commissions use the SPI payment form when supplied
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:1500-1502`
**Plain English:** If no specific debit payment form is given, the order's payment form is used to select which header rows move to Transition; for TRANSQUICK, when an SPI payment form is supplied, it replaces the payment form used to resolve the separate-commission accounting configuration.
**Specification:**
  Given A TRANSQUICK order with payment form 'CUE' and SPI payment form 'COB', and no explicit debit payment form
  When  The main debit finishes and the commission step starts
  Then  The header update (option 01) targets rows with te_frm_pagcob = 'CUE'; the commission transaction/causal is looked up with payment form 'COB'
**Parameters:** i_frm_pagcob_deb = isnull(i_frm_pagcob_deb, i_frm_pagcob) at line 262; override only when service = 'TRANSQUICK' and i_frm_pagcob_spi is not null
**Edge cases handled:** Override happens after the main movement was recorded, so the main movement keeps the original payment form while the commission uses the SPI one; Other services ignore i_frm_pagcob_spi entirely
**Confidence:** Medium — Is it intended that only TRANSQUICK uses the SPI payment form for the commission configuration, while the main debit record keeps the original payment form?

### RULE-026: Virtual-account debit is flagged as batch unless invoked from COBIS online
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:174-180`
**Also cited:** sp_debcred_empresa.sp:176-178
**Plain English:** The debit is marked as a batch operation for the virtual-accounts debit note whenever the caller is not the COBIS online application (aplcobis = 'N'), and as online when aplcobis = 'S'.
**Specification:**
  Given A debit to a virtual account (account type 12) invoked with aplcobis = 'N'
  When  The automatic virtual debit note (sp_vi_ndc_automatica) is called at line 654
  Then  The batch flag passed is 1; with aplcobis = 'S' the flag is 0
**Parameters:** aplcobis 'S' -> batch 0; aplcobis 'N' (default) -> batch 1
**Edge cases handled:** If aplcobis is any value other than 'S' or 'N', the batch flag stays NULL and is passed as NULL to the virtual debit note
**Confidence:** Medium — Does the virtual-accounts debit note behave differently (e.g. limits, notifications, cut-off) when the batch flag is 1 versus 0, and is NULL for an unexpected aplcobis value an acceptable outcome? / Does the virtual-account engine behave differently for batch=1 versus batch=0 (e.g. skips online limits or holds), and should a modernized service keep the SAT channel constant regardless of the origi…

### RULE-027: Combined commission is normalized into a separate commission when no separate value is supplied
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:188-196`
**Also cited:** sp_debcred_empresa.sp:188-196; sp_debcred_empresa.sp:188-196
**Plain English:** If the caller passes the commission bundled with the debit (@i_comision) but no separate commission (@i_valor_comision = 0), the procedure moves the amount to the separate-commission field and clears the bundled one, so commissions are always posted as their own movement.
**Specification:**
  Given A company debit order with @i_comision = 2.50 and @i_valor_comision = 0
  When  sp_debcred_empresa starts
  Then  @i_valor_comision becomes 2.50 and @i_comision becomes 0; the commission will later be debited via sp_grb_comision as a separate movement
**Parameters:** Trigger: @i_comision > 0 AND @i_valor_comision = 0 (exact zero; NULL does not trigger). Comment dates the change to 18/11/2013 (ref28).
**Edge cases handled:** @i_valor_comision NULL and @i_comision > 0: no swap occurs, commission stays bundled (isnull only applied later at line 230/236); Both > 0: no swap; both values coexist and later @w_comision_und takes @i_valor_comision (line 236-238 wins over 230-232)
**Confidence:** High

### RULE-028: Error reporting mode depends on COBIS invocation flag
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:2140-2170`
**Also cited:** sp_debcred_empresa.sp:2140-2170
**Plain English:** On a fatal error the transaction is rolled back; when invoked from COBIS online the error is raised through the COBIS error handler and returned as the return code, otherwise it is returned in the output parameter with return code 0.
**Specification:**
  Given Error 120000 with @i_aplcobis = 'N'
  When  lbl_error is reached
  Then  @o_error = 120000 and the procedure returns 0; with @i_aplcobis = 'S' cobis..sp_cerror is raised and 120000 is the return code
**Parameters:** Fatal errors: 120000, 122001 (via debit path), 122002, 122003, 122004; @i_aplcobis 'S'/'N'; batch flag derived at 176-178
**Confidence:** High

### RULE-029: Accounting concept selected by service and basic-account status
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:278-312`
**Also cited:** sp_debcred_empresa.sp:278-312
**Plain English:** Before looking up the accounting configuration, the concept code is taken from the terminal field for SPI orders, or set to '91' when the debited virtual account is a basic account (banking product 13), otherwise '0'.
**Specification:**
  Given A debit against virtual account type 12 whose cob_virtuales..vi_cuenta row has vi_prod_banc = 13, for a non-SPI service
  When  The procedure resolves the accounting concept before calling sp_con_confcontable
  Then  Concept '91' is passed to the accounting-configuration lookup; for a non-basic virtual, current or savings account the concept is '0'
**Parameters:** Basic-account banking product = 13; basic concept = '91'; default concept = '0'; SPI: concept = @s_term (then @s_term is blanked to ' ')
**Edge cases handled:** SPI service: concept comes from @s_term and the terminal field is reset to a single space, so downstream debit-note calls receive a blank terminal; Basic-account check only runs for account type 12; types 3/4 always get '0'
**Suspected defect:** The SPI branch (282-284) repurposes the session terminal parameter as business data and then blanks it, so movements for SPI are recorded with a blank terminal.
**Confidence:** Medium — Is it intended that SPI callers overload the terminal parameter (@s_term) to carry the accounting concept, and that the terminal is blanked afterwards for the debit-note record? / For SPI, is the terminal parameter deliberately overloaded to carry the accounting concept (who sets it upstream), and should the modernized system model it as an explicit 'concept' input instead?

### RULE-030: TRANSQUICK orders with an SPI payment form use that form for accounting lookup and commission
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:320-346`
**Also cited:** sp_debcred_empresa.sp:320-346
**Plain English:** For TRANSQUICK, when an SPI payment form is supplied, the accounting transaction/causal and the separate commission are resolved under the SPI payment form instead of the order's own payment form.
**Specification:**
  Given Service TRANSQUICK with @i_frm_pagcob = 'CUE' and @i_frm_pagcob_spi = 'COB'
  When  The accounting configuration is looked up and later the separate commission is charged
  Then  sp_con_confcontable is called with payment form 'COB' (without the basic concept), and before sp_grb_comision @i_frm_pagcob is overwritten with 'COB' (line 1500-1502); the header-status update still uses the original form via @i_frm_pagcob_deb
**Parameters:** Service = 'TRANSQUICK'; SPI form passed through when not null; @i_frm_pagcob_deb defaults to @i_frm_pagcob (line 262)
**Edge cases handled:** TRANSQUICK without SPI form follows the normal path with the basic concept; The SPI form is also passed to sp_ndc_ahcc as @i_frm_pagcob for all services (line 716)
**Confidence:** High

### RULE-031: IMPADUAN debits for CORPEI use the NDCORPEI accounting concept with fallback causal 512
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:456-550`
**Also cited:** sp_debcred_empresa.sp:456-550; sp_debcred_empresa.sp:456-550
**Plain English:** For customs-import payments (IMPADUAN) where the SWIFT code is 'CORPEI', the causal is re-resolved using the catalog concept flagged NDCORPEI; if no causal comes back, causal '512' is used, and a failed lookup aborts with 120000.
**Specification:**
  Given Service IMPADUAN, @i_cod_swift = 'CORPEI', catalog ad_concepto_contable has an active entry with ct_otro_campo_catalogo = 'NDCORPEI' and code '77'
  When  Causal is resolved for the debit
  Then  sp_con_confcontable is called with concept '77'; @w_causal = returned causal, or '512' if NULL
**Parameters:** Default concept '99999' if catalog entry missing; fallback causal '512'; catalog table 'ad_concepto_contable', flag 'NDCORPEI', status 'A'. Error 120000 on @w_return > 0.
**Edge cases handled:** NULL @i_cod_swift is treated as '' and does not match CORPEI; Catalog entry missing: concept '99999' is sent to the config lookup
**Confidence:** High

### RULE-032: SPI return (affectation 12) uses the company order number as the debit-note reference
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:558-614`
**Also cited:** sp_debcred_empresa.sp:556-614; sp_debcred_empresa.sp:558-614
**Plain English:** When the service is SPI and the affectation type is '12' (SPI return/devolucion), the reference string sent with the debit note is replaced by the company order number, overriding whatever service-based reference was built earlier.
**Specification:**
  Given An SPI order with @i_tipo_afec = '12' and @i_orden_empresa = 4587, for which the service-based reference was already built
  When  The debit note reference is finalized before posting
  Then  @w_cadena becomes '4587' (the order company number as text); the earlier reference text is discarded
**Parameters:** Service literal 'SPI'; affectation type '12'; reference = convert(varchar(10), @i_orden_empresa)
**Edge cases handled:** Comparison uses ltrim/rtrim on service, so padded 'SPI ' matches; Older code (commented out, ref39) built the reference from catalogue ad_concepto_spi code '01'; it is no longer executed; Order numbers longer than 10 characters would be truncated by convert(varchar(10))
**Suspected defect:** Block is effectively a no-op duplicate of the default assignment; safe to drop in a rewrite but confirm no downstream report depends on the concept description.
**Confidence:** High

### RULE-033: Virtual-account and ledger-account debits do not carry the bundled commission or card data
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:624-740`
**Plain English:** Only current/savings account debits pass the bundled commission, transaction count, unit commission, card number and provider detail to the debit engine; virtual-account debits post the plain value with the causal and reference only.
**Specification:**
  Given A debit of $100.00 with @i_comision = $2.50 (bundled), @w_cantidad = 5, @w_comision_und = $0.50
  When  The company account is virtual (type 12) vs current/savings (type 3/4)
  Then  Type 3/4: sp_ndc_ahcc receives tcomision=$2.50, nchq=5, solca=$0.50, card, provider detail, SPI payment form and order refs. Type 12: sp_vi_ndc_automatica receives only value $100.00, causal, currency, company, reference; the bundled commission and counts are not posted.
**Parameters:** sp_ndc_ahcc: @i_frm_pagcob = @i_frm_pagcob_spi, @i_alterno_dos = @i_frm_pagcob, @i_secuencial = 0; sp_vi_ndc_automatica: no commission fields
**Edge cases handled:** Bundled commission normally moves to separate commission at lines 188-196, so @i_comision reaching sp_ndc_ahcc is usually 0 unless both were supplied > 0; Ledger accounts (type 9) go through sp_graba_tran_servicio with @i_indicador = 1 and @i_tipo_chequera = company id as text
**Confidence:** Medium — Is it intended that virtual-account debits never bill the bundled commission or transaction count, or is that data simply not supported by the virtual engine?

### RULE-034: Successful debits trigger a customer notification event with channel and product mapping, unless blocked
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:746-1254`
**Also cited:** sp_debcred_empresa.sp:746-1254; sp_debcred_empresa.sp:746-1246; sp_debcred_empresa.sp:746
**Plain English:** After a successful debit (except supplier payments PAGOPRV), if the service is configured for SMS on the mapped channel, a notification is sent: basic-notification services ('B') go through pa_sat_pnotificacion, others through cob_internet..sp_eventos, unless the service/sms-service/calling-sp combination is in the block list or it is a BCE-channel ROLPAGO with no account.
**Specification:**
  Given Service TRANSCLI on channel 'BNK', debit 100.00, commission 0.50, active ad_servicios_sms entry ending in 'IBK', credit account type 4, no block-list entry
  When  The debit note returns 0
  Then  Channel is mapped to 'IBK'/'24OnLine', credit product 'AHO', debit product from the company account type, sp_eventos is called with value '100.00' and cost '0.50'
**Parameters:** Channel map: DIR/SAT -> 'SAT'/'SAT'; BNK -> 'IBK'/'24OnLine'; VEN -> 'Ventanilla'. Credit product map: tip_cta 0 -> null, 3 -> 'CTE', 4 -> 'AHO', 9 -> 'CON', 8 -> 'ESP'. Debit product: 3 -> 'CTE', 4 -> 'AHO', 12 -> 'VIR' (or 'AHO' when vi_prod_banc = 13). Notification cost = isnull(@w_valor_comision,0) + isnull(@i_valor2_swift,0). Catalogs: ad_servicios_sms, ad_notificacion_basica (default 'OTRO'), ba_bloqueaNotificacionSAT, ad_cuentas_bce. Excluded service: 'PAGOPRV'. TRANSWIFT: institution/credit account from bp_orden/bp_detalle (falls back to db_sat_his). TRANSCLI/TARJCRED/COMEXT: institution from ad_cuentas_bce catalog name positions 10-32.
**Edge cases handled:** No ad_servicios_sms match: no notification, silently skipped; Block list match on rtrim(service)+'-'+sms_service and sp_name -> notification suppressed; @w_canal_sms = 'BCE' and service ROLPAGO and NULL account -> suppressed; Lookups fall back to historical tables (db_sat_his) when live tables return no rows; @w_return from notification routines overwrites @w_cod_errord (line 1248), so a notification failure marks the debit as failed
**Suspected defect:** @w_cod_errord = @w_return at line 1248 lets a notification failure reverse an otherwise successful debit.
**Confidence:** Medium — Should a failure in the notification routines (sp_eventos / pa_sat_pnotificacion) really cause the whole debit to roll back (line 1248 propagates it into @w_cod_errord), or should notification failures be non-blocking? / Is the notification intended to be best-effort, or should a notification failure really reverse the debit as the code currently does (line 1248)? / Should a failed notification e…

### RULE-035: Notification channel and SMS service resolved from the order channel and catalogue
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:760-814`
**Also cited:** sp_debcred_empresa.sp:798-816
**Plain English:** After a successful debit (except PAGOPRV) the notification channel is normalised (DIR/SAT to SAT, BNK to IBK '24OnLine', VEN 'Ventanilla') and a notification is only attempted if an active ad_servicios_sms catalogue entry matches the service and that channel.
**Specification:**
  Given A successful debit on channel 'BNK' for service 'TRANSCLI' with an active catalogue entry 'TRC-IBK' whose name contains 'TRANSCLI'
  When  The post-debit notification step runs
  Then  Channel becomes 'IBK', description '24OnLine', SMS service 'TRC', and the notification flow proceeds; with no matching catalogue entry nothing is sent
**Parameters:** Channel map: DIR,SAT -> SAT/'SAT'; BNK -> IBK/'24OnLine'; VEN -> VEN/'Ventanilla'; catalogue table 'ad_servicios_sms', code format '<svc>-<canal>', status 'A'; excluded service 'PAGOPRV'
**Edge cases handled:** Catalogue match uses LIKE '%service%' so a service name contained in another (e.g. 'SPI' within another name) could match the wrong row; Channels other than DIR/SAT/BNK/VEN keep their code with a null description
**Confidence:** High

### RULE-036: Notification is routed to basic-account notifier or event engine by service classification
**Category:** Policy
**Priority:** P1
**Source:** `sp_debcred_empresa.sp:994-1094`
**Also cited:** sp_debcred_empresa.sp:994-1094
**Plain English:** Services flagged 'B' in the ad_notificacion_basica catalogue notify via pa_sat_pnotificacion with beneficiary data; all other services (defaulting to 'OTRO') raise a cob_internet event with debtor and creditor product codes.
**Specification:**
  Given Service 'ROLPAGO' listed with other-field 'B' in ad_notificacion_basica
  When  A successful debit reaches the notification step with a matching SMS service
  Then  pa_sat_pnotificacion is invoked with the ordered value, the separate commission, beneficiary name and group reference from bp_detalle (falling back to history); for a service not in the catalogue the sp_eventos path is used instead
**Parameters:** Catalogue 'ad_notificacion_basica'; classification values 'B' and default 'OTRO'; event value = debit value, event cost = separate commission + second SWIFT value (lines 980-986)
**Edge cases handled:** A catalogue value other than 'B' or 'OTRO' sends no notification at all; Order detail lookups fall back to db_sat_his history tables when not found in live tables
**Confidence:** High

### RULE-037: Invocation context defaults: direct channel, affectation 10, batch flag derived from COBIS flag
**Category:** Policy
**Priority:** P2
**Source:** `sp_debcred_empresa.sp:14-38`
**Plain English:** When callers omit optional inputs, the debit is treated as a non-COBIS call (aplcobis 'N'), on the direct channel 'DIR', with affectation type '10' (standard debit), zero second SWIFT value and sequential 0; the COBIS flag also decides whether the virtual-account debit note is flagged as batch (batch = 1 only when aplcobis = 'N').
**Specification:**
  Given A caller invokes the procedure without @i_canal, @i_tipo_afec or @i_aplcobis
  When  The procedure initializes
  Then  @i_canal = 'DIR', @i_tipo_afec = '10', @i_aplcobis = 'N', and @w_batch = 1 (passed to sp_vi_ndc_automatica for virtual accounts)
**Parameters:** Defaults: aplcobis 'N', canal 'DIR', tipo_afec '10', valor2_swift 0, secuencial 0; batch mapping S->0, N->1 at lines 176-178, used at line 654
**Edge cases handled:** Any @i_aplcobis value other than 'S' or 'N' leaves @w_batch null; @s_ssn defaults to 0 when null (line 266); @i_frm_pagcob_deb defaults to @i_frm_pagcob when null (line 262)
**Confidence:** Medium — Is the 'batch' flag for virtual-account debit notes intended to mean 'not invoked from COBIS online', and what does the virtual-account module do differently in batch mode?

### RULE-038: Debit reference text depends on the service
**Category:** Policy
**Priority:** P2
**Source:** `sp_debcred_empresa.sp:422-450`
**Also cited:** sp_debcred_empresa.sp:422-450
**Plain English:** The reference stamped on the debit note is the company order number, except for TRANSWIFT where it is 'COD:' plus the SWIFT code.
**Specification:**
  Given Company order 12345 for service TRANSWIFT with SWIFT code ABC123
  When  the debit note reference is built
  Then  reference = 'COD:ABC123'; for any other service reference = '12345'
**Parameters:** Prefix 'COD:'; SPI returns (affectation '12') also use the order number (line 612)
**Edge cases handled:** TRANSWIFT with null @i_cod_swift yields a NULL reference (string concatenation with null)
**Confidence:** High

### RULE-039: Cash/cheque/account tariff and commission breakdown is passed through to the separate-commission debit only
**Category:** Policy
**Priority:** P2
**Source:** `sp_debcred_empresa.sp:86-96`
**Plain English:** The six REF33 tariff/commission breakdown inputs (tarifa, comision_cue, tarifa_efe, comision_efe, tarifa_che, comision_che) are not validated or computed here; they are forwarded unchanged to the separate-commission debit note (lines 1590-1600), and the generic tariff is overwritten with the separate commission before that call (line 1508), while the second SWIFT debit uses the SWIFT value as its…
**Specification:**
  Given A call supplying @i_valor_tarifa_efe = 1.50 and @i_valor_comision = 2.00
  When  The separate-commission debit note is posted
  Then  The commission debit note receives valor_tarifa = 2.00 (overwritten from @i_valor_comision), valor_tarifa_efe = 1.50 and the other breakdown fields as supplied
**Parameters:** All six breakdown inputs default to null; main debit note does not receive them
**Edge cases handled:** @i_valor_tarifa supplied by the caller is silently replaced by @i_valor_comision at line 1508; Second SWIFT commission passes @i_valor2_swift as valor_tarifa
**Confidence:** Medium — Are the cash/cheque/account tariff breakdown values meant to be informational for the commission movement only, or should they influence the amount debited?

## Rules requiring SME confirmation

- **RULE-001** (Medium): Why does TRANSBIMO override the tariff with the commission value, and should the per-form (cue/efe/che) tariff pairs also be derived for it?
- **RULE-002** (Medium): Is truncating the transaction count (int) intended when the commission is not an exact multiple of the per-transaction tariff, and should the unit commission really be reported as 0 when no tariff is configured? / Is the unconditional overwrite @w_comision_und = @w_val_por_tran at line 248 intentional (unit commission always equals the configured fee, even when the caller-supplied commission diff…
- **RULE-003** (Medium): Is it intended that the recorded movement value for TRANSCLI/TARJCRED/COMEXT equals the ordered value (excluding commission) only when an SMS configuration exists, and should the stray result set at line 882 be removed?
- **RULE-004** (Medium): Is it intended that for TRANSCLI/TARJCRED/COMEXT the recorded movement value becomes the ordered value (excluding commission) rather than the debited value passed in?
- **RULE-013** (Medium): P0 panel doubts spec fidelity: Read legacy/debcred/sp_debcred_empresa.sp:1500-1732. The core Given/When/Then is largely correct: TRANSQUICK swaps @i_frm_pagcob for @i_frm_pagcob_spi when not null (1500-1502); TRANSBIMO sets @i_valor_tarifa=@i_valor_comision (1506-1508); when @i_valor_comision > 0, sp_grb_comision is called with the commission value and literal reference 'COBRO DE COMISION' (1512-…
- **RULE-020** (Medium): Is the payment start date for ROLPAGO orders now set by another process, or is the field simply unused? Should the modernized service keep it disabled?
- **RULE-021** (Medium): Is the BCE/ROLPAGO/no-account exception still reachable in production, and is the block list intended to be keyed by the calling procedure name?
- **RULE-022** (Low): What business arrangement does company 1295 on SPI represent (ref45), and should the zeroed debit value also flow into the recorded movement? / Who is company 1295 and why are its SPI accounting debits recorded as balance with a zero transaction value? Should this be a configurable list rather than a hardcoded company? / Who is company 1295 and why is its SPI debit on ledger accounts recorded as…
- **RULE-025** (Medium): Is it intended that only TRANSQUICK uses the SPI payment form for the commission configuration, while the main debit record keeps the original payment form?
- **RULE-026** (Medium): Does the virtual-accounts debit note behave differently (e.g. limits, notifications, cut-off) when the batch flag is 1 versus 0, and is NULL for an unexpected aplcobis value an acceptable outcome? / Does the virtual-account engine behave differently for batch=1 versus batch=0 (e.g. skips online limits or holds), and should a modernized service keep the SAT channel constant regardless of the origi…
- **RULE-029** (Medium): Is it intended that SPI callers overload the terminal parameter (@s_term) to carry the accounting concept, and that the terminal is blanked afterwards for the debit-note record? / For SPI, is the terminal parameter deliberately overloaded to carry the accounting concept (who sets it upstream), and should the modernized system model it as an explicit 'concept' input instead?
- **RULE-033** (Medium): Is it intended that virtual-account debits never bill the bundled commission or transaction count, or is that data simply not supported by the virtual engine?
- **RULE-034** (Medium): Should a failure in the notification routines (sp_eventos / pa_sat_pnotificacion) really cause the whole debit to roll back (line 1248 propagates it into @w_cod_errord), or should notification failures be non-blocking? / Is the notification intended to be best-effort, or should a notification failure really reverse the debit as the code currently does (line 1248)? / Should a failed notification e…
- **RULE-037** (Medium): Is the 'batch' flag for virtual-account debit notes intended to mean 'not invoked from COBIS online', and what does the virtual-account module do differently in batch mode?
- **RULE-039** (Medium): Are the cash/cheque/account tariff breakdown values meant to be informational for the commission movement only, or should they influence the amount debited?

## Rules folded into another

These rules described the same behavior as another rule in a different place, so they were merged into it (the kept rule lists their locations under "Also cited"):

- Commission carried in the combined field is moved to the separate-commission field (sp_debcred_empresa.sp:188-196) into Combined commission is normalized into a separate commission when no separate value is supplied
- Commission bundled with the debit is converted to a separate commission (sp_debcred_empresa.sp:188-196) into Combined commission is normalized into a separate commission when no separate value is supplied
- Debit-side product code for notifications is derived from the account type, with virtual accounts o… (sp_debcred_empresa.sp:1102-1154) into Event notification identifies debtor product and client, treating basic virtual accounts as savings
- Number of billable transactions is derived from commission amount divided by configured per-transac… (sp_debcred_empresa.sp:204-248) into Unit commission and transaction count derived from company per-transaction tariff
- Per-transaction commission unit and record count derived from the company tariff (sp_debcred_empresa.sp:204-248) into Unit commission and transaction count derived from company per-transaction tariff
- A positive commission is debited as a separate 'COBRO DE COMISION' movement; failure reverses the w… (sp_debcred_empresa.sp:1500-1732) into Separate commission is debited as its own movement; failure rolls back the whole debit
- Separate commission debit and its failure handling (sp_debcred_empresa.sp:1500-1732) into Separate commission is debited as its own movement; failure rolls back the whole debit
- Debit posting routed by the company account type (sp_debcred_empresa.sp:414-1322) into Only current, savings, virtual and accounting account types may be debited
- Debit note is routed by account type: virtual accounts via automatic virtual NDC, current/savings v… (sp_debcred_empresa.sp:624-740) into Only current, savings, virtual and accounting account types may be debited
- Debit is rejected with error 122001 for unsupported company account types (sp_debcred_empresa.sp:1308-1322) into Only current, savings, virtual or accounting company accounts may be debited
- Debit outcome determines process status and whether the debit is kept (sp_debcred_empresa.sp:1332-1492) into Debit outcome sets process status, rolls back on failure, and the movement is always recorded
- Debit-note failure rolls back to the savepoint and records the movement with status X (sp_debcred_empresa.sp:1332-1492) into Debit outcome sets process status, rolls back on failure, and the movement is always recorded
- Order header moves from Initial to Transition after successful debit (sp_debcred_empresa.sp:1876-2090) into Order header transitions from Initial to In-Transition by channel, option and payment form
- Order header moves from Initial to Transition for the matching payment forms (sp_debcred_empresa.sp:1876-2070) into Order header transitions from Initial to In-Transition by channel, option and payment form
- Second SWIFT commission charged for international transfers (sp_debcred_empresa.sp:1742-1856) into Second SWIFT commission is debited as affectation type 16
- TRANSWIFT second commission posted as affectation 16 (sp_debcred_empresa.sp:1742-1856) into Second SWIFT commission is debited as affectation type 16
- SPI refunds are attributed to the originating service and skip order-header update (sp_debcred_empresa.sp:1348-1380) into SPI returns are posted under the original order's service and skip header update
- Accounting concept is derived from account class: basic virtual accounts use concept 91, SPI reuses… (sp_debcred_empresa.sp:278-312) into Accounting concept selected by service and basic-account status
- Company 1295 SPI debits on accounting accounts are recorded as balance, not value (sp_debcred_empresa.sp:1270-1280) into Accounting-account debits for company 1295 on SPI are booked as balance instead of value
- Ledger-account SPI debits for company 1295 are recorded as balance only (sp_debcred_empresa.sp:1264-1300) into Accounting-account debits for company 1295 on SPI are booked as balance instead of value
- Batch mode is derived from the COBIS invocation flag and drives virtual-account posting (sp_debcred_empresa.sp:176-178) into Virtual-account debit is flagged as batch unless invoked from COBIS online
- Debit-note reference string depends on service (sp_debcred_empresa.sp:422-450) into Debit reference text depends on the service
- Error reporting mode depends on invocation context (sp_debcred_empresa.sp:2140-2170) into Error reporting mode depends on COBIS invocation flag
- Event notification suppressed by block catalogue or for BCE payroll without debit account (sp_debcred_empresa.sp:1162-1208) into Generic notification is suppressed by the SAT block list or for BCE payroll without a debit account
- IMPADUAN debits for CORPEI use a dedicated causal (sp_debcred_empresa.sp:456-550) into IMPADUAN debits for CORPEI use the NDCORPEI accounting concept with fallback causal 512
- IMPADUAN orders for CORPEI use the NDCORPEI concept causal, defaulting to 512 (sp_debcred_empresa.sp:456-550) into IMPADUAN debits for CORPEI use the NDCORPEI accounting concept with fallback causal 512
- Notification is sent only when an active SMS-service mapping exists for the service and channel (sp_debcred_empresa.sp:798-816) into Notification channel and SMS service resolved from the order channel and catalogue
- Notification type is chosen from the basic-notification catalogue: 'B' sends a structured payment n… (sp_debcred_empresa.sp:994-1094) into Notification is routed to basic-account notifier or event engine by service classification
- Post-debit customer notification (SMS/event) by service and channel (sp_debcred_empresa.sp:746-1254) into Successful debits trigger a customer notification event with channel and product mapping, unless bl…
- Successful debit triggers a customer notification event unless blocked (sp_debcred_empresa.sp:746-1246) into Successful debits trigger a customer notification event with channel and product mapping, unless bl…
- Provider payments (PAGOPRV) never trigger customer notification (sp_debcred_empresa.sp:746) into Successful debits trigger a customer notification event with channel and product mapping, unless bl…
- SPI return debit notes reference the company order number (sp_debcred_empresa.sp:556-614) into SPI return (affectation 12) uses the company order number as the debit-note reference
- SPI return debits use the company order number as reference (sp_debcred_empresa.sp:558-614) into SPI return (affectation 12) uses the company order number as the debit-note reference
- TRANSQUICK debits resolve accounting configuration by the SPI payment form when one is supplied (sp_debcred_empresa.sp:320-346) into TRANSQUICK orders with an SPI payment form use that form for accounting lookup and commission
- Accounting configuration lookup fails the debit on both procedure error and SQL error (sp_debcred_empresa.sp:350-394) into Accounting transaction code and causal are resolved from the accounting configuration; missing conf…
- Accounting transaction and causal must be resolved from the accounting configuration before any deb… (sp_debcred_empresa.sp:278-394) into Accounting transaction code and causal are resolved from the accounting configuration; missing conf…
- An accounting configuration (transaction code and causal) must exist for the debit (sp_debcred_empresa.sp:278-394) into Accounting transaction code and causal are resolved from the accounting configuration; missing conf…
