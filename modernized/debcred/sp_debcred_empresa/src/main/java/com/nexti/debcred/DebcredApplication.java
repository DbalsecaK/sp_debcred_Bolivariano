package com.nexti.debcred;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The company-account debit service, formerly {@code dbo.sp_debcred_empresa} (brief section 7, A2:
 * the procedure becomes a REST API and its COBIS callers are re-pointed to it).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DebcredApplication {

    public static void main(String[] args) {
        SpringApplication.run(DebcredApplication.class, args);
    }
}
