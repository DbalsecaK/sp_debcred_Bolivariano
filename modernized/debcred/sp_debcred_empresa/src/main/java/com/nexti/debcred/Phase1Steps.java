package com.nexti.debcred;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The steps later phases fill in, wired as no-ops for Phase 1 (brief section 3): notifications
 * answer "not configured", so the debit error code is never touched (line 814 false); commissions
 * and the order-header update return 0 and are only shown to be reached.
 */
@Configuration(proxyBeanMethods = false)
class Phase1Steps {

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
