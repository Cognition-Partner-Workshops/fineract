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
package org.apache.fineract.portfolio.savings.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.portfolio.savings.SavingsPeriodFrequencyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DepositTermDetailTest {

    @ParameterizedTest
    @CsvSource({ "DAYS,2024-02-28,2024-03-01,2", "WEEKS,2024-02-28,2024-03-13,2", "MONTHS,2024-01-31,2024-02-29,0",
            "MONTHS,2024-01-31,2024-03-31,2", "YEARS,2024-02-29,2025-02-28,0", "YEARS,2024-02-29,2025-03-01,1",
            "INVALID,2024-01-01,2025-01-01,0" })
    void countsOnlyCompleteCalendarPeriods(SavingsPeriodFrequencyType type, LocalDate start, LocalDate end, int expected) {
        assertEquals(expected, detail(1, 3, type).depositPeriod(start, end, type));
    }

    @ParameterizedTest
    @CsvSource({ "2024-01-30,false", "2024-01-31,true", "2024-03-31,true", "2024-05-01,false" })
    void termBoundsAreInclusive(LocalDate end, boolean expected) {
        DepositTermDetail detail = detail(1, 3, SavingsPeriodFrequencyType.MONTHS);
        assertEquals(expected, detail.isDepositBetweenMinAndMax(LocalDate.of(2023, 12, 31), end));
    }

    @Test
    void absentBoundsAndMultiplesAllowAnyDepositTerm() {
        DepositTermDetail detail = detail(null, null, SavingsPeriodFrequencyType.DAYS);
        assertTrue(detail.isDepositBetweenMinAndMax(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)));
        assertTrue(detail.isValidInMultiplesOfPeriod(3, SavingsPeriodFrequencyType.DAYS));
        assertFalse(detail.isMinDepositTermGreaterThanMaxDepositTerm());
    }

    @ParameterizedTest
    @CsvSource({ "DAYS,7,WEEKS,1,false", "WEEKS,2,DAYS,13,true", "MONTHS,12,YEARS,1,false", "YEARS,1,MONTHS,12,true" })
    void comparesDifferentUnitsUsingSafeDayConversions(SavingsPeriodFrequencyType minType, int min, SavingsPeriodFrequencyType maxType,
            int max, boolean expected) {
        DepositTermDetail detail = DepositTermDetail.createFrom(min, max, minType, maxType, null, null);
        assertEquals(expected, detail.isMinDepositTermGreaterThanMaxDepositTerm());
    }

    @ParameterizedTest
    @CsvSource({ "1,true", "2,false", "3,true", "4,false", "5,true" })
    void multiplesAreMeasuredRelativeToMinimumTerm(int term, boolean expected) {
        DepositTermDetail detail = DepositTermDetail.createFrom(1, 12, SavingsPeriodFrequencyType.MONTHS, SavingsPeriodFrequencyType.MONTHS,
                2, SavingsPeriodFrequencyType.MONTHS);
        assertEquals(expected, detail.isValidInMultiplesOfPeriod(term, SavingsPeriodFrequencyType.MONTHS));
    }

    @Test
    void zeroMultipleDoesNotDivideByZeroAndCopyRetainsIndependentTerms() {
        DepositTermDetail original = DepositTermDetail.createFrom(1, 12, SavingsPeriodFrequencyType.MONTHS,
                SavingsPeriodFrequencyType.MONTHS, 0, SavingsPeriodFrequencyType.MONTHS);
        assertTrue(original.isValidInMultiplesOfPeriod(5, SavingsPeriodFrequencyType.MONTHS));
        DepositTermDetail copy = original.copy();
        JsonCommand command = mock(JsonCommand.class);
        when(command.isChangeInIntegerParameterNamed("minDepositTerm", 1)).thenReturn(true);
        when(command.integerValueOfParameterNamed("minDepositTerm")).thenReturn(2);
        List<ApiParameterError> errors = new ArrayList<>();
        Map<String, Object> changes = copy.update(command, new DataValidatorBuilder(errors));
        assertEquals(Map.of("minDepositTerm", 2), changes);
        assertTrue(errors.isEmpty());
        assertEquals(1, original.minDepositTerm());
        assertEquals(2, copy.minDepositTerm());
        assertEquals(12, copy.maxDepositTerm());
        assertEquals(0, copy.inMultiplesOfDepositTerm());
        assertEquals(SavingsPeriodFrequencyType.MONTHS.getValue(), copy.inMultiplesOfDepositTermType());
    }

    @Test
    void updateReportsAllTermChangesAndRejectsMinimumAboveMaximum() {
        DepositTermDetail detail = detail(1, 12, SavingsPeriodFrequencyType.MONTHS);
        JsonCommand command = mock(JsonCommand.class);
        Map<String, Integer> values = Map.of("minDepositTerm", 3, "maxDepositTerm", 2, "minDepositTermTypeId", 3, "maxDepositTermTypeId", 3,
                "inMultiplesOfDepositTerm", 1, "inMultiplesOfDepositTermTypeId", 2);
        when(command.isChangeInIntegerParameterNamed("minDepositTerm", 1)).thenReturn(true);
        when(command.isChangeInIntegerParameterNamed("maxDepositTerm", 12)).thenReturn(true);
        when(command.isChangeInIntegerParameterNamed("minDepositTermTypeId", 2)).thenReturn(true);
        when(command.isChangeInIntegerParameterNamed("maxDepositTermTypeId", 2)).thenReturn(true);
        when(command.isChangeInIntegerParameterNamed("inMultiplesOfDepositTerm", null)).thenReturn(true);
        when(command.isChangeInIntegerParameterNamed("inMultiplesOfDepositTermTypeId", null)).thenReturn(true);
        values.forEach((name, value) -> when(command.integerValueOfParameterNamed(name)).thenReturn(value));
        List<ApiParameterError> errors = new ArrayList<>();
        Map<String, Object> changes = detail.update(command, new DataValidatorBuilder(errors));
        assertEquals(values.keySet(), changes.keySet());
        assertEquals(3, detail.minDepositTerm());
        assertEquals(2, detail.maxDepositTerm());
        assertEquals(SavingsPeriodFrequencyType.YEARS.getValue(), detail.minDepositTermType());
        assertEquals(SavingsPeriodFrequencyType.YEARS.getValue(), detail.maxDepositTermType());
        assertEquals(1, detail.inMultiplesOfDepositTerm());
        assertTrue(errors.stream().anyMatch(e -> "minDepositTerm".equals(e.getParameterName())));
    }

    private DepositTermDetail detail(Integer min, Integer max, SavingsPeriodFrequencyType type) {
        return DepositTermDetail.createFrom(min, max, type, type, null, null);
    }
}
