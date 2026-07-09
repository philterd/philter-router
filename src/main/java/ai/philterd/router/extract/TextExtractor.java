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
package ai.philterd.router.extract;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Content-type detection and lossy plain-text extraction using Apache Tika. The extracted text is
 * throwaway input for classification and language detection only; it is never the redaction output and
 * is never logged.
 */
public class TextExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextExtractor.class);

    private final Tika tika;

    /** @param maxChars the maximum number of characters to extract (an excerpt is enough to classify). */
    public TextExtractor(final int maxChars) {
        this.tika = new Tika();
        this.tika.setMaxStringLength(maxChars);
    }

    /** Detects the content type from the file's bytes, or null if it cannot be determined. */
    public String detectContentType(final File file) {
        try {
            return tika.detect(file);
        } catch (final IOException e) {
            LOGGER.warn("Could not detect content type for a file: {}", e.getMessage());
            return null;
        }
    }

    /** Extracts an excerpt of plain text, or null if none could be extracted. Content is never logged. */
    public String extractText(final File file) {
        try {
            return tika.parseToString(file);
        } catch (final IOException | TikaException e) {
            LOGGER.warn("Could not extract text from a file: {}", e.getMessage());
            return null;
        }
    }

}
