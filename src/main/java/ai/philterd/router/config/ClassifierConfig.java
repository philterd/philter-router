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

import java.util.List;

/**
 * A named local LLM classifier. It runs one prompt that returns a single label from {@link #labels}.
 * A classifier reads un-redacted document text, so its endpoint must stay inside the customer boundary.
 */
public class ClassifierConfig {

    public String type = "ollama";
    public String endpoint;
    public String model;
    public long timeoutMs = 2000;
    public List<String> labels;

    /** The prompt. The token {@code {{text}}} is replaced with the (excerpt of) extracted document text. */
    public String prompt;

}
