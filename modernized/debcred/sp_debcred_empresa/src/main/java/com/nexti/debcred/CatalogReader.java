package com.nexti.debcred;

import java.util.Optional;

/** {@code db_biz_admempresa..ba_tabla / ba_catalogo} (lines 484-496). RULE-031. */
public interface CatalogReader {

    /** {@code ct_cod_catalogo} of the active {@code ad_concepto_contable} row flagged {@code NDCORPEI}. */
    Optional<String> ndcorpeiConcept();
}
