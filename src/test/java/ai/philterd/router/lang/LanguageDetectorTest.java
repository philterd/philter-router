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

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the real OpenNLP language-detector model end to end. */
class LanguageDetectorTest {

    private final LanguageDetector detector = new LanguageDetector(0.05);

    @Test
    void detectsEnglishAsIso6393() {
        final String text = "The quick brown fox jumps over the lazy dog. This document describes the "
                + "patient's medical history and the treatment plan recommended by the attending physician.";
        assertEquals("eng", detector.detect(text).orElse("none"));
    }

    @Test
    void detectsSpanishAsIso6393() {
        final String text = "El paciente presenta antecedentes medicos importantes y el medico ha recomendado "
                + "un plan de tratamiento detallado para las proximas semanas segun el informe clinico.";
        assertEquals("spa", detector.detect(text).orElse("none"));
    }

    @Test
    void returnsEmptyForBlankText() {
        final Optional<String> result = detector.detect("   ");
        assertTrue(result.isEmpty());
    }

}
