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
package ai.philterd.router.config;

import ai.philterd.router.api.ApiRouting;
import ai.philterd.router.audit.AuditLogger;
import ai.philterd.router.classify.Classifier;
import ai.philterd.router.engine.EngineRegistry;
import ai.philterd.router.extract.TextExtractor;
import ai.philterd.router.lang.LanguageDetector;
import ai.philterd.router.model.AttributeSources;
import ai.philterd.router.routing.DefaultAttributeSources;
import ai.philterd.router.routing.Router;
import ai.philterd.router.watch.FileProcessor;
import ai.philterd.router.watch.ProcessedLedger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the routing pipeline as Spring beans from the loaded {@link RouterConfig}. */
@Configuration
public class RouterBeans {

    /** Characters of text extracted for classification and language detection. */
    private static final int EXTRACT_MAX_CHARS = 20_000;
    /** Minimum OpenNLP confidence to accept a detected language. */
    private static final double LANGUAGE_MIN_CONFIDENCE = 0.05;

    @Bean
    public TextExtractor textExtractor() {
        return new TextExtractor(EXTRACT_MAX_CHARS);
    }

    @Bean
    public LanguageDetector languageDetector() {
        return new LanguageDetector(LANGUAGE_MIN_CONFIDENCE);
    }

    @Bean
    public Classifier classifier() {
        return new Classifier();
    }

    @Bean
    public AttributeSources attributeSources(final TextExtractor textExtractor,
                                             final LanguageDetector languageDetector,
                                             final Classifier classifier, final RouterConfig config) {
        return new DefaultAttributeSources(textExtractor, languageDetector, classifier, config.classifiers);
    }

    @Bean
    public Router router(final RouterConfig config) {
        return new Router(config);
    }

    @Bean
    public EngineRegistry engineRegistry(final RouterConfig config) {
        return new EngineRegistry(config.engines);
    }

    @Bean
    public AuditLogger auditLogger() {
        return new AuditLogger();
    }

    @Bean
    public ProcessedLedger processedLedger() {
        return new ProcessedLedger();
    }

    @Bean
    public FileProcessor fileProcessor(final Router router, final EngineRegistry engines,
                                       final AttributeSources sources, final ProcessedLedger ledger,
                                       final AuditLogger audit) {
        return new FileProcessor(router, engines, sources, ledger, audit);
    }

    @Bean
    public ApiRouting apiRouting(final Router router, final AttributeSources sources) {
        return new ApiRouting(router, sources);
    }

}
