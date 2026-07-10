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

import ai.philterd.router.config.EngineConfig;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

/** TLS trust configuration for the forwarded Philter request: a pinned CA cert, or verification disabled. */
final class TlsSupport {

    private static final Logger LOGGER = LogManager.getLogger(TlsSupport.class);

    private TlsSupport() {
    }

    /** Applies the engine's TLS trust settings to the client builder. Fails closed on a bad certificate. */
    static void apply(final OkHttpClient.Builder builder, final EngineConfig config, final String engineName) {
        try {
            if (config.insecureSkipVerify) {
                final X509TrustManager trustManager = insecureTrustManager();
                builder.sslSocketFactory(socketFactory(trustManager), trustManager);
                builder.hostnameVerifier((hostname, session) -> true);
                LOGGER.warn("Engine '{}' has insecureSkipVerify enabled: TLS verification to Philter is "
                        + "disabled and the connection is exposed to interception. Use caCertPath instead "
                        + "outside a trusted network.", engineName);
            } else if (config.caCertPath != null && !config.caCertPath.isBlank()) {
                try (InputStream in = Files.newInputStream(Path.of(config.caCertPath))) {
                    final X509TrustManager trustManager = trustManagerForCertificates(in);
                    builder.sslSocketFactory(socketFactory(trustManager), trustManager);
                }
            }
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to configure TLS for engine '" + engineName + "': "
                    + e.getMessage(), e);
        }
    }

    /** A trust manager that accepts any certificate. Disables server authentication. */
    static X509TrustManager insecureTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    /** Builds a trust manager that trusts only the PEM certificate(s) in the stream. */
    static X509TrustManager trustManagerForCertificates(final InputStream pem) throws Exception {
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final Collection<? extends Certificate> certificates = factory.generateCertificates(pem);
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("No certificates found");
        }

        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        int index = 0;
        for (final Certificate certificate : certificates) {
            keyStore.setCertificateEntry("ca" + (index++), certificate);
        }

        final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        for (final TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new IllegalStateException("No X509TrustManager available");
    }

    private static SSLSocketFactory socketFactory(final X509TrustManager trustManager) throws Exception {
        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trustManager}, null);
        return context.getSocketFactory();
    }

}
