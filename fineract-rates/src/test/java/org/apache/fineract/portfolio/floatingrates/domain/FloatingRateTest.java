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
package org.apache.fineract.portfolio.floatingrates.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.floatingrates.data.FloatingRateDTO;
import org.apache.fineract.portfolio.floatingrates.data.FloatingRatePeriodData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FloatingRateTest {

    @Test
    void createNewParsesPeriodsAndSetsDefaults() {
        JsonCommand command = command(
                """
                        {"name":"Prime","isBaseLendingRate":true,"ratePeriods":[
                          {"fromDate":"2025-01-01","interestRate":5.25,"isDifferentialToBaseLendingRate":true,"locale":"en","dateFormat":"yyyy-MM-dd"},
                          {"fromDate":"2025-04-01","interestRate":6.00,"locale":"en","dateFormat":"yyyy-MM-dd"}
                        ]}
                        """);

        FloatingRate rate = FloatingRate.createNew(command);

        assertEquals("Prime", rate.getName());
        assertTrue(rate.isBaseLendingRate());
        assertTrue(rate.isActive());
        assertEquals(2, rate.getFloatingRatePeriods().size());
        FloatingRatePeriod first = rate.getFloatingRatePeriods().get(0);
        assertEquals(LocalDate.of(2025, 1, 1), first.getFromDate());
        assertEquals(0, first.getInterestRate().compareTo(new BigDecimal("5.25")));
        assertTrue(first.isDifferentialToBaseLendingRate());
        assertTrue(first.isActive());
        assertEquals(rate, first.getFloatingRate());
    }

    @Test
    void updateDeactivatesOnlyFuturePeriodsAndAddsReplacementPeriods() {
        FloatingRatePeriod historical = new FloatingRatePeriod(LocalDate.of(2024, 1, 1), new BigDecimal("4.00"), false, true);
        FloatingRatePeriod future = new FloatingRatePeriod(LocalDate.of(2026, 1, 1), new BigDecimal("5.00"), false, true);
        FloatingRate rate = new FloatingRate("Old", false, true, new java.util.ArrayList<>(List.of(historical, future)));
        JsonCommand command = command("""
                {"name":"New","isBaseLendingRate":true,"isActive":false,"ratePeriods":[
                  {"fromDate":"2026-06-01","interestRate":7.50,"locale":"en","dateFormat":"yyyy-MM-dd"}
                ]}
                """);

        try (MockedStatic<DateUtils> dateUtils = Mockito.mockStatic(DateUtils.class, Mockito.CALLS_REAL_METHODS)) {
            dateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2025, 1, 1));
            Map<String, Object> changes = rate.update(command);

            assertEquals("New", rate.getName());
            assertTrue(rate.isBaseLendingRate());
            assertFalse(rate.isActive());
            assertEquals("New", changes.get("name"));
            assertEquals(true, changes.get("isBaseLendingRate"));
            assertEquals(false, changes.get("isActive"));
            assertNotNull(changes.get("ratePeriods"));
            assertTrue(historical.isActive());
            assertFalse(future.isActive());
            assertEquals(3, rate.getFloatingRatePeriods().size());
            FloatingRatePeriod added = rate.getFloatingRatePeriods().get(2);
            assertEquals(LocalDate.of(2026, 6, 1), added.getFromDate());
            assertEquals(rate, added.getFloatingRate());
        }
    }

    @Test
    void unchangedUpdateReturnsNoChanges() {
        FloatingRate rate = new FloatingRate("Prime", false, true, List.of());

        assertTrue(rate.update(command("{\"name\":\"Prime\",\"isBaseLendingRate\":false,\"isActive\":true}")).isEmpty());
    }

    @Test
    void addingFirstPeriodsToRateCreatedWithoutPeriodsCurrentlyFails() {
        FloatingRate rate = FloatingRate.createNew(command("{\"name\":\"Prime\"}"));
        JsonCommand update = command("""
                {"ratePeriods":[{"fromDate":"2026-06-01","interestRate":7.50,"locale":"en","dateFormat":"yyyy-MM-dd"}]}
                """);

        try (MockedStatic<DateUtils> dateUtils = Mockito.mockStatic(DateUtils.class, Mockito.CALLS_REAL_METHODS)) {
            dateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2025, 1, 1));
            assertThrows(NullPointerException.class, () -> rate.update(update));
        }
    }

    @Test
    void fetchInterestRatesAddsDifferentialPeriodsAndBaseRates() {
        FloatingRatePeriod first = new FloatingRatePeriod(LocalDate.of(2024, 1, 1), new BigDecimal("2.00"), true, true);
        FloatingRatePeriod second = new FloatingRatePeriod(LocalDate.of(2024, 3, 1), new BigDecimal("3.00"), false, true);
        FloatingRate rate = new FloatingRate("Rate", false, true, new java.util.ArrayList<>(List.of(first, second)));
        FloatingRateDTO dto = new FloatingRateDTO(true, LocalDate.of(2023, 12, 1), new BigDecimal("0.50"),
                List.of(new FloatingRatePeriodData(1L, LocalDate.of(2024, 1, 1), new BigDecimal("5.00"), false, true)));

        var periods = rate.fetchInterestRates(dto);

        assertEquals(2, periods.size());
        assertEquals(0, periods.iterator().next().getInterestRate().compareTo(new BigDecimal("7.50")));
    }

    private static JsonCommand command(String json) {
        return JsonCommand.fromJsonElement(null, JsonParser.parseString(json), new FromJsonHelper());
    }
}
