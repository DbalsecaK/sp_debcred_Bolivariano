package com.nexti.debcred;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block B7 (lines 746-1256): the customer notification after a successful debit. Business logic
 * preserved as-is (INTENT.md), including the approved quirks: a notifier failure becomes the debit
 * error code and reverses the debit (RULE-034, RULE-012, line 1248); the basic notifier's
 * {@code @o_error} reaches the caller (brief section 7 A15); the lowest {@code ct_cod_catalogo} wins
 * (A16); {@code @wRowdbBiz} counts the live read only (A17). The stray result set at line 882 is not
 * emitted (A11, RULE-011).
 */
public final class CustomerNotifications implements NotificationStep {

    private static final Logger log = LoggerFactory.getLogger(CustomerNotifications.class);
    private static final List<String> INTERBANK_SERVICES = List.of("TRANSCLI", "TARJCRED", "COMEXT");
    private static final String DEFAULT_CLASS = "OTRO";

    private final NotificationCatalogReader catalog;
    private final OrderDetailReader details;
    private final AccountOwnerReader owners;
    private final BasicNotificationPort basic;
    private final EventNotificationPort events;

    public CustomerNotifications(NotificationCatalogReader catalog, OrderDetailReader details,
                                 AccountOwnerReader owners, BasicNotificationPort basic, EventNotificationPort events) {
        this.catalog = catalog;
        this.details = details;
        this.owners = owners;
        this.basic = basic;
        this.events = events;
    }

    @Override
    public NotificationOutcome notify(NotificationContext ctx) {
        DebitRequest r = ctx.request();
        String servicio = r.iServicio();
        String trimmedService = AseText.trim(servicio);
        if (trimmedService == null) {
            return NotificationOutcome.notConfigured();                     // 746: NULL <> 'PAGOPRV' is unknown
        }

        Channel channel = Channel.of(r.iCanal());                           // RULE-035
        String servSms = smsService(servicio, channel.code());             // RULE-035, A16
        if (servSms == null) {
            return NotificationOutcome.notConfigured();                     // 814
        }

        // 820-976: the credit side and the working values each service family sets
        Credit credit = Credit.NONE;
        BigDecimal valorComision = null;
        BigDecimal valorDebito = ctx.valorDebito();
        BigDecimal newValorDebito = null;
        Integer wRowdbBiz = null;
        if (trimmedService.equals("TRANSWIFT")) {                                                  // RULE-019
            List<SwiftCreditDetail> live = details.liveSwiftCreditDetails(r.iOrden(), r.iSecuencial(), r.iEmpresa());
            wRowdbBiz = live.size();                                                               // 840 (A17)
            SwiftCreditDetail row = firstOf(live,
                    () -> details.historySwiftCreditDetails(r.iOrden(), r.iSecuencial(), r.iEmpresa()));
            if (row != null) {
                credit = new Credit(AseText.varchar(row.referenciaGrupo(), 32),
                        AseText.substring(row.nomCuenta(), 1, 30), null);
            }
            valorComision = ctx.valorComision();
        } else if (INTERBANK_SERVICES.contains(trimmedService)) {                                  // RULE-003, RULE-004
            valorComision = ctx.comision();
            newValorDebito = r.iValorOrdenado();                                                   // 886
            valorDebito = newValorDebito;
            List<InterbankCreditDetail> live = details.liveInterbankCreditDetails(r.iOrden());
            wRowdbBiz = live.size();                                                               // 912 (A17)
            InterbankCreditDetail row = firstOf(live, () -> details.historyInterbankCreditDetails(r.iOrden()));
            if (row != null) {
                credit = new Credit(AseText.substring(row.catalogName(), 10, 32),
                        AseText.varchar(row.numeroCuenta(), 30), row.tipoCta());
            }
        }
        String prodCre = creditProduct(credit.tipoCta());                                         // RULE-004, RULE-005

        // 980-986: the texts the event carries. RULE-006
        BigDecimal costo = AseText.zeroIfNull(valorComision).add(AseText.zeroIfNull(r.iValor2Swift()));
        String valorSms = AseText.moneyToVarchar(valorDebito);
        String costoSms = AseText.moneyToVarchar(costo);

        // 994-1014: which notifier. RULE-036
        String cls = AseText.varchar(catalog.notificationClass(servicio).orElse(null), 4);
        if (cls == null) {
            cls = DEFAULT_CLASS;
        }
        int returnCode = 0;                                                  // @w_return is the debit's 0 (746)
        Integer oError = 0;                                                  // @o_error as line 256 left it
        if (AseText.equalsIgnoringTrailingBlanks(cls, "B")) {
            BeneficiaryDetail beneficiary = firstOf(details.liveBeneficiaryDetails(r.iOrden()),
                    () -> details.historyBeneficiaryDetails(r.iOrden()));
            String nombre = beneficiary == null ? null : AseText.varchar(beneficiary.nombreBeneficiario(), 64);
            String refGrupo = beneficiary == null ? null : AseText.varchar(beneficiary.referenciaGrupo(), 20);
            BasicNotificationResult result = basic.notifyBasic(new BasicNotificationCommand(r.iCanal(),
                    r.iNumctaEmp(), r.iTipctaEmp(), r.iServicio(), r.iOrden(), refGrupo, r.iSecuencial(),
                    r.iValorOrdenado(), nombre, ctx.valorComision(), credit.cuenta(), prodCre, credit.institucion()));
            returnCode = result.returnCode();
            oError = result.oError();                                                              // A15
        } else if (AseText.equalsIgnoringTrailingBlanks(cls, DEFAULT_CLASS)) {
            Debtor debtor = debtor(r.iTipctaEmp(), r.iNumctaEmp());                               // RULE-005
            boolean blocked = catalog.notificationBlocked(servicio, servSms, r.iSpName());        // RULE-021
            boolean bcePayroll = AseText.equalsIgnoringTrailingBlanks(channel.code(), "BCE")
                    && AseText.equalsIgnoringTrailingBlanks(servicio, "ROLPAGO") && r.iNumctaEmp() == null;
            if (!blocked && !bcePayroll) {
                EventResult result = events.registerEvent(new EventCommand("I", channel.code(), servSms,
                        r.iTipctaEmp(), r.iNumctaEmp(), valorSms, r.iNumctaEmp(), debtor.producto(), credit.cuenta(),
                        prodCre, debtor.cliente(), costoSms, credit.institucion(), channel.description()));
                returnCode = result.returnCode();
            }
        }
        if (returnCode != 0) {
            log.warn("notifier failed for order {} service {}: status {}; the debit is reversed (RULE-034, line 1248)",
                    r.iOrden(), trimmedService, returnCode);
        }
        return new NotificationOutcome(true, returnCode, newValorDebito, oError, wRowdbBiz);   // 1248
    }

    /** 798-812: the SMS service code prefix of the lowest matching row, or NULL. */
    private String smsService(String servicio, String canalSms) {
        List<String> codes = catalog.smsServiceCodes(servicio, canalSms);
        if (codes.isEmpty()) {
            return null;
        }
        String code = codes.get(0);
        int dash = code == null ? 0 : code.indexOf('-') + 1;                       // patindex('%-%', code)
        return AseText.varchar(AseText.trim(AseText.substring(code, 1, dash - 1)), 10);
    }

    /** 944-974: the beneficiary account type as a credit product. */
    private static String creditProduct(Integer tipCta) {
        if (tipCta == null) {
            return null;
        }
        return switch (tipCta) {
            case 3 -> "CTE";
            case 4 -> "AHO";
            case 8 -> "ESP";
            case 9 -> "CON";
            default -> null;
        };
    }

    /** 1102-1154: the debtor product and client by company account type. */
    private Debtor debtor(Integer tipctaEmp, String numctaEmp) {
        if (Objects.equals(tipctaEmp, 3)) {
            return new Debtor("CTE", owners.currentAccountClient(numctaEmp).orElse(null));
        }
        if (Objects.equals(tipctaEmp, 4)) {
            return new Debtor("AHO", owners.savingsAccountClient(numctaEmp).orElse(null));
        }
        if (Objects.equals(tipctaEmp, 12)) {
            Optional<VirtualAccountOwner> owner = owners.virtualAccountOwner(numctaEmp);
            return owner.map(o -> new Debtor(Objects.equals(o.prodBanc(), 13) ? "AHO" : "VIR", o.cliente()))
                    .orElse(new Debtor("VIR", null));
        }
        return new Debtor(null, null);
    }

    /** A scalar-assignment read: the live row, else the history row; which row of several is undefined in the legacy. */
    private static <T> T firstOf(List<T> live, Supplier<List<T>> history) {
        List<T> rows = live.isEmpty() ? history.get() : live;
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code @w_canal_sms char(3)} and {@code @w_desc_canal} (760-794). */
    private record Channel(String code, String description) {

        static Channel of(String canal) {
            String code = AseText.toChar(canal, 3);
            if (AseText.equalsIgnoringTrailingBlanks(code, "DIR") || AseText.equalsIgnoringTrailingBlanks(code, "SAT")) {
                return new Channel("SAT", "SAT");
            }
            if (AseText.equalsIgnoringTrailingBlanks(code, "BNK")) {
                return new Channel("IBK", "24OnLine");
            }
            if (AseText.equalsIgnoringTrailingBlanks(code, "VEN")) {
                return new Channel(code, "Ventanilla");
            }
            return new Channel(code, null);
        }
    }

    /** {@code @w_emp_inst}, {@code @w_cta_cre}, {@code @w_tip_cta}. */
    private record Credit(String institucion, String cuenta, Integer tipoCta) {
        static final Credit NONE = new Credit(null, null, null);
    }

    /** {@code @w_proddeb}, {@code @w_ente}. */
    private record Debtor(String producto, Integer cliente) {
    }
}
