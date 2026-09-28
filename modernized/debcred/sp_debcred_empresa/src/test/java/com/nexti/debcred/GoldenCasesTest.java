package com.nexti.debcred;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.nexti.debcred.support.FakeAseSession;
import com.nexti.debcred.support.Requests;

// Spring Boot 4 ships Jackson 3 (tools.jackson), not com.fasterxml.jackson.
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Table-driven equivalence cases from {@code src/test/resources/golden/phase1-cases.json} (the
 * three exits of Phase 1), {@code phase2-cases.json} (the commission blocks and exit B) and
 * {@code phase3-cases.json} (the order-header update B12, RULE-014/018/010) and {@code phase4-cases.json}
 * (the notifications B7, RULE-034/035/019/003/004/005/006/021/036, A15/A17): one
 * dynamic test per case, each named with its rule ids. The files are the shape the bank's recorded
 * outputs (brief section 7 A6) will take, so a future dual run can feed the same rows to the T-SQL
 * procedure and to the service.
 *
 * <p>A missing or empty fixture is a FAILURE, never a skip: a suite that is green because nothing
 * ran proves nothing. The factory also emits a final test that reports how many cases executed
 * across all files.
 */
class GoldenCasesTest {

    private static final List<String> FIXTURES = List.of("/golden/phase1-cases.json", "/golden/phase2-cases.json",
            "/golden/phase3-cases.json", "/golden/phase4-cases.json");

    @TestFactory
    List<DynamicTest> goldenCases() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        int[] executed = {0};
        int total = 0;
        for (String fixture : FIXTURES) {
            JsonNode cases = loadFixture(fixture).get("cases");
            if (cases == null || !cases.isArray() || cases.isEmpty()) {
                throw new AssertionError(fixture + " has no 'cases': equivalence cases executed: 0");
            }
            for (JsonNode c : cases) {
                String name = c.get("rules").toString() + " " + c.get("name").asText();
                tests.add(DynamicTest.dynamicTest(name, () -> {
                    runCase(c);
                    executed[0]++;
                }));
            }
            total += cases.size();
        }
        int expected = total;
        tests.add(DynamicTest.dynamicTest("equivalence cases executed: " + expected, () -> {
            System.out.println("equivalence cases executed: " + executed[0] + " of " + expected);
            assertThat(executed[0]).as("equivalence cases executed").isEqualTo(expected).isPositive();
        }));
        return tests;
    }

    private static JsonNode loadFixture(String fixture) throws IOException {
        try (InputStream in = GoldenCasesTest.class.getResourceAsStream(fixture)) {
            if (in == null) {
                throw new AssertionError("fixture " + fixture + " is missing: equivalence cases executed: 0");
            }
            return new ObjectMapper().readTree(in);
        }
    }

    private static void runCase(JsonNode c) {
        FakeAseSession ase = new FakeAseSession()
                .accounting(new AccountingConfiguration(c.get("accountingReturnCode").asInt(), 2701, "0150"))
                .debitNote(c.get("debitReturnCode").asInt(), 9001)
                .virtualDebit(c.get("debitReturnCode").asInt(), 0, 9002)
                .ledgerDebit(c.get("debitReturnCode").asInt())
                .movement(c.get("movementReturnCode").asInt());
        // Phase 2 inputs (optional; absent = the Phase 1 fixture: no commission, no SWIFT value)
        if (c.hasNonNull("commissionReturnCode")) {
            ase.commission(c.get("commissionReturnCode").asInt(), c.get("commissionOError").asInt());
        }
        if (c.hasNonNull("secondCommissionReturnCode")) {
            ase.secondCommission(c.get("secondCommissionReturnCode").asInt(), c.get("secondCommissionOError").asInt());
        }
        // Phase 3 inputs (optional; absent = unscripted header tables, the Phase 1/2 fixture)
        if (c.has("headerRows")) {
            ase.noHeaderRows();
            for (JsonNode h : c.get("headerRows")) {
                String state = text(h, "state");
                if ("HISTORY".equals(h.get("table").asText())) {
                    ase.historyHeaderRow(h.get("order").asInt(), text(h, "form"), text(h, "service"), state);
                } else {
                    ase.headerRow(h.get("order").asInt(), text(h, "form"), text(h, "service"), state);
                }
            }
        }
        if (c.has("headerThrows")) {
            c.get("headerThrows").forEach(t -> ase.headerUpdateThrows(FakeAseSession.HeaderTable.valueOf(t.asText())));
        }
        if (c.hasNonNull("liveOrderService")) {
            ase.liveOrder(Requests.BANK_ORDER, c.get("liveOrderService").asText());
        }
        scriptNotificationTables(ase, c);
        Requests request = Requests.currentAccount()
                .tipctaEmp(c.get("accountType").asInt())
                .servicio(c.get("service").asText())
                .aplcobis(c.get("aplcobis").asText())
                .empresa(c.get("company").asInt());
        if (c.has("comision")) {
            request.comision(text(c, "comision"));
        }
        if (c.has("valorComision")) {
            request.valorComision(text(c, "valorComision"));
        }
        if (c.has("valor2Swift")) {
            request.valor2Swift(text(c, "valor2Swift"));
        }
        if (c.has("codSwift")) {
            request.codSwift(text(c, "codSwift"));
        }
        if (c.has("frmPagcobSpi")) {
            request.frmPagcobSpi(text(c, "frmPagcobSpi"));
        }
        if (c.has("canal")) {
            request.canal(text(c, "canal"));
        }
        if (c.has("opcion")) {
            request.opcion(text(c, "opcion"));
        }
        if (c.has("frmPagcob")) {
            request.frmPagcob(text(c, "frmPagcob"));
        }
        if (c.has("frmPagcobDeb")) {
            request.frmPagcobDeb(text(c, "frmPagcobDeb"));
        }
        if (c.has("tipoAfec")) {
            request.tipoAfec(text(c, "tipoAfec"));
        }
        // Phase 4 inputs (optional)
        if (c.has("valorDebito")) {
            request.valorDebito(text(c, "valorDebito"));
        }
        if (c.has("valorOrdenado")) {
            request.valorOrdenado(text(c, "valorOrdenado"));
        }
        if (c.has("spName")) {
            request.spName(text(c, "spName"));
        }
        if (c.has("numcta")) {
            request.numctaEmp(text(c, "numcta"));
        }
        if (c.hasNonNull("secuencial")) {
            request.secuencial(c.get("secuencial").asInt());
        }

        DebitResult result = ase.service().debit(request.build());

        JsonNode e = c.get("expected");
        assertThat(result.returnCode()).as("return code").isEqualTo(e.get("returnCode").asInt());
        // Phase 4 (A15): exit C in COBIS mode can answer a NULL @o_error; a JSON null is that NULL
        assertThat(result.oError()).as("@o_error").isEqualTo(integer(e, "oError"));
        assertThat(result.oRegAProc()).as("@o_reg_a_proc").isNull();
        assertThat(ase.begins()).as("begin tran").isEqualTo(e.get("begins").asInt());
        assertThat(ase.commits()).as("commit tran").isEqualTo(e.get("commits").asInt());
        assertThat(ase.rollbacks()).as("rollback tran").isEqualTo(e.get("rollbacks").asInt());
        if (e.has("movementStatuses")) {
            List<String> statuses = new ArrayList<>();
            e.get("movementStatuses").forEach(n -> statuses.add(n.asText()));
            assertThat(ase.movements().stream().map(MovementCommand::iEstProceso).toList())
                    .as("movement statuses, in call order").containsExactlyElementsOf(statuses);
        } else {
            JsonNode status = e.get("movementStatus");
            if (status.isNull()) {
                assertThat(ase.movements()).as("no movement").isEmpty();
            } else {
                assertThat(ase.onlyMovement().iEstProceso()).as("movement status").isEqualTo(status.asText());
            }
        }
        List<String> committed = new ArrayList<>();
        e.get("committed").forEach(n -> committed.add(n.asText()));
        assertThat(ase.committedProcedures()).as("committed writes").containsExactlyElementsOf(committed);
        assertThat(!ase.errorReports().isEmpty()).as("sp_cerror called").isEqualTo(e.get("cerror").asBoolean());
        assertThat(ase.transactionOpen()).as("transaction left open").isFalse();
        if (e.has("headerUpdates")) {
            assertThat(ase.headerUpdates()).as("header UPDATE statements issued").hasSize(e.get("headerUpdates").asInt());
        }
        if (e.has("headerStates")) {
            for (JsonNode h : e.get("headerStates")) {
                int order = h.get("order").asInt();
                String state = "HISTORY".equals(h.get("table").asText())
                        ? ase.historyHeaderState(order, text(h, "form"), text(h, "service"))
                        : ase.headerState(order, text(h, "form"), text(h, "service"));
                assertThat(state).as("state of %s", h).isEqualTo(text(h, "state"));
            }
        }
        // Phase 4 expectations (optional)
        if (e.has("events")) {
            assertThat(ase.events()).as("sp_eventos calls").hasSize(e.get("events").asInt());
        }
        if (e.has("basicNotifications")) {
            assertThat(ase.basicNotifications()).as("pa_sat_pnotificacion calls").hasSize(e.get("basicNotifications").asInt());
        }
        if (e.has("movementValor")) {
            assertThat(ase.movements().get(0).iValorMov()).as("@i_valor_mov of the movement")
                    .isEqualByComparingTo(text(e, "movementValor"));
        }
        if (e.has("priorRowCount")) {
            assertThat(ase.onlyOrderHeaderStep().priorRowCount()).as("@wRowdbBiz as B12 finds it")
                    .isEqualTo(integer(e, "priorRowCount"));
        }
        if (e.has("event")) {
            EventCommand sent = ase.onlyEvent();
            JsonNode ev = e.get("event");
            if (ev.has("canal")) {
                assertThat(sent.iCanal().replaceAll(" +$", "")).as("event canal").isEqualTo(text(ev, "canal"));
            }
            if (ev.has("servicio")) {
                assertThat(sent.iServicio()).as("event servicio").isEqualTo(text(ev, "servicio"));
            }
            if (ev.has("valor")) {
                assertThat(sent.iValor()).as("event valor").isEqualTo(text(ev, "valor"));
            }
            if (ev.has("costo")) {
                assertThat(sent.iCosto()).as("event costo").isEqualTo(text(ev, "costo"));
            }
            if (ev.has("prodDeb")) {
                assertThat(sent.iProdDeb()).as("event prod_deb").isEqualTo(text(ev, "prodDeb"));
            }
            if (ev.has("prodCre")) {
                assertThat(sent.iProdCre()).as("event prod_cre").isEqualTo(text(ev, "prodCre"));
            }
            if (ev.has("ctaCre")) {
                assertThat(sent.iCtaCre()).as("event cta_cre").isEqualTo(text(ev, "ctaCre"));
            }
            if (ev.has("empresa")) {
                assertThat(sent.iEmpresa()).as("event empresa").isEqualTo(text(ev, "empresa"));
            }
            if (ev.has("cliente")) {
                assertThat(sent.iCliente()).as("event cliente").isEqualTo(integer(ev, "cliente"));
            }
            if (ev.has("descCanal")) {
                assertThat(sent.iDescCanal()).as("event desc_canal").isEqualTo(text(ev, "descCanal"));
            }
        }
    }

    /** Phase 4 tables of B7, all optional: catalogue rows, order detail, account masters, notifier answers. */
    private static void scriptNotificationTables(FakeAseSession ase, JsonNode c) {
        if (c.has("smsServices")) {
            c.get("smsServices").forEach(s -> ase.smsService(text(s, "code"), text(s, "name")));
        }
        if (c.has("notificationClass")) {
            ase.notificationClassRow(c.get("service").asText(), text(c, "notificationClass"));
        }
        if (c.has("blocked")) {
            c.get("blocked").forEach(b -> ase.blockedNotification(text(b, "key"), text(b, "spName")));
        }
        if (c.has("bceInstitutions")) {
            c.get("bceInstitutions").forEach(n -> ase.bceInstitution(n.asText()));
        }
        if (c.has("interbankDetails")) {
            c.get("interbankDetails").forEach(d -> ase.interbankDetail(FakeAseSession.DetailTable.valueOf(text(d, "table")),
                    Requests.BANK_ORDER, text(d, "referenciaGrupo"), integer(d, "tipoCta"), text(d, "numeroCuenta")));
        }
        if (c.has("swiftDetails")) {
            c.get("swiftDetails").forEach(d -> ase.swiftDetail(FakeAseSession.DetailTable.valueOf(text(d, "table")),
                    Requests.BANK_ORDER, d.get("secuencial").asInt(), c.get("company").asInt(), text(d, "referenciaGrupo"),
                    text(d, "nomCuenta")));
        }
        if (c.has("beneficiaryDetails")) {
            c.get("beneficiaryDetails").forEach(d -> ase.beneficiaryDetail(FakeAseSession.DetailTable.valueOf(text(d, "table")),
                    Requests.BANK_ORDER, text(d, "nombre"), text(d, "referenciaGrupo")));
        }
        if (c.has("currentOwner")) {
            ase.currentOwner(Requests.ACCOUNT, integer(c, "currentOwner"));
        }
        if (c.has("virtualOwner")) {
            JsonNode v = c.get("virtualOwner");
            ase.virtualOwner(Requests.ACCOUNT, integer(v, "cliente"), integer(v, "prodBanc"));
        }
        if (c.has("eventReturnCode")) {
            ase.event(c.get("eventReturnCode").asInt());
        }
        if (c.has("basicReturnCode")) {
            ase.basicNotification(c.get("basicReturnCode").asInt(), integer(c, "basicOError"), null);
        }
    }

    /** A JSON null (or an absent field) is a T-SQL NULL int. */
    private static Integer integer(JsonNode c, String field) {
        JsonNode n = c.get(field);
        return n == null || n.isNull() ? null : n.asInt();
    }

    /** A JSON null is a T-SQL NULL for the money/text builders. */
    private static String text(JsonNode c, String field) {
        JsonNode n = c.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
