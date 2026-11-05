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
package org.apache.fineract.organisation.teller.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.junit.jupiter.api.Test;

class CashierTest {

    private final JsonCommand command = mock(JsonCommand.class);

    @Test
    void createsCashierWithAllocationAndShift() {
        Office office = mock(Office.class);
        Staff staff = mock(Staff.class);
        Teller teller = new Teller();
        LocalDate date = LocalDate.of(2025, 2, 1);
        when(command.stringValueOfParameterNamed("description")).thenReturn("Morning");
        when(command.localDateValueOfParameterNamed("startDate")).thenReturn(date);
        when(command.localDateValueOfParameterNamed("endDate")).thenReturn(date.plusDays(1));
        when(command.booleanObjectValueOfParameterNamed("isFullDay")).thenReturn(false);
        Cashier cashier = Cashier.fromJson(office, teller, staff, "09:30", "17:45", command);
        assertSame(office, cashier.getOffice());
        assertSame(staff, cashier.getStaff());
        assertSame(teller, cashier.getTeller());
        assertEquals("Morning", cashier.getDescription());
        assertEquals(date, cashier.getStartDate());
        assertEquals(date.plusDays(1), cashier.getEndDate());
        assertFalse(cashier.getIsFullDay());
        assertEquals("09:30", cashier.getStartTime());
        assertEquals("17:45", cashier.getEndTime());
    }

    @Test
    void unchangedFullDayAllocationHasNoChanges() {
        when(command.booleanObjectValueOfParameterNamed("isFullDay")).thenReturn(true);
        Cashier cashier = new Cashier().setIsFullDay(true);
        assertTrue(cashier.update(command).isEmpty());
        assertNull(cashier.getStartTime());
    }

    @Test
    void updatesDatesDescriptionAndFullDayFlag() {
        Cashier cashier = new Cashier().setIsFullDay(false);
        LocalDate date = LocalDate.of(2025, 3, 1);
        when(command.booleanObjectValueOfParameterNamed("isFullDay")).thenReturn(true);
        when(command.isChangeInBooleanParameterNamed("isFullDay", false)).thenReturn(true);
        when(command.isChangeInStringParameterNamed("description", null)).thenReturn(true);
        when(command.stringValueOfParameterNamed("description")).thenReturn("Full day");
        when(command.isChangeInLocalDateParameterNamed("startDate", null)).thenReturn(true);
        when(command.isChangeInLocalDateParameterNamed("endDate", null)).thenReturn(true);
        when(command.localDateValueOfParameterNamed("startDate")).thenReturn(date);
        when(command.localDateValueOfParameterNamed("endDate")).thenReturn(date.plusDays(2));
        when(command.stringValueOfParameterNamed("startDate")).thenReturn("2025-03-01");
        when(command.stringValueOfParameterNamed("endDate")).thenReturn("2025-03-03");
        when(command.dateFormat()).thenReturn("yyyy-MM-dd");
        when(command.locale()).thenReturn("en");
        assertEquals(Map.of("isFullDay", true, "description", "Full day", "startDate", "2025-03-01", "endDate", "2025-03-03", "dateFormat",
                "yyyy-MM-dd", "locale", "en"), cashier.update(command));
        assertTrue(cashier.getIsFullDay());
        assertEquals("Full day", cashier.getDescription());
        assertEquals(date, cashier.getStartDate());
        assertEquals(date.plusDays(2), cashier.getEndDate());
    }

    @Test
    void updatesShiftAndPadsMidnightComponents() {
        Cashier cashier = new Cashier().setIsFullDay(false).setStartTime("09:30").setEndTime("17:45");
        when(command.booleanObjectValueOfParameterNamed("isFullDay")).thenReturn(false);
        when(command.isChangeInLongParameterNamed("hourStartTime", cashier.getHourFromStartTime())).thenReturn(true);
        when(command.isChangeInLongParameterNamed("hourEndTime", 17L)).thenReturn(true);
        when(command.stringValueOfParameterNamed("hourStartTime")).thenReturn("0");
        when(command.stringValueOfParameterNamed("minStartTime")).thenReturn("0");
        when(command.stringValueOfParameterNamed("hourEndTime")).thenReturn("0");
        when(command.stringValueOfParameterNamed("minEndTime")).thenReturn("0");
        assertEquals(Map.of("hourStartTime", "00", "minStartTime", "00", "hourEndTime", "00", "minEndTime", "00"), cashier.update(command));
        assertEquals("00:00", cashier.getStartTime());
        assertEquals("00:00", cashier.getEndTime());
    }

    @Test
    void parsesShiftComponentsAndHandlesMissingTimes() {
        Cashier cashier = new Cashier();
        assertNull(cashier.getHourFromStartTime());
        assertNull(cashier.getMinFromStartTime());
        assertNull(cashier.getHourFromEndTime());
        assertNull(cashier.getMinFromEndTime());
        cashier.setStartTime("").setEndTime("");
        assertNull(cashier.getHourFromStartTime());
        assertNull(cashier.getMinFromStartTime());
        assertNull(cashier.getHourFromEndTime());
        assertNull(cashier.getMinFromEndTime());
        cashier.setStartTime("09:30").setEndTime("17:45");
        // Current behavior: the start-hour accessor reads the minute component.
        assertEquals(30L, cashier.getHourFromStartTime());
        assertEquals(30L, cashier.getMinFromStartTime());
        assertEquals(17L, cashier.getHourFromEndTime());
        assertEquals(45L, cashier.getMinFromEndTime());
    }

    @Test
    void missingFullDayFlagCurrentlyFailsOnUpdate() {
        // Current behavior: an omitted flag is unboxed before validation.
        when(command.booleanObjectValueOfParameterNamed("isFullDay")).thenReturn(null);
        assertThrows(NullPointerException.class, () -> new Cashier().update(command));
    }
}
