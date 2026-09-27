package com.nexti.debcred.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.nexti.debcred.AsePortException;
import com.nexti.debcred.CommissionStep;
import com.nexti.debcred.NotificationOutcome;
import com.nexti.debcred.NotificationStep;
import com.nexti.debcred.OrderHeaderStep;
import com.nexti.debcred.ase.AseSessionFactory.SessionOpener;
import com.nexti.debcred.ase.AseUnavailableException;
import com.nexti.debcred.support.FakeAseSession;

/**
 * The REST edge (architecture review H3, H4): JSON binding of the 45-field request (money with its
 * scale, ISO date-time), business outcomes as HTTP 200 with return code and o_error (RULE-028),
 * unknown keys refused, infrastructure failures as problem details with a correlation id.
 */
@WebMvcTest(DebitController.class)
@Import(DebitControllerWebTest.Wiring.class)
class DebitControllerWebTest {

    /** One fake session per test, shared with the test through this holder. */
    static final ThreadLocal<FakeAseSession> SESSION = ThreadLocal.withInitial(FakeAseSession::new);
    static final ThreadLocal<RuntimeException> OPEN_FAILURE = new ThreadLocal<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class Wiring {
        @Bean
        SessionOpener sessionOpener() {
            return () -> {
                RuntimeException failure = OPEN_FAILURE.get();
                if (failure != null) {
                    throw failure;
                }
                return SESSION.get();
            };
        }

        @Bean
        NotificationStep notificationStep() {
            return context -> NotificationOutcome.notConfigured();
        }

        @Bean
        CommissionStep commissionStep() {
            return context -> 0;
        }

        @Bean
        OrderHeaderStep orderHeaderStep() {
            return context -> 0;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private static final String REQUEST = """
            {"sSsn": 1234, "sUser": "usrtest", "sTerm": "TERM01", "sSrv": "srvtest", "sOfi": 1,
             "iAplcobis": "N", "iSpName": "sp_test", "iOrden": 123456, "iOrdenEmpresa": 77,
             "iFechaProceso": "2026-09-27T10:15:30", "iNemEmp": "EMPTEST", "iEmpresa": 500, "iProducto": 1,
             "iServicio": "TRANSCLI", "iFrmPagcob": "CUE", "iFrmPagcobSpi": null, "iCanal": "DIR", "iTipoAfec": "10",
             "iReferencia": "REF TEST", "iMonDebito": 1, "iPaisCta": 1, "iCodBancoCta": 1,
             "iTipctaEmp": 3, "iNumctaEmp": "0000012345",
             "iValorOrdenado": 12.10, "iValorDebito": 12.10, "iComision": 0.50, "iValorComision": 0,
             "iTipoReferencia": "1", "iTarjeta": null, "iRefProv": "PROV", "iOpcion": "01", "iTipoPagcob": "CUE",
             "iLocalidadOrden": 1, "iTipoProceso": "P", "iFrmPagcobDeb": null,
             "iValor2Swift": 0, "iSecuencial": 0, "iCodSwift": null,
             "iValorTarifa": null, "iValorComisionCue": null, "iValorTarifaEfe": null,
             "iValorComisionEfe": null, "iValorTarifaChe": null, "iValorComisionChe": null}
            """;

    private MvcResult send(String json) throws Exception {
        return mockMvc.perform(post("/debitos-empresa").contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    @Test
    void rule028_aSuccessfulDebitIsHttp200WithReturnCodeZeroAndOErrorZero() throws Exception {
        SESSION.set(new FakeAseSession());
        OPEN_FAILURE.remove();

        MvcResult result = send(REQUEST);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"returnCode\":0,\"oError\":0,\"oRegAProc\":null}");
        // binding: money keeps its scale, the date-time is parsed, the debit reached sp_ndc_ahcc
        assertThat(SESSION.get().onlyDebitNote().iValor()).isEqualTo(new BigDecimal("12.10"));
        assertThat(SESSION.get().onlyDebitNote().sDate()).isEqualTo(LocalDateTime.of(2026, 9, 27, 10, 15, 30));
        assertThat(SESSION.get().onlyDebitNote().iTcomision()).isEqualByComparingTo("0");   // RULE-027 swap happened
    }

    @Test
    void rule028_aBusinessErrorIsStillHttp200WithTheCodeInTheBody() throws Exception {
        SESSION.set(new FakeAseSession().debitNote(201045, null));
        OPEN_FAILURE.remove();

        MvcResult result = send(REQUEST);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"returnCode\":201045,\"oError\":201045,\"oRegAProc\":null}");
    }

    @Test
    void h4_anUnknownJsonKeyIsRefusedWith400() throws Exception {
        SESSION.set(new FakeAseSession());
        OPEN_FAILURE.remove();

        MvcResult result = send(REQUEST.replace("\"iTipctaEmp\": 3", "\"iTipctaEmp\": 3, \"iTipctaEmpresa\": 4"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(SESSION.get().calls()).as("nothing ran").isEmpty();
    }

    @Test
    void rule007_rule028_anAseErrorTheLegacyMapsIsABusinessOutcomeNotAnHttpError() throws Exception {
        // @@error on the first sp_con_confcontable is mapped to 120000 before begin tran (CONTRACT §4 step 8).
        SESSION.set(new FakeAseSession().accountingLookupThrows());
        OPEN_FAILURE.remove();

        MvcResult result = send(REQUEST);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"returnCode\":0,\"oError\":120000,\"oRegAProc\":null}");
        assertThat(SESSION.get().begins()).isZero();
    }

    @Test
    void h3_anUnreachableAseIs503WithACorrelationId() throws Exception {
        OPEN_FAILURE.set(new AseUnavailableException("could not open an ASE connection", null));
        try {
            MvcResult result = send(REQUEST);

            assertThat(result.getResponse().getStatus()).isEqualTo(503);
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(body).contains("\"correlationId\"").contains("ASE unavailable").doesNotContain("jdbc");
        } finally {
            OPEN_FAILURE.remove();
        }
    }

    @Test
    void h3_aPortFailureThatPropagatesIs502() throws Exception {
        OPEN_FAILURE.set(new AsePortException("cobis..sp_ndc_ahcc failed"));
        try {
            MvcResult result = send(REQUEST);

            assertThat(result.getResponse().getStatus()).isEqualTo(502);
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("\"correlationId\"");
        } finally {
            OPEN_FAILURE.remove();
        }
    }
}
