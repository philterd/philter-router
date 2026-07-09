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
package ai.philterd.router.lang;

import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Detects a document's language as an ISO 639-3 code with the local Apache OpenNLP model. Below the
 * confidence threshold it reports undetermined, which sends the file toward the default policy.
 */
public class LanguageDetector {

    /** Classpath location of the model bundled by the opennlp-models-langdetect artifact. */
    private static final String MODEL_RESOURCE = "/langdetect-183.bin";

    private final LanguageDetectorME detector;
    private final double minConfidence;

    public LanguageDetector(final double minConfidence) {
        this.minConfidence = minConfidence;
        try (final InputStream in = LanguageDetector.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("OpenNLP language-detector model " + MODEL_RESOURCE
                        + " was not found on the classpath.");
            }
            this.detector = new LanguageDetectorME(new LanguageDetectorModel(in));
        } catch (final IOException e) {
            throw new IllegalStateException("Could not load the OpenNLP language-detector model.", e);
        }
    }

    /** The detected ISO 639-3 code, or empty when confidence is below the threshold. */
    public synchronized Optional<String> detect(final String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        final Language language = detector.predictLanguage(text);
        if (language == null || language.getConfidence() < minConfidence) {
            return Optional.empty();
        }
        return Optional.of(language.getLang());
    }

}
