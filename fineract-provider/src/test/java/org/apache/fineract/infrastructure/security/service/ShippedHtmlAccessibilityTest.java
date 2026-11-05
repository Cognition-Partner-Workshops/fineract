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
package org.apache.fineract.infrastructure.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.junit.jupiter.api.Test;

class ShippedHtmlAccessibilityTest {

    private static final String LEGACY_HTML = "/static/legacy-docs/apiLive.htm";

    @Test
    void shippedPagesDeclareEnglishLanguage() throws IOException {
        for (String path : List.of(LEGACY_HTML, "/templates/login.html")) {
            assertTrue(resource(path).matches("(?s).*<html\\b[^>]*\\blang=\"en\"[^>]*>.*"), path);
        }
    }

    @Test
    void legacyDataTablesHaveScopedHeadersAndLayoutTablesArePresentational() throws IOException {
        List<Table> tables = new ArrayList<>();
        Deque<Table> stack = new ArrayDeque<>();
        parse(new HTMLEditorKit.ParserCallback() {

            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int position) {
                if (tag == HTML.Tag.TABLE) {
                    Table table = new Table("presentation".equals(attrs.getAttribute("role")));
                    tables.add(table);
                    stack.push(table);
                } else if (!stack.isEmpty()) {
                    if (tag == HTML.Tag.TR) {
                        stack.peek().rowCells = 0;
                    } else if (tag == HTML.Tag.TD || tag == HTML.Tag.TH) {
                        Table table = stack.peek();
                        table.maxRowCells = Math.max(table.maxRowCells, ++table.rowCells);
                        if (tag == HTML.Tag.TH) {
                            table.headers++;
                            assertEquals("col", attrs.getAttribute("scope"), "Legacy matrices use column headers");
                        }
                    }
                }
            }

            @Override
            public void handleEndTag(HTML.Tag tag, int position) {
                if (tag == HTML.Tag.TABLE) {
                    stack.pop();
                }
            }
        });
        assertEquals(389, tables.size(), "All shipped documentation tables are checked");
        assertEquals(27, tables.stream().filter(table -> !table.presentation).count(), "Do not hide data tables as layout");
        for (Table table : tables) {
            if (table.presentation) {
                assertEquals(0, table.headers, "Layout tables must not invent data headers");
                assertEquals(1, table.maxRowCells, "Only single-column prose/list layouts are presentational");
            } else {
                assertTrue(table.headers > 0, "Data matrices need native scoped column headers");
            }
        }
    }

    @Test
    void legacyIdsAreUniqueAndExistingFragmentTargetsRemainAvailable() throws IOException {
        Set<String> ids = new HashSet<>();
        parse(new HTMLEditorKit.ParserCallback() {

            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int position) {
                Object id = attrs.getAttribute(HTML.Attribute.ID);
                if (id != null) {
                    assertTrue(ids.add(id.toString()), "Duplicate id: " + id);
                }
            }
        });
        assertTrue(ids.containsAll(List.of("staff_retrieve", "staff_retrieve_by_status", "clients_template", "templates_template")));
    }

    @Test
    void legacyInlineCodeUsesHtml5Markup() throws IOException {
        assertFalse(resource(LEGACY_HTML).matches("(?s).*<\\/?tt\\b.*"));
    }

    @Test
    void legacyStylesUseCssCommentsAndKnownTypeSelectors() throws IOException {
        String css = resource("/static/legacy-docs/apidocs.css");
        assertFalse(css.matches("(?s).*\\n\\s*//.*"), "CSS does not support line comments");
        assertFalse(css.contains("tdjpw"));
        assertFalse(css.contains("tablejpw"));
    }

    private static void parse(HTMLEditorKit.ParserCallback callback) throws IOException {
        new ParserDelegator().parse(new StringReader(resource(LEGACY_HTML)), callback, true);
    }

    private static String resource(String path) throws IOException {
        try (var stream = ShippedHtmlAccessibilityTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class Table {

        private final boolean presentation;
        private int headers;
        private int rowCells;
        private int maxRowCells;

        private Table(boolean presentation) {
            this.presentation = presentation;
        }
    }
}
