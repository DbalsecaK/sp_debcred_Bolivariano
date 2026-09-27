package com.nexti.debcred;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Refuses to start without the {@code ase} profile: the service has no meaning without the COBIS
 * procedures and tables it calls (brief section 2). This runs before any other bean, so the reason
 * is the first thing an operator sees.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!ase")
class UnconfiguredProfileGuard {

    @Bean
    static BeanFactoryPostProcessor refuseWithoutAse() {
        return beanFactory -> {
            throw new IllegalStateException("sp-debcred-empresa needs SPRING_PROFILES_ACTIVE=ase and DEBCRED_ASE_URL, "
                    + "DEBCRED_ASE_USERNAME and DEBCRED_ASE_PASSWORD: every debit runs through the Sybase ASE "
                    + "procedures (MODERNIZATION_BRIEF.md section 2).");
        };
    }
}
