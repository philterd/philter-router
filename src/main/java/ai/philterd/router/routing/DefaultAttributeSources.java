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
package ai.philterd.router.routing;

import ai.philterd.router.classify.Classifier;
import ai.philterd.router.config.ClassifierConfig;
import ai.philterd.router.extract.TextExtractor;
import ai.philterd.router.lang.LanguageDetector;
import ai.philterd.router.model.AttributeSources;

import java.io.File;
import java.util.Map;
import java.util.Optional;

/** Production {@link AttributeSources}: Tika extraction, OpenNLP language detection, Ollama classifiers. */
public class DefaultAttributeSources implements AttributeSources {

    private final TextExtractor extractor;
    private final LanguageDetector languageDetector;
    private final Classifier classifier;
    private final Map<String, ClassifierConfig> classifiers;

    public DefaultAttributeSources(final TextExtractor extractor, final LanguageDetector languageDetector,
                                   final Classifier classifier, final Map<String, ClassifierConfig> classifiers) {
        this.extractor = extractor;
        this.languageDetector = languageDetector;
        this.classifier = classifier;
        this.classifiers = classifiers == null ? Map.of() : classifiers;
    }

    @Override
    public String detectContentType(final File file, final String filename) {
        return extractor.detectContentType(file);
    }

    @Override
    public String extractText(final File file) {
        return extractor.extractText(file);
    }

    @Override
    public Optional<String> detectLanguage(final String text) {
        return languageDetector.detect(text);
    }

    @Override
    public Optional<String> classify(final String classifierName, final String text) {
        final ClassifierConfig config = classifiers.get(classifierName);
        return classifier.classify(config, text);
    }

}
