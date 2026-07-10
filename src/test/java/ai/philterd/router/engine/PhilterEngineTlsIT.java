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
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Integration test: forwarding to a Philter engine that serves HTTPS with a self-signed certificate. */
class PhilterEngineTlsIT {

    private MockWebServer philter;
    private HeldCertificate certificate;

    @BeforeEach
    void setUp() throws Exception {
        certificate = new HeldCertificate.Builder().addSubjectAlternativeName("localhost").build();
        final HandshakeCertificates serverCertificates =
                new HandshakeCertificates.Builder().heldCertificate(certificate).build();
        philter = new MockWebServer();
        philter.useHttps(serverCertificates.sslSocketFactory(), false);
        philter.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        philter.shutdown();
    }

    private EngineConfig baseConfig() {
        final EngineConfig config = new EngineConfig();
        config.url = philter.url("/").toString();
        return config;
    }

    @Test
    void trustsSelfSignedCertificateViaCaCertPath(@TempDir final Path tmp) throws Exception {
        final Path pem = tmp.resolve("philter.pem");
        Files.writeString(pem, certificate.certificatePem());
        final EngineConfig config = baseConfig();
        config.caCertPath = pem.toString();

        final PhilterEngine engine = new PhilterEngine("philter1", config);
        philter.enqueue(new MockResponse().setBody(new Buffer().write(new byte[]{9})));

        assertArrayEquals(new byte[]{9}, engine.redact(file(tmp), "default", null).getContent());
    }

    @Test
    void rejectsUntrustedCertificateByDefault(@TempDir final Path tmp) throws Exception {
        final PhilterEngine engine = new PhilterEngine("philter1", baseConfig());
        philter.enqueue(new MockResponse().setBody(new Buffer().write(new byte[]{9})));

        assertThrows(IOException.class, () -> engine.redact(file(tmp), "default", null));
    }

    @Test
    void insecureSkipVerifyAcceptsUntrustedCertificate(@TempDir final Path tmp) throws Exception {
        final EngineConfig config = baseConfig();
        config.insecureSkipVerify = true;

        final PhilterEngine engine = new PhilterEngine("philter1", config);
        philter.enqueue(new MockResponse().setBody(new Buffer().write(new byte[]{9})));

        assertArrayEquals(new byte[]{9}, engine.redact(file(tmp), "default", null).getContent());
    }

    private static java.io.File file(final Path tmp) throws IOException {
        final Path file = tmp.resolve("a.txt");
        Files.write(file, new byte[]{1});
        return file.toFile();
    }

}
