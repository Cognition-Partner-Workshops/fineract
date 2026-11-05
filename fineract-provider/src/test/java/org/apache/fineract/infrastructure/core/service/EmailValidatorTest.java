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
package org.apache.fineract.infrastructure.core.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.campaigns.email.data.EmailConfigurationValidator;
import org.apache.fineract.infrastructure.campaigns.email.data.EmailDataValidator;
import org.apache.fineract.infrastructure.reportmailingjob.validation.ReportMailingJobValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EmailValidatorTest {

    static Stream<Predicate<String>> validators() {
        return Stream.of(new EmailConfigurationValidator(null)::isValidEmail, new EmailDataValidator(null)::isValidEmail,
                new ReportMailingJobValidator(null)::isValidEmail);
    }

    @ParameterizedTest
    @MethodSource("validators")
    void acceptsLargeEmailWithoutStackOverflow(Predicate<String> validator) {
        assertTrue(validator.test("a" + ".a".repeat(100_000) + "@" + "b.".repeat(100_000) + "com"));
    }

    @ParameterizedTest
    @MethodSource("validators")
    void preservesEmailValidation(Predicate<String> validator) {
        for (String email : new String[] { "user.name@example.com", "a@b.com", "user+tag@sub.example.abcdef", "a_b@x.com" }) {
            assertTrue(validator.test(email), email);
        }
        for (String email : new String[] { "", "a@b", "a..b@x.com", "@x.com", "a@x.c", "a@x.abcdefg", "a@x.com.", "a b@x.com" }) {
            assertFalse(validator.test(email), email);
        }
        assertFalse(validator.test(null));
    }

    @ParameterizedTest
    @MethodSource("validators")
    void rejectsLargeInvalidEmailWithoutStackOverflow(Predicate<String> validator) {
        assertFalse(validator.test("a" + ".a".repeat(100_000) + "@b.c"));
        assertFalse(validator.test("a@" + "b.".repeat(100_000) + "c"));
    }
}
