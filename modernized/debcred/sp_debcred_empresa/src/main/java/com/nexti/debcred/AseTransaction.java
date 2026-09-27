package com.nexti.debcred;

/**
 * The Sybase transaction the legacy opened around the debit (sp_debcred_empresa.sp:402-408, 1338,
 * 1486, 2130, 2144). One connection, one transaction; the savepoint name is
 * {@link DebitCompanyAccountService#SAVEPOINT}. RULE-012, RULE-015, RULE-028.
 */
public interface AseTransaction {

    /** {@code begin tran} (line 402). */
    void begin();

    /** {@code save tran @w_savepoint} (line 408). */
    void savepoint(String name);

    /** {@code rollback tran @w_savepoint} (line 1338): the debit is undone, the transaction stays open. */
    void rollbackToSavepoint(String name);

    /** {@code rollback tran} (line 2144). */
    void rollback();

    /** {@code commit tran} (lines 1486, 2130). */
    void commit();
}
