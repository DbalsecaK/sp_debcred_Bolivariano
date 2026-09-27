package com.nexti.debcred.ase;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.nexti.debcred.AseSession;

/**
 * Opens one {@link JdbcAseSession} per debit under the {@code ase} profile. The session is the
 * transaction and all the ports, so one connection carries the whole debit, as the legacy did.
 * Blank connection settings fail here, at startup, not at the first debit.
 */
@Configuration(proxyBeanMethods = false)
@Profile("ase")
public class AseSessionFactory {

    @Bean
    DataSource aseDataSource(AseProperties ase) {
        require(ase.url(), "DEBCRED_ASE_URL");
        require(ase.username(), "DEBCRED_ASE_USERNAME");
        require(ase.password(), "DEBCRED_ASE_PASSWORD");
        return DataSourceBuilder.create()
                .driverClassName("net.sourceforge.jtds.jdbc.Driver")
                .url(ase.url())
                .username(ase.username())
                .password(ase.password())
                .build();
    }

    @Bean
    SessionOpener sessionOpener(DataSource aseDataSource, AseProperties ase) {
        return () -> {
            try {
                return new JdbcAseSession(aseDataSource.getConnection(), ase.cobisDatabase());
            } catch (SQLException e) {
                throw new AseUnavailableException("could not open an ASE connection", e);
            }
        };
    }

    private static void require(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is not set; the 'ase' profile needs it");
        }
    }

    /** Opens a session; the caller closes it. */
    @FunctionalInterface
    public interface SessionOpener {
        AseSession open();
    }
}
