package com.nexti.debcred.localpg;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.nexti.debcred.ase.AseSessionFactory.SessionOpener;
import com.nexti.debcred.ase.AseUnavailableException;

/**
 * Profile {@code local-pg}: the local PostgreSQL test environment instead of Sybase ASE. The connection
 * comes from {@code DEBCRED_PG_URL}, {@code DEBCRED_PG_USERNAME} and {@code DEBCRED_PG_PASSWORD}; nothing
 * is kept in the repository.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local-pg")
public class LocalPgSessionFactory {

    @Bean
    DataSource localPgDataSource(@Value("${DEBCRED_PG_URL:}") String url,
                                 @Value("${DEBCRED_PG_USERNAME:}") String username,
                                 @Value("${DEBCRED_PG_PASSWORD:}") String password) {
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("local-pg needs DEBCRED_PG_URL, DEBCRED_PG_USERNAME and DEBCRED_PG_PASSWORD");
        }
        return DataSourceBuilder.create().driverClassName("org.postgresql.Driver")
                .url(url).username(username).password(password).build();
    }

    @Bean
    SessionOpener sessionOpener(DataSource localPgDataSource) {
        return () -> {
            try {
                return new PgAseSession(localPgDataSource.getConnection());
            } catch (SQLException e) {
                throw new AseUnavailableException("could not open a local PostgreSQL connection", e);
            }
        };
    }
}
