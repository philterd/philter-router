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
 * The match conditions of a route. All specified fields must match (AND); within a single field a
 * list is any-of (OR).
 */
public class RouteMatch {

    /** Detected content types (from Tika), e.g. {@code application/pdf}. */
    public List<String> contentTypes;

    /** Filename extensions, e.g. {@code .docx} (leading dot optional). */
    public List<String> extensions;

    /** Source directory prefixes. */
    public List<String> directories;

    /** A classifier label match. */
    public ClassificationMatch classification;

    public boolean isEmpty() {
        return (contentTypes == null || contentTypes.isEmpty())
                && (extensions == null || extensions.isEmpty())
                && (directories == null || directories.isEmpty())
                && classification == null;
    }

}
