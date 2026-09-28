package com.nexti.debcred.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.x509;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nexti.debcred.support.Certificates;
import com.nexti.debcred.support.FakeAseSession;

/**
 * Harden JSEC-001 / brief section 7 A21: the debit endpoint is reachable only with a client certificate
 * whose CN is one of the configured COBIS callers. No certificate, or an unlisted one, is 403 and nothing
 * runs; an empty caller list refuses everyone (fail closed). Fake certificates only.
 */
class MutualTlsWebTest {

    private static final String REQUEST = "{\"iOrden\": 123456, \"iTipctaEmp\": 3, \"iNumctaEmp\": \"0000012345\","
            + " \"iServicio\": \"TRANSCLI\", \"iValorDebito\": 12.10, \"iValorOrdenado\": 12.10}";

    private static MockHttpServletRequestBuilder debit() {
        return post("/debitos-empresa").contentType(MediaType.APPLICATION_JSON).content(REQUEST);
    }

    @Nested
    @WebMvcTest(controllers = DebitController.class,
            properties = "debcred.security.allowed-callers=cobis-fake-caller, cobis-fake-batch")
    @Import({DebitControllerWebTest.Wiring.class, SecurityConfiguration.class})
    class WithCallersConfigured {

        @Autowired
        private MockMvc mockMvc;

        @BeforeEach
        void freshSession() {
            DebitControllerWebTest.SESSION.set(new FakeAseSession());
            DebitControllerWebTest.OPEN_FAILURE.remove();
        }

        @Test
        void jsec001_noClientCertificateIs403AndNothingRuns() throws Exception {
            int status = mockMvc.perform(debit()).andReturn().getResponse().getStatus();

            assertThat(status).isEqualTo(403);
            assertThat(DebitControllerWebTest.SESSION.get().calls()).isEmpty();
        }

        @Test
        void jsec001_anUnlistedCertificateIs403AndNothingRuns() throws Exception {
            int status = mockMvc.perform(debit().with(x509(Certificates.caller("intruder-fake"))))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isEqualTo(403);
            assertThat(DebitControllerWebTest.SESSION.get().calls()).isEmpty();
        }

        @Test
        void jsec001_eachListedCallerIsServed() throws Exception {
            for (String caller : new String[] {"cobis-fake-caller", "cobis-fake-batch"}) {
                int status = mockMvc.perform(debit().with(x509(Certificates.caller(caller))))
                        .andReturn().getResponse().getStatus();
                assertThat(status).as(caller).isEqualTo(200);
            }
        }

        @Test
        void jsec001_theCallerNameMustMatchExactly() throws Exception {
            int status = mockMvc.perform(debit().with(x509(Certificates.caller("COBIS-FAKE-CALLER"))))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isEqualTo(403);
        }
    }

    @Nested
    @WebMvcTest(controllers = DebitController.class, properties = "debcred.security.allowed-callers=")
    @Import({DebitControllerWebTest.Wiring.class, SecurityConfiguration.class})
    class WithNoCallersConfigured {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void jsec001_anEmptyCallerListRefusesEveryone() throws Exception {
            DebitControllerWebTest.SESSION.set(new FakeAseSession());
            DebitControllerWebTest.OPEN_FAILURE.remove();

            int status = mockMvc.perform(debit().with(x509(Certificates.caller("cobis-fake-caller"))))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isEqualTo(403);
            assertThat(DebitControllerWebTest.SESSION.get().calls()).isEmpty();
        }
    }
}
