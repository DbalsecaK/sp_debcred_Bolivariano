package com.nexti.debcred;

import java.util.List;
import java.util.Optional;

/** The notification catalogues in {@code db_biz_admempresa..ba_tabla / ba_catalogo} (block B7). RULE-035, RULE-036, RULE-021. */
public interface NotificationCatalogReader {

    /**
     * 798-810: {@code ct_cod_catalogo} of every active {@code ad_servicios_sms} row whose name contains the
     * trimmed service and whose code ends in {@code canalSms}, lowest code first (brief section 7 A16).
     */
    List<String> smsServiceCodes(String servicio, String canalSms);

    /** 998-1010: {@code ct_otro_campo_catalogo} of the active {@code ad_notificacion_basica} row for the raw service. */
    Optional<String> notificationClass(String servicio);

    /** 1166-1180: an active {@code ba_bloqueaNotificacionSAT} row for {@code service-smsService} and the calling procedure. */
    boolean notificationBlocked(String servicio, String servicioSms, String spName);
}
