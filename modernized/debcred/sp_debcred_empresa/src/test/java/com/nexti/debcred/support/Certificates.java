package com.nexti.debcred.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

/** Fake client certificates for the mTLS tests (brief section 7 A21): only the subject matters. */
public final class Certificates {

    private Certificates() {
    }

    /** A client certificate whose subject is {@code CN=<commonName>, O=Fake Bank Test}. */
    public static X509Certificate caller(String commonName) {
        X509Certificate certificate = mock(X509Certificate.class);
        X500Principal subject = new X500Principal("CN=" + commonName + ", O=Fake Bank Test");
        when(certificate.getSubjectX500Principal()).thenReturn(subject);
        when(certificate.getSubjectDN()).thenReturn(subject);
        return certificate;
    }
}
