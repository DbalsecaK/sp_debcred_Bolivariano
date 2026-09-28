package com.nexti.debcred;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the debit flow per ASE session (architecture review M2): notifications (Phase 4,
 * {@link CustomerNotifications}), commissions (Phase 2, {@link CommissionDebits}) and the order-header
 * update (Phase 3, {@link OrderHeaderTransition}), all over the session's ports.
 */
@Configuration(proxyBeanMethods = false)
class DebitFlowConfiguration {

    @Bean
    DebitFlow debitFlow() {
        return ase -> new DebitCompanyAccountService(ase, new CustomerNotifications(ase, ase, ase, ase, ase),
                new CommissionDebits(ase, ase, ase), new OrderHeaderTransition(ase));
    }
}
