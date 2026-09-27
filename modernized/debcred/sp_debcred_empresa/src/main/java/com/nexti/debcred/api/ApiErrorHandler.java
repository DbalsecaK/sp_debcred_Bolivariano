package com.nexti.debcred.api;

import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nexti.debcred.AsePortException;
import com.nexti.debcred.ase.AseUnavailableException;

/**
 * Infrastructure failures as RFC 9457 problem details (architecture review H2, H3). A COBIS caller
 * re-pointed to this API can tell "ASE unreachable" (503) from "a call inside the debit failed"
 * (502) from "a bug in the service" (500). The SQL text never reaches the caller; the log line
 * carries the correlation id, SQLState and vendor code for the on-call engineer.
 */
@RestControllerAdvice
class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(AseUnavailableException.class)
    ProblemDetail aseUnavailable(AseUnavailableException failure) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "ASE unavailable", failure);
    }

    @ExceptionHandler(AsePortException.class)
    ProblemDetail aseCallFailed(AsePortException failure) {
        return problem(HttpStatus.BAD_GATEWAY, "ASE call failed", failure);
    }

    @ExceptionHandler(RuntimeException.class)
    ProblemDetail unexpected(RuntimeException failure) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected failure", failure);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException failure) {
        String correlation = UUID.randomUUID().toString();
        if (failure.getCause() instanceof SQLException sql) {
            log.error("{} [{}]: {} (SQLState {}, code {})", title, correlation, failure.getMessage(),
                    sql.getSQLState(), sql.getErrorCode());
        } else {
            log.error("{} [{}]: {}", title, correlation, failure.getMessage());
        }
        log.debug("[{}] cause", correlation, failure);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "See the service log for correlation id " + correlation);
        problem.setTitle(title);
        problem.setProperty("correlationId", correlation);
        return problem;
    }
}
