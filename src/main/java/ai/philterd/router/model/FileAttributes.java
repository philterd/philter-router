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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Attributes of one file, computed lazily and memoized. Filename/extension/directory are immediate;
 * content type, text, language, and classifier labels are computed on first access, so routing on cheap
 * attributes never triggers extraction or a classifier call.
 */
public class FileAttributes {

    private final File file;
    private final AttributeSources sources;

    private final String filename;
    private final String extension;
    private final String directory;

    private boolean contentTypeDone;
    private String contentType;

    private boolean textDone;
    private String text;

    private boolean languageDone;
    private String language;

    private final Map<String, String> classificationMemo = new HashMap<>();
    private final Set<String> classificationDone = new HashSet<>();

    /** Caller-supplied classifier labels used in place of running that classifier. */
    private final Map<String, String> presetClassifications;

    public FileAttributes(final File file, final AttributeSources sources) {
        this(file, null, null, sources, Map.of());
    }

    public FileAttributes(final File file, final String filename, final String directory,
                          final AttributeSources sources) {
        this(file, filename, directory, sources, Map.of());
    }

    /**
     * @param filename              logical filename (e.g. an uploaded name); falls back to the file's name.
     * @param directory             source directory to route on; falls back to the file's parent.
     * @param presetClassifications caller-supplied classifier labels that skip running that classifier.
     */
    public FileAttributes(final File file, final String filename, final String directory,
                          final AttributeSources sources, final Map<String, String> presetClassifications) {
        this.file = file;
        this.sources = sources;
        this.presetClassifications = presetClassifications == null ? Map.of() : presetClassifications;
        this.filename = (filename != null && !filename.isBlank()) ? filename : file.getName();
        final int dot = this.filename.lastIndexOf('.');
        this.extension = dot >= 0 ? this.filename.substring(dot).toLowerCase() : "";
        if (directory != null && !directory.isBlank()) {
            this.directory = directory;
        } else {
            final File parent = file.getParentFile();
            this.directory = parent == null ? "" : parent.getAbsolutePath();
        }
    }

    public File file() {
        return file;
    }

    public String filename() {
        return filename;
    }

    /** The lowercased extension including the leading dot, or empty string when there is none. */
    public String extension() {
        return extension;
    }

    public String directory() {
        return directory;
    }

    public String contentType() {
        if (!contentTypeDone) {
            contentType = sources.detectContentType(file, filename);
            contentTypeDone = true;
        }
        return contentType;
    }

    /** The lossy extracted text, or null if none could be extracted. */
    public String text() {
        if (!textDone) {
            text = sources.extractText(file);
            textDone = true;
        }
        return text;
    }

    /** The detected ISO 639-3 language, or null when text is unavailable or detection is not confident. */
    public String language() {
        if (!languageDone) {
            final String t = text();
            language = (t == null || t.isBlank()) ? null : sources.detectLanguage(t).orElse(null);
            languageDone = true;
        }
        return language;
    }

    /** The label from the named classifier, or null when text is unavailable or the classifier failed. */
    public String classification(final String classifierName) {
        if (!classificationDone.contains(classifierName)) {
            final Optional<String> label;
            if (presetClassifications.containsKey(classifierName)) {
                // Caller supplied the label; do not run the classifier.
                label = Optional.ofNullable(presetClassifications.get(classifierName));
            } else {
                final String t = text();
                label = (t == null || t.isBlank()) ? Optional.empty() : sources.classify(classifierName, t);
            }
            label.ifPresent(l -> classificationMemo.put(classifierName, l));
            classificationDone.add(classifierName);
        }
        return classificationMemo.get(classifierName);
    }

    /** Whether language detection has already run for this file (used for audit without forcing work). */
    public boolean isLanguageComputed() {
        return languageDone;
    }

    /** The detected language only if already computed; does not trigger detection. */
    public String computedLanguage() {
        return languageDone ? language : null;
    }

    /** A snapshot of classifier labels computed so far; does not trigger any classifier. */
    public Map<String, String> computedClassifications() {
        return new HashMap<>(classificationMemo);
    }

}
