package com.nexti.debcred;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the debit flow per ASE session (architecture review M2): commissions (Phase 2,
 * {@link CommissionDebits}) and the order-header update (Phase 3, {@link OrderHeaderTransition}) are
 * real; notifications (Phase 4) answer "not configured", so the debit error code is never touched
 * (line 814 false).
 */
@Configuration(proxyBeanMethods = false)
class DebitFlowConfiguration {

    @Bean
    DebitFlow debitFlow() {
        NotificationStep notifications = context -> NotificationOutcome.notConfigured();   // Phase 4
        return ase -> new DebitCompanyAccountService(ase, notifications, new CommissionDebits(ase, ase, ase),
                new OrderHeaderTransition(ase));
    }
}
