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
package org.apache.fineract.portfolio.tax.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.tax.api.TaxApiConstants;
import org.apache.fineract.portfolio.tax.exception.TaxMappingNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaxDomainTest {

    private static final LocalDate START = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setBusinessDate() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, START.plusDays(10))));
    }

    @AfterEach
    void clearContext() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void percentageChangeRetainsHistoricalRateThroughPreviousEndDate() {
        TaxComponent component = component("10");
        JsonCommand command = mock(JsonCommand.class);
        when(command.isChangeInBigDecimalParameterNamed(TaxApiConstants.percentageParamName, new BigDecimal("10"))).thenReturn(true);
        when(command.bigDecimalValueOfParameterNamed(TaxApiConstants.percentageParamName)).thenReturn(new BigDecimal("20"));
        when(command.parameterExists(TaxApiConstants.startDateParamName)).thenReturn(true);
        when(command.localDateValueOfParameterNamed(TaxApiConstants.startDateParamName)).thenReturn(START.plusDays(10));

        Map<String, Object> changes = component.update(command);

        assertEquals(new BigDecimal("20"), changes.get(TaxApiConstants.percentageParamName));
        assertEquals(START.plusDays(10), changes.get(TaxApiConstants.startDateParamName));
        assertEquals(new BigDecimal("10"), component.getApplicablePercentage(START.plusDays(5)));
        assertEquals(new BigDecimal("10"), component.getApplicablePercentage(START.plusDays(10)));
        assertEquals(new BigDecimal("20"), component.getApplicablePercentage(START.plusDays(11)));
        assertNull(component.getApplicablePercentage(START));
        assertEquals(Set.of(START, START.plusDays(10)), Set.copyOf(component.allStartDates()));
    }

    @Test
    void mappingEndDateCanOnlyBeSetOnceAndIsInclusive() {
        TaxGroupMappings mapping = TaxGroupMappings.createTaxGroupMappings(component("10"), START);
        List<Map<String, Object>> changes = new ArrayList<>();
        assertFalse(mapping.occursOnDayFromAndUpToAndIncluding(START));
        mapping.update(START.plusDays(2), changes);
        mapping.update(START.plusDays(3), changes);
        assertTrue(mapping.occursOnDayFromAndUpToAndIncluding(START.plusDays(2)));
        assertFalse(mapping.occursOnDayFromAndUpToAndIncluding(START.plusDays(3)));
        assertEquals(START.plusDays(2), mapping.endDate());
        assertEquals(1, changes.size());
        assertEquals(START.plusDays(2), changes.getFirst().get(TaxApiConstants.endDateParamName));
    }

    @Test
    void groupUpdatesExistingMappingAndName() {
        TaxComponent first = component("10");
        first.setId(1L);
        TaxGroupMappings existing = TaxGroupMappings.createTaxGroupMappings(first, START);
        existing.setId(2L);
        TaxGroup group = TaxGroup.createTaxGroup("taxes", new java.util.HashSet<>(Set.of(existing)));
        assertSame(group, existing.getTaxGroup());

        TaxGroupMappings closure = TaxGroupMappings.createTaxGroupMappings(2L, first, START.plusDays(4));
        TaxComponent second = component("5");
        second.setId(3L);
        TaxGroupMappings addition = TaxGroupMappings.createTaxGroupMappings(second, START.plusDays(4));
        JsonCommand command = mock(JsonCommand.class);
        when(command.isChangeInStringParameterNamed(TaxApiConstants.nameParamName, "taxes")).thenReturn(true);
        when(command.stringValueOfParameterNamed(TaxApiConstants.nameParamName)).thenReturn("new taxes");

        Map<String, Object> changes = group.update(command, Set.of(closure));

        assertEquals("new taxes", group.getName());
        assertEquals("new taxes", changes.get(TaxApiConstants.nameParamName));
        assertEquals(START.plusDays(4), existing.endDate());
        assertEquals(1, ((List<?>) changes.get("modifiedComponents")).size());
        assertSame(existing, group.findOneBy(closure));
        assertThrows(TaxMappingNotFoundException.class, () -> group.findOneBy(TaxGroupMappings.createTaxGroupMappings(999L, first, null)));
        assertNull(group.findOneBy(addition));
    }

    @Test
    void groupAddsNewComponent() {
        TaxComponent component = component("5");
        component.setId(3L);
        TaxGroupMappings addition = TaxGroupMappings.createTaxGroupMappings(component, START);
        TaxGroup group = TaxGroup.createTaxGroup("taxes", new java.util.HashSet<>());
        Map<String, Object> changes = group.update(mock(JsonCommand.class), Set.of(addition));
        assertEquals(List.of(3L), changes.get("addComponents"));
        assertEquals(Set.of(addition), group.getTaxGroupMappings());
    }

    @Test
    void historicalRateIsOnlyApplicableInsideItsDateWindow() {
        TaxComponentHistory history = TaxComponentHistory.createTaxComponentHistory(new BigDecimal("10"), START, START.plusDays(5));
        assertFalse(history.occursOnDayFromAndUpToAndIncluding(START));
        assertTrue(history.occursOnDayFromAndUpToAndIncluding(START.plusDays(5)));
        assertFalse(history.occursOnDayFromAndUpToAndIncluding(START.plusDays(6)));
        assertEquals(new BigDecimal("10"), history.getPercentage());
    }

    private static TaxComponent component(String percentage) {
        return TaxComponent.createTaxComponent("tax", new BigDecimal(percentage), null, null, null, null, START);
    }
}
