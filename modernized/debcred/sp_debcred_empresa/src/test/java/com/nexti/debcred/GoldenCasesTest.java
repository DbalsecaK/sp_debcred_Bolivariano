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
 * three exits of Phase 1) and {@code phase2-cases.json} (the commission blocks and exit B): one
 * dynamic test per case, each named with its rule ids. The files are the shape the bank's recorded
 * outputs (brief section 7 A6) will take, so a future dual run can feed the same rows to the T-SQL
 * procedure and to the service.
 *
 * <p>A missing or empty fixture is a FAILURE, never a skip: a suite that is green because nothing
 * ran proves nothing. The factory also emits a final test that reports how many cases executed
 * across both files.
 */
class GoldenCasesTest {

    private static final List<String> FIXTURES = List.of("/golden/phase1-cases.json", "/golden/phase2-cases.json");

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

        DebitResult result = ase.service().debit(request.build());

        JsonNode e = c.get("expected");
        assertThat(result.returnCode()).as("return code").isEqualTo(e.get("returnCode").asInt());
        assertThat(result.oError()).as("@o_error").isEqualTo(e.get("oError").asInt());
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
    }

    /** A JSON null is a T-SQL NULL for the money/text builders. */
    private static String text(JsonNode c, String field) {
        JsonNode n = c.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
