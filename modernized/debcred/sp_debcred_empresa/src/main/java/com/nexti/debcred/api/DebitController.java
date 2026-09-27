package com.nexti.debcred.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexti.debcred.AseSession;
import com.nexti.debcred.CommissionStep;
import com.nexti.debcred.DebitCompanyAccountService;
import com.nexti.debcred.DebitRequest;
import com.nexti.debcred.DebitResult;
import com.nexti.debcred.NotificationStep;
import com.nexti.debcred.OrderHeaderStep;
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
    private final NotificationStep notification;
    private final CommissionStep commission;
    private final OrderHeaderStep orderHeader;

    public DebitController(SessionOpener sessions, NotificationStep notification, CommissionStep commission,
                           OrderHeaderStep orderHeader) {
        this.sessions = sessions;
        this.notification = notification;
        this.commission = commission;
        this.orderHeader = orderHeader;
    }

    @PostMapping
    public DebitResult debit(@RequestBody DebitRequest request) {
        try (AseSession ase = sessions.open()) {
            return new DebitCompanyAccountService(ase, notification, commission, orderHeader).debit(request);
        }
    }
}
