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
package org.apache.fineract.portfolio.floatingrates.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FloatingRateDTOTest {

    @Test
    void fetchBaseRateUsesTheMostRecentPeriodOnOrBeforeDate() {
        FloatingRateDTO dto = new FloatingRateDTO(true, LocalDate.of(2024, 1, 1), BigDecimal.ZERO,
                List.of(period("2024-03-01", "6.75"), period("2024-01-01", "5.25")));

        assertNull(dto.fetchBaseRate(LocalDate.of(2023, 12, 31)));
        assertEquals(0, dto.fetchBaseRate(LocalDate.of(2024, 1, 1)).compareTo(new BigDecimal("5.25")));
        assertEquals(0, dto.fetchBaseRate(LocalDate.of(2024, 2, 29)).compareTo(new BigDecimal("5.25")));
        assertEquals(0, dto.fetchBaseRate(LocalDate.of(2024, 3, 1)).compareTo(new BigDecimal("6.75")));
    }

    @Test
    void fetchBaseRateReturnsNullWhenNoPeriodsExist() {
        FloatingRateDTO dto = new FloatingRateDTO(true, LocalDate.of(2024, 1, 1), BigDecimal.ZERO, List.of());

        assertNull(dto.fetchBaseRate(LocalDate.of(2024, 1, 1)));
    }

    @Test
    void differentialCanBeAccumulatedAndReset() {
        FloatingRateDTO dto = new FloatingRateDTO(false, LocalDate.of(2024, 1, 1), new BigDecimal("1.25"), List.of());

        dto.addInterestRateDiff(new BigDecimal("0.50"));
        dto.addInterestRateDiff(new BigDecimal("0.25"));
        assertEquals(0, dto.getInterestRateDiff().compareTo(new BigDecimal("2.00")));

        dto.resetInterestRateDiff();
        assertEquals(0, dto.getInterestRateDiff().compareTo(new BigDecimal("1.25")));
    }

    private static FloatingRatePeriodData period(String date, String rate) {
        return new FloatingRatePeriodData(1L, LocalDate.parse(date), new BigDecimal(rate), false, true);
    }
}
