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
package ai.philterd.router.model;

import java.io.File;
import java.util.Optional;

/**
 * Supplies the content-derived attributes a {@link FileAttributes} exposes lazily. Kept as an
 * interface so the routing logic can be exercised with in-memory fakes and without a running engine,
 * classifier, or model.
 */
public interface AttributeSources {

    /** Detects the content type from the file's bytes (not just its extension). */
    String detectContentType(File file, String filename);

    /** Extracts lossy plain text for classification/language detection (an excerpt is sufficient). */
    String extractText(File file);

    /** Detects the document language as an ISO 639-3 code, or empty when it cannot be determined. */
    Optional<String> detectLanguage(String text);

    /** Runs the named classifier over the text, returning its label or empty on failure/unknown. */
    Optional<String> classify(String classifierName, String text);

}
