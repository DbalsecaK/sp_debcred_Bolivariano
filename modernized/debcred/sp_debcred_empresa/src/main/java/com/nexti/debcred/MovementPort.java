package com.nexti.debcred;

/** {@code cobis..sp_grb_mov_y_frmpgo} (lines 1394-1460): the movement and payment-form record. RULE-012, RULE-015. */
public interface MovementPort {

    MovementResult record(MovementCommand command);
}
