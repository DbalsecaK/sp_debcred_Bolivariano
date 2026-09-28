package com.nexti.debcred;

/** {@code pa_sat_pnotificacion} in the procedure's home database (1052-1082, brief section 7 A8). RULE-036. */
public interface BasicNotificationPort {

    BasicNotificationResult notifyBasic(BasicNotificationCommand command);
}
