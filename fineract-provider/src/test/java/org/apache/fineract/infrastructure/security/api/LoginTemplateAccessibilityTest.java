/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.security.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LoginTemplateAccessibilityTest {

    private static final Path TEMPLATES_DIR = Paths.get("src", "main", "resources", "templates");
    private static final Pattern HTML_START_TAG = Pattern.compile("<html\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LANG_ATTRIBUTE = Pattern.compile("(?:^|\\s)(?:xml:)?lang\\s*=\\s*\"[A-Za-z]{2,3}(?:-[A-Za-z0-9]+)*\"",
            Pattern.CASE_INSENSITIVE);

    @Test
    void everyTemplateDeclaresDocumentLanguage() throws IOException {
        List<Path> templates;
        try (Stream<Path> files = Files.walk(TEMPLATES_DIR)) {
            templates = files.filter(p -> p.toString().endsWith(".html")).toList();
        }
        assertFalse(templates.isEmpty(), "No templates found in " + TEMPLATES_DIR.toAbsolutePath());
        for (Path template : templates) {
            String content = Files.readString(template, StandardCharsets.UTF_8);
            Matcher html = HTML_START_TAG.matcher(content);
            assertTrue(html.find(), template + " has no <html> element");
            assertTrue(LANG_ATTRIBUTE.matcher(html.group(1)).find(), template + " <html> element must declare a lang attribute");
        }
    }
}
