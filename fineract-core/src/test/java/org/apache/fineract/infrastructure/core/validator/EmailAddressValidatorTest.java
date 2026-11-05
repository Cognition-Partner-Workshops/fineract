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
package org.apache.fineract.infrastructure.core.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EmailAddressValidatorTest {

    private static final Pattern PREVIOUS_PATTERN = Pattern
            .compile("^[\\w!#$%&’*+/=?`{|}~^-]+(?:\\.[\\w!#$%&’*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$");

    @Test
    void acceptsRegularAddresses() {
        assertTrue(EmailAddressValidator.isValid("first.last@example.com"));
        assertTrue(EmailAddressValidator.isValid("o'neil+tag@mail.example.co.uk".replace("'", "’")));
    }

    @Test
    void rejectsMalformedAddresses() {
        assertFalse(EmailAddressValidator.isValid(null));
        assertFalse(EmailAddressValidator.isValid(""));
        assertFalse(EmailAddressValidator.isValid("user@example.com."));
        assertFalse(EmailAddressValidator.isValid("user@@example.com"));
        assertFalse(EmailAddressValidator.isValid(".user@example.com"));
        assertFalse(EmailAddressValidator.isValid("user..name@example.com"));
        assertFalse(EmailAddressValidator.isValid("user@example"));
        assertFalse(EmailAddressValidator.isValid("user@example.c"));
    }

    @Test
    void matchesSameAddressesAsPreviousPattern() {
        List<String> samples = List.of("a@b.cd", "a.b.c@d.e.fg", "a-b@c-d.efghij", "a@b.efghijk", "a.@b.cd", "a@.b.cd", "a@b..cd",
                "a b@c.de", "a@b.c1", "_@x.yz", "a@b-.cd", "test@localhost", "a.b@c.d.e");
        for (String sample : samples) {
            assertEquals(PREVIOUS_PATTERN.matcher(sample).matches() && !sample.endsWith("."), EmailAddressValidator.isValid(sample),
                    sample);
        }
    }

    @Test
    void longDottedLocalPartDoesNotOverflowStack() {
        String email = "a" + ".a".repeat(50_000) + "@example.com";
        assertTrue(assertDoesNotThrow(() -> EmailAddressValidator.isValid(email)));
    }

    @Test
    void longDottedDomainDoesNotOverflowStack() {
        String email = "a@" + "a.".repeat(50_000) + "com";
        assertTrue(assertDoesNotThrow(() -> EmailAddressValidator.isValid(email)));
        assertFalse(assertDoesNotThrow(() -> EmailAddressValidator.isValid(email + "1")));
    }
}
