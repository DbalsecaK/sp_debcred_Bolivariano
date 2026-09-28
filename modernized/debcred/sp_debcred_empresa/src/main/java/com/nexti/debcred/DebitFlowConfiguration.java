package com.nexti.debcred;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the debit flow per ASE session (architecture review M2). Commissions are real since Phase 2
 * ({@link CommissionDebits}); the order-header update (Phase 3) returns 0 and notifications (Phase 4)
 * answer "not configured", so the debit error code is never touched (line 814 false).
 */
@Configuration(proxyBeanMethods = false)
class DebitFlowConfiguration {

    @Bean
    DebitFlow debitFlow() {
        NotificationStep notifications = context -> NotificationOutcome.notConfigured();   // Phase 4
        OrderHeaderStep orderHeader = context -> 0;                                         // Phase 3
        return ase -> new DebitCompanyAccountService(ase, notifications, new CommissionDebits(ase, ase, ase), orderHeader);
    }
}
