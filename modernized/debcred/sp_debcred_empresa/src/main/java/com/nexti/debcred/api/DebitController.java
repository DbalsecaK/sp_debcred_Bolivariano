package com.nexti.debcred.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexti.debcred.AseSession;
import com.nexti.debcred.DebitFlow;
import com.nexti.debcred.DebitRequest;
import com.nexti.debcred.DebitResult;
import com.nexti.debcred.ase.AseSessionFactory.SessionOpener;

/**
 * The REST face of {@code dbo.sp_debcred_empresa} (brief section 7, A2): one call, the 45 inputs as
 * JSON, the return value and {@code @o_error} as the reply (A10). Business outcomes, error codes
 * included, are HTTP 200; only an infrastructure failure is an HTTP error ({@link ApiErrorHandler}).
 * One ASE session per request: the transaction and every procedure call share the connection.
 */
@RestController
@RequestMapping("/debitos-empresa")
public class DebitController {

    private final SessionOpener sessions;
    private final DebitFlow flow;

    public DebitController(SessionOpener sessions, DebitFlow flow) {
        this.sessions = sessions;
        this.flow = flow;
    }

    @PostMapping
    public DebitResult debit(@RequestBody DebitRequest request) {
        try (AseSession ase = sessions.open()) {
            return flow.over(ase).debit(request);
        }
    }
}
