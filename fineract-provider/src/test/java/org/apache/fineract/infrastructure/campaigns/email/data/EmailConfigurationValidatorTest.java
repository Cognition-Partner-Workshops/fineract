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
package org.apache.fineract.infrastructure.campaigns.email.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

class EmailConfigurationValidatorTest {

    private final EmailConfigurationValidator validator = new EmailConfigurationValidator(new FromJsonHelper());

    @Test
    void acceptsAndRejectsRegularAddresses() {
        assertTrue(validator.isValidEmail("first.last@example.com"));
        assertFalse(validator.isValidEmail("not-an-email"));
        assertFalse(validator.isValidEmail("user@example.com."));
        assertFalse(validator.isValidEmail(null));
    }

    @Test
    void longDottedLocalPartDoesNotOverflowStack() {
        String email = "a" + ".a".repeat(20_000) + "@example.com";
        assertTrue(assertDoesNotThrow(() -> validator.isValidEmail(email)));
    }

    @Test
    void longDottedDomainDoesNotOverflowStack() {
        String email = "a@" + "a.".repeat(20_000) + "com";
        assertTrue(assertDoesNotThrow(() -> validator.isValidEmail(email)));
    }
}
