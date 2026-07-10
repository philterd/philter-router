/*
 * Copyright 2026 Philterd, LLC @ https://www.philterd.ai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.philterd.router.engine;

import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.Test;

import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TlsSupportTest {

    @Test
    void insecureTrustManagerAcceptsAnyCertificate() {
        final X509TrustManager trustManager = TlsSupport.insecureTrustManager();
        assertEquals(0, trustManager.getAcceptedIssuers().length);
        assertDoesNotThrow(() -> trustManager.checkServerTrusted(new java.security.cert.X509Certificate[0], "RSA"));
    }

    @Test
    void loadsTrustManagerFromPemCertificate() throws Exception {
        final HeldCertificate certificate = new HeldCertificate.Builder().build();
        final var in = new ByteArrayInputStream(certificate.certificatePem().getBytes(StandardCharsets.UTF_8));

        final X509TrustManager trustManager = TlsSupport.trustManagerForCertificates(in);

        assertEquals(1, trustManager.getAcceptedIssuers().length);
    }

    @Test
    void emptyCertificateStreamIsRejected() {
        final var in = new ByteArrayInputStream(new byte[0]);
        assertThrows(Exception.class, () -> TlsSupport.trustManagerForCertificates(in));
    }

}
