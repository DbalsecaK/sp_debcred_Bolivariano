package com.nexti.debcred;

import java.math.BigDecimal;

/** The arguments of {@code pa_sat_pnotificacion}, in the legacy order (1066-1082). */
public record BasicNotificationCommand(String iCanal, String iCtadebito, Integer iTipctadeb, String iServicio,
                                       Integer iOrdenBanco, String iDireccionTransf, Integer iSecuencial,
                                       BigDecimal iValor, String iNombrecred, BigDecimal iComision, String iCtacred,
                                       String iProdCre, String iEmpresa) {
}
