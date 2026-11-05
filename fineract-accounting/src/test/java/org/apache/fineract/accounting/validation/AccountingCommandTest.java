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
package org.apache.fineract.accounting.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.fineract.accounting.AccountingTestSupport;
import org.apache.fineract.accounting.closure.command.GLClosureCommand;
import org.apache.fineract.accounting.glaccount.command.GLAccountCommand;
import org.apache.fineract.accounting.journalentry.command.JournalEntryCommand;
import org.apache.fineract.accounting.journalentry.serialization.JournalEntryCommandFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountingCommandTest extends AccountingTestSupport {

    private final JournalEntryCommandFromApiJsonDeserializer journals = new JournalEntryCommandFromApiJsonDeserializer(jsonHelper);

    private String journalJson(String entries) {
        return "{\"officeId\":1,\"currencyCode\":\"USD\",\"transactionDate\":\"2025-03-19\",\"dateFormat\":\"yyyy-MM-dd\",\"locale\":\"en\","
                + entries + "}";
    }

    @Test
    void journalParsingPreservesExactDecimalAmountsAndBothSides() {
        JournalEntryCommand journal = journals.commandFromApiJson(journalJson(
                "\"amount\":123.456789,\"credits\":[{\"glAccountId\":2,\"amount\":123.456789}],\"debits\":[{\"glAccountId\":3,\"amount\":123.456789}]"));
        assertDoesNotThrow(journal::validateForCreate);
        assertEquals(new BigDecimal("123.456789"), journal.getAmount());
        assertEquals(new BigDecimal("123.456789"), journal.getCredits()[0].getAmount());
        assertEquals(2L, journal.getCredits()[0].getGlAccountId());
        assertEquals(3L, journal.getDebits()[0].getGlAccountId());
        assertEquals(BUSINESS_DATE.minusDays(1), journal.getTransactionDate());
    }

    @Test
    void emptyDebitAndCreditArraysProduceIndexedValidationErrors() {
        JournalEntryCommand journal = journals.commandFromApiJson(journalJson("\"credits\":[],\"debits\":[]"));
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class, journal::validateForCreate);
        Set<String> parameters = error.getErrors().stream().map(e -> e.getParameterName()).collect(Collectors.toSet());
        assertEquals(Set.of("credits[0].glAccountId", "credits[0].amount", "debits[0].glAccountId", "debits[0].amount"), parameters);
    }

    @Test
    void negativeJournalAmountsAndNonpositiveAccountsAreRejected() {
        JournalEntryCommand journal = journals
                .commandFromApiJson(journalJson("\"amount\":-0.01,\"credits\":[{\"glAccountId\":0,\"amount\":-1}]"));
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class, journal::validateForCreate);
        Set<String> parameters = error.getErrors().stream().map(e -> e.getParameterName()).collect(Collectors.toSet());
        assertTrue(parameters.containsAll(Set.of("amount", "credits[0].glAccountId", "credits[0].amount")));
    }

    @Test
    void zeroAmountAndAbsentArraysAreAcceptedForRuleBasedJournal() {
        JournalEntryCommand journal = journals.commandFromApiJson(journalJson("\"amount\":0,\"accountingRule\":1"));
        assertDoesNotThrow(journal::validateForCreate);
        assertNull(journal.getCredits());
        assertNull(journal.getDebits());
        assertEquals(BigDecimal.ZERO, journal.getAmount());
    }

    @Test
    void closureCreateValidatesOfficeDateAndCommentLength() {
        assertDoesNotThrow(() -> new GLClosureCommand(null, 1L, BUSINESS_DATE, "x".repeat(500)).validateForCreate());
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> new GLClosureCommand(null, 0L, null, "x".repeat(501)).validateForCreate());
        assertEquals(Set.of("closingDate", "officeId", "comments"),
                error.getErrors().stream().map(e -> e.getParameterName()).collect(Collectors.toSet()));
    }

    @Test
    void closureUpdateRequiresCommentsAndAllowsEmptyCommentToClear() {
        assertThrows(PlatformApiDataValidationException.class, () -> new GLClosureCommand(null, null, null, null).validateForUpdate());
        assertDoesNotThrow(() -> new GLClosureCommand(null, null, null, "").validateForUpdate());
    }

    @Test
    void glAccountCreateValidatesClassificationUsageAndLengths() {
        GLAccountCommand valid = new GLAccountCommand(null, "Cash", null, "1000", false, true, 1, 2, null, null);
        assertDoesNotThrow(valid::validateForCreate);
        assertTrue(valid.isHeaderAccount());
        GLAccountCommand invalid = new GLAccountCommand(null, "", 0L, "x".repeat(46), false, null, 0, 0, "x".repeat(501), 0L);
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class, invalid::validateForCreate);
        assertTrue(error.getErrors().stream().map(e -> e.getParameterName()).collect(Collectors.toSet())
                .containsAll(Set.of("name", "parentId", "glCode", "type", "usage", "description", "manualEntriesAllowed", "tagId")));
    }

    @Test
    void glAccountUpdateRequiresAChangeAndValidatesPartialFields() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> new GLAccountCommand(null, null, null, null, null, null, null, null, null, null).validateForUpdate());
        assertDoesNotThrow(() -> new GLAccountCommand(null, "renamed", null, null, null, null, null, null, null, null).validateForUpdate());
        assertThrows(PlatformApiDataValidationException.class,
                () -> new GLAccountCommand(null, "renamed", -1L, null, null, null, 9, 9, "", null).validateForUpdate());
    }

    @Test
    void updateCurrentlyAcceptsInvalidTagWhenAnotherFieldChanges() {
        // Tag validation runs after the exception guard in validateForUpdate.
        GLAccountCommand update = new GLAccountCommand(null, "renamed", null, null, null, null, null, null, null, 0L);
        assertDoesNotThrow(update::validateForUpdate);
        assertEquals(0L, update.tagId());
    }
}
