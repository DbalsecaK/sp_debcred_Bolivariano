package com.nexti.debcred;

/**
 * Return value and {@code @o_error output} of {@code sp_grb_comision}. A null {@code oError} means
 * the output variable was not assigned and keeps the 0 it held before the call.
 */
public record CommissionResult(int returnCode, Integer oError) {
}
