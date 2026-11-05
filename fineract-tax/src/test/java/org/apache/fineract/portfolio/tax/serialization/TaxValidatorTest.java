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
package org.apache.fineract.portfolio.tax.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.tax.api.TaxApiConstants;
import org.apache.fineract.portfolio.tax.domain.TaxComponent;
import org.apache.fineract.portfolio.tax.domain.TaxGroup;
import org.apache.fineract.portfolio.tax.domain.TaxGroupMappings;
import org.junit.jupiter.api.Test;

class TaxValidatorTest {

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private final TaxValidator validator = new TaxValidator(new FromJsonHelper());

    @Test
    void rejectsMissingAndOutOfRangeComponentRatesAndUnpairedGlAccounts() {
        assertThrows(InvalidJsonException.class, () -> validator.validateForTaxComponentCreate(" "));
        assertDoesNotThrow(() -> validator.validateForTaxComponentCreate("{\"name\":\"VAT\",\"percentage\":10}"));
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForTaxComponentCreate("{\"name\":\"\",\"percentage\":101,\"debitAccountId\":2}"));
        assertTrue(error.getErrors().stream().anyMatch(e -> TaxApiConstants.percentageParamName.equals(e.getParameterName())));
        assertTrue(error.getErrors().stream().anyMatch(e -> TaxApiConstants.debitAccountTypeParamName.equals(e.getParameterName())));
        assertTrue(error.getErrors().stream().anyMatch(e -> TaxApiConstants.nameParamName.equals(e.getParameterName())));
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForTaxComponentUpdate("{\"percentage\":0}"));
        assertDoesNotThrow(() -> validator.validateForTaxComponentUpdate("{\"percentage\":100}"));
    }

    @Test
    void requiresNonemptyGroupAndValidComponentIdentifiers() {
        assertThrows(InvalidJsonException.class, () -> validator.validateForTaxGroupCreate(""));
        assertDoesNotThrow(() -> validator.validateForTaxGroupCreate("{\"name\":\"VAT\",\"taxComponents\":[{\"taxComponentId\":1}]}"));
        PlatformApiDataValidationException empty = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForTaxGroupCreate("{\"name\":\"VAT\",\"taxComponents\":[]}"));
        assertTrue(empty.getErrors().stream().anyMatch(e -> TaxApiConstants.taxComponentsParamName.equals(e.getParameterName())));
        assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForTaxGroupCreate("{\"name\":\"VAT\",\"taxComponents\":[{\"taxComponentId\":-1}]}"));
    }

    @Test
    void rejectsCombinedRatesAboveOneHundredAndOverlappingIntervals() {
        TaxComponent first = component(60);
        TaxComponent second = component(50);
        TaxGroup group = group(TaxGroupMappings.createTaxGroupMappings(first, START),
                TaxGroupMappings.createTaxGroupMappings(second, START));
        PlatformApiDataValidationException overLimit = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateTaxGroup(group));
        assertTrue(overLimit.getErrors().stream().anyMatch(e -> TaxValidator.TOTAL_PERCENTAGE.equals(e.getParameterName())));

        TaxGroup overlapping = group(TaxGroupMappings.createTaxGroupMappings(first, START),
                TaxGroupMappings.createTaxGroupMappings(first, START.plusDays(1)));
        PlatformApiDataValidationException overlap = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateTaxGroup(overlapping));
        assertTrue(overlap.getErrors().stream().anyMatch(e -> e.getUserMessageGlobalisationCode().contains("dates.are.overlapping")));

        assertDoesNotThrow(() -> validator.validateTaxGroup(group(TaxGroupMappings.createTaxGroupMappings(component(60), START),
                TaxGroupMappings.createTaxGroupMappings(component(40), START))));
    }

    @Test
    void forbidsChangingAnAlreadyClosedMappingOrItsComponent() {
        TaxComponent original = component(10);
        original.setId(1L);
        TaxGroupMappings existing = TaxGroupMappings.createTaxGroupMappings(original, START);
        existing.setId(2L);
        existing.update(START.plusDays(5), new java.util.ArrayList<>());
        TaxGroup group = group(existing);
        TaxComponent replacement = component(20);
        replacement.setId(3L);
        TaxGroupMappings changed = TaxGroupMappings.createTaxGroupMappings(2L, replacement, START.plusDays(6));

        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateTaxGroupEndDateAndTaxComponent(group, Set.of(changed)));
        assertTrue(error.getErrors().stream().anyMatch(e -> TaxApiConstants.endDateParamName.equals(e.getParameterName())));
        assertTrue(error.getErrors().stream().anyMatch(e -> TaxApiConstants.taxComponentIdParamName.equals(e.getParameterName())));
        assertDoesNotThrow(() -> validator.validateTaxGroupEndDateAndTaxComponent(group,
                Set.of(TaxGroupMappings.createTaxGroupMappings(2L, original, START.plusDays(5)))));
    }

    @Test
    void refusesStartDateEarlierThanExistingDate() {
        JsonCommand command = mock(JsonCommand.class);
        when(command.localDateValueOfParameterNamed(TaxApiConstants.startDateParamName)).thenReturn(START.minusDays(1));
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateStartDate(START, command));
    }

    private static TaxComponent component(int rate) {
        return TaxComponent.createTaxComponent("tax", BigDecimal.valueOf(rate), null, null, null, null, START);
    }

    private static TaxGroup group(TaxGroupMappings... mappings) {
        return TaxGroup.createTaxGroup("taxes", new HashSet<>(Set.of(mappings)));
    }
}
