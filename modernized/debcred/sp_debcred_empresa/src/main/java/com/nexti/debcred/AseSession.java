package com.nexti.debcred;

/**
 * One Sybase ASE connection for one debit: the transaction and every call the legacy made from
 * inside it (the ten procedures and the table reads), so {@code save tran} and
 * {@code rollback tran @w_savepoint} behave as they did inside the procedure (brief section 7, A1).
 * Closing a session with a transaction still open rolls it back.
 */
public interface AseSession extends AseTransaction, AutoCloseable, CommissionTariffPort, AccountingConfigurationPort,
        DebitNotePort, VirtualDebitNotePort, LedgerDebitPort, MovementPort, ErrorReportingPort, CatalogReader,
        VirtualAccountReader, OrderReader {

    @Override
    default void close() {
    }
}
