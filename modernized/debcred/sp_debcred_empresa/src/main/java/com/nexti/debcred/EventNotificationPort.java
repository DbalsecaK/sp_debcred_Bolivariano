package com.nexti.debcred;

/** {@code cob_internet..sp_eventos} (1210-1242). RULE-034. */
public interface EventNotificationPort {

    EventResult registerEvent(EventCommand command);
}
