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
 * Table-driven equivalence cases from {@code src/test/resources/golden/phase1-cases.json}: one
 * dynamic test per case, each named with its rule ids. The file is the shape the bank's recorded
 * outputs (brief section 7 A6) will take, so a future dual run can feed the same rows to the T-SQL
 * procedure and to the service.
 *
 * <p>A missing or empty fixture is a FAILURE, never a skip: a suite that is green because nothing
 * ran proves nothing. The factory also emits a final test that reports how many cases executed.
 */
class GoldenCasesTest {

    private static final String FIXTURE = "/golden/phase1-cases.json";

    @TestFactory
    List<DynamicTest> goldenCases() throws IOException {
        JsonNode root = loadFixture();
        JsonNode cases = root.get("cases");
        if (cases == null || !cases.isArray() || cases.isEmpty()) {
            throw new AssertionError(FIXTURE + " has no 'cases': equivalence cases executed: 0");
        }
        List<DynamicTest> tests = new ArrayList<>();
        int[] executed = {0};
        for (JsonNode c : cases) {
            String name = c.get("rules").toString() + " " + c.get("name").asText();
            tests.add(DynamicTest.dynamicTest(name, () -> {
                runCase(c);
                executed[0]++;
            }));
        }
        int total = cases.size();
        tests.add(DynamicTest.dynamicTest("equivalence cases executed: " + total, () -> {
            System.out.println("equivalence cases executed: " + executed[0] + " of " + total);
            assertThat(executed[0]).as("equivalence cases executed").isEqualTo(total).isPositive();
        }));
        return tests;
    }

    private static JsonNode loadFixture() throws IOException {
        try (InputStream in = GoldenCasesTest.class.getResourceAsStream(FIXTURE)) {
            if (in == null) {
                throw new AssertionError("fixture " + FIXTURE + " is missing: equivalence cases executed: 0");
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
        DebitRequest request = Requests.currentAccount()
                .tipctaEmp(c.get("accountType").asInt())
                .servicio(c.get("service").asText())
                .aplcobis(c.get("aplcobis").asText())
                .empresa(c.get("company").asInt())
                .build();

        DebitResult result = ase.service().debit(request);

        JsonNode e = c.get("expected");
        assertThat(result.returnCode()).as("return code").isEqualTo(e.get("returnCode").asInt());
        assertThat(result.oError()).as("@o_error").isEqualTo(e.get("oError").asInt());
        assertThat(result.oRegAProc()).as("@o_reg_a_proc").isNull();
        assertThat(ase.begins()).as("begin tran").isEqualTo(e.get("begins").asInt());
        assertThat(ase.commits()).as("commit tran").isEqualTo(e.get("commits").asInt());
        assertThat(ase.rollbacks()).as("rollback tran").isEqualTo(e.get("rollbacks").asInt());
        JsonNode status = e.get("movementStatus");
        if (status.isNull()) {
            assertThat(ase.movements()).as("no movement").isEmpty();
        } else {
            assertThat(ase.onlyMovement().iEstProceso()).as("movement status").isEqualTo(status.asText());
        }
        List<String> committed = new ArrayList<>();
        e.get("committed").forEach(n -> committed.add(n.asText()));
        assertThat(ase.committedProcedures()).as("committed writes").containsExactlyElementsOf(committed);
        assertThat(!ase.errorReports().isEmpty()).as("sp_cerror called").isEqualTo(e.get("cerror").asBoolean());
        assertThat(ase.transactionOpen()).as("transaction left open").isFalse();
    }
}
