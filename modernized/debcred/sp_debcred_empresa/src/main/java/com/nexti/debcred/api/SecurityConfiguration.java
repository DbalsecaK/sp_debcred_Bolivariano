package com.nexti.debcred.api;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may debit (harden JSEC-001, brief section 7 A21): the COBIS callers authenticate with <b>mTLS</b>.
 * The server requires a client certificate ({@code server.ssl.client-auth: need}, application.yml) and
 * only a certificate whose CN is listed in {@code debcred.security.allowed-callers} is served; anything
 * else is 403 and nothing runs. An empty list refuses everyone (fail closed).
 *
 * <p>The {@code local-pg} profile (localhost-only test environment, not production) has no TLS and serves
 * any local request.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);
    static final String CALLER_ROLE = "COBIS_CALLER";

    @Bean
    @Profile("!local-pg")
    SecurityFilterChain mutualTls(HttpSecurity http) throws Exception {
        return http
                .x509(x509 -> { })                                   // CN of the client certificate
                .authorizeHttpRequests(requests -> requests.anyRequest().hasRole(CALLER_ROLE))
                .csrf(csrf -> csrf.disable())                        // no browser, no cookie: a certificate per call
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Profile("local-pg")
    SecurityFilterChain localOnly(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /** The listed certificate names, matched exactly (case included). */
    @Bean
    UserDetailsService allowedCallers(@Value("${debcred.security.allowed-callers:}") String configured) {
        Set<String> callers = Arrays.stream(configured.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (callers.isEmpty()) {
            log.warn("debcred.security.allowed-callers is empty: every debit request will be refused");
        }
        return name -> {
            if (!callers.contains(name)) {
                throw new UsernameNotFoundException("client certificate not allowed");
            }
            return User.withUsername(name).password("").roles(CALLER_ROLE).build();
        };
    }
}
