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
package org.apache.fineract.portfolio.tax.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.tax.data.TaxComponentData;
import org.apache.fineract.portfolio.tax.data.TaxGroupMappingsData;
import org.apache.fineract.portfolio.tax.domain.TaxComponent;
import org.apache.fineract.portfolio.tax.domain.TaxGroupMappings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

class TaxUtilsTest {

    private static final LocalDate START = LocalDate.of(2024, 1, 1);

    @ParameterizedTest
    @CsvSource({ "HALF_UP, 0.01", "HALF_EVEN, 0.00", "DOWN, 0.00" })
    void respectsConfiguredRoundingAtHalfCent(RoundingMode mode, BigDecimal expected) {
        TaxComponent component = component("VAT", "10");
        TaxGroupMappings mapping = TaxGroupMappings.createTaxGroupMappings(component, START);
        try (MockedStatic<MoneyHelper> rounding = mockStatic(MoneyHelper.class)) {
            rounding.when(MoneyHelper::getRoundingMode).thenReturn(mode);
            assertEquals(expected, TaxUtils.splitTax(new BigDecimal("0.05"), START.plusDays(1), Set.of(mapping), 2).get(component));
        }
    }

    @Test
    void ignoresMappingsWhoseComponentRateIsNotYetApplicable() {
        TaxComponent future = TaxComponent.createTaxComponent("future", BigDecimal.TEN, null, null, null, null, START.plusDays(5));
        TaxGroupMappings mapping = TaxGroupMappings.createTaxGroupMappings(future, START);
        TaxComponentData futureData = TaxComponentData.instance(1L, "future", BigDecimal.TEN, null, null, null, null, START.plusDays(5),
                List.of());
        TaxGroupMappingsData dataMapping = new TaxGroupMappingsData(1L, futureData, START, null);
        assertTrue(TaxUtils.splitTax(BigDecimal.TEN, START.plusDays(1), Set.of(mapping), 2).isEmpty());
        assertTrue(TaxUtils.splitTaxData(BigDecimal.TEN, START.plusDays(1), Set.of(dataMapping), 2).isEmpty());
        try (MockedStatic<MoneyHelper> rounding = mockStatic(MoneyHelper.class)) {
            rounding.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("10.00"), TaxUtils.addTax(BigDecimal.TEN, START.plusDays(1), List.of(mapping), 2));
        }
    }

    @Test
    void splitsEachActiveComponentWithRoundingAndSubtractsTaxFromIncome() {
        TaxComponent vat = component("VAT", "12.5");
        TaxComponent levy = component("levy", "2");
        TaxGroupMappings vatMapping = TaxGroupMappings.createTaxGroupMappings(vat, START);
        TaxGroupMappings levyMapping = TaxGroupMappings.createTaxGroupMappings(levy, START);
        try (MockedStatic<MoneyHelper> rounding = mockStatic(MoneyHelper.class)) {
            rounding.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_UP);
            Map<TaxComponent, BigDecimal> split = TaxUtils.splitTax(new BigDecimal("10.05"), START.plusDays(1),
                    Set.of(vatMapping, levyMapping), 2);
            assertEquals(new BigDecimal("1.26"), split.get(vat));
            assertEquals(new BigDecimal("0.20"), split.get(levy));
            assertEquals(new BigDecimal("1.46"), TaxUtils.totalTaxAmount(split));
            assertEquals(new BigDecimal("8.59"), TaxUtils.incomeAmount(new BigDecimal("10.05"), split));
            assertEquals(new BigDecimal("8.59"),
                    TaxUtils.incomeAmount(new BigDecimal("10.05"), START.plusDays(1), Set.of(vatMapping, levyMapping), 2));
        }
    }

    @Test
    void excludesMappingsAtStartAndAfterEndAndHandlesNullAmount() {
        TaxComponent component = component("VAT", "10");
        TaxGroupMappings mapping = TaxGroupMappings.createTaxGroupMappings(component, START);
        mapping.update(START.plusDays(2), new java.util.ArrayList<>());
        assertTrue(TaxUtils.splitTax(new BigDecimal("100"), START, Set.of(mapping), 2).isEmpty());
        assertTrue(TaxUtils.splitTax(new BigDecimal("100"), START.plusDays(3), Set.of(mapping), 2).isEmpty());
        assertTrue(TaxUtils.splitTax(null, START.plusDays(1), Set.of(mapping), 2).isEmpty());
        assertNull(TaxUtils.addTax(null, START.plusDays(1), List.of(mapping), 2));
        assertNull(TaxUtils.addTax(BigDecimal.ZERO, START.plusDays(1), List.of(mapping), 2));
    }

    @Test
    void grossesUpNetAmountUsingCombinedActiveRates() {
        TaxGroupMappings first = TaxGroupMappings.createTaxGroupMappings(component("VAT", "10"), START);
        TaxGroupMappings second = TaxGroupMappings.createTaxGroupMappings(component("levy", "5"), START);
        try (MockedStatic<MoneyHelper> rounding = mockStatic(MoneyHelper.class)) {
            rounding.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_UP);
            assertEquals(new BigDecimal("117.65"), TaxUtils.addTax(new BigDecimal("100"), START.plusDays(1), List.of(first, second), 2));
            assertEquals(new BigDecimal("100.00"), TaxUtils.addTax(new BigDecimal("100"), START, List.of(first, second), 2));
        }
    }

    @Test
    void splitsDataMappingsAndTotalsRoundedTaxes() {
        TaxComponentData data = TaxComponentData.instance(1L, "VAT", new BigDecimal("7.5"), null, null, null, null, START, List.of());
        TaxGroupMappingsData mapping = new TaxGroupMappingsData(1L, data, START, START.plusDays(2));
        try (MockedStatic<MoneyHelper> rounding = mockStatic(MoneyHelper.class)) {
            rounding.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_UP);
            Map<TaxComponentData, BigDecimal> split = TaxUtils.splitTaxData(new BigDecimal("10"), START.plusDays(2), Set.of(mapping), 2);
            assertEquals(new BigDecimal("0.75"), split.get(data));
            assertEquals(new BigDecimal("0.75"), TaxUtils.totalTaxDataAmount(split));
        }
        assertTrue(TaxUtils.splitTaxData(new BigDecimal("10"), START.plusDays(3), Set.of(mapping), 2).isEmpty());
        assertTrue(TaxUtils.splitTaxData(null, START.plusDays(1), Set.of(mapping), 2).isEmpty());
    }

    private static TaxComponent component(String name, String percentage) {
        return TaxComponent.createTaxComponent(name, new BigDecimal(percentage), null, null, null, null, START);
    }
}
