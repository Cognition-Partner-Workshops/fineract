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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.organisation.office.domain.Office;
import org.junit.jupiter.api.Test;

class TellerTest {

    private final Office office = mock(Office.class);
    private final JsonCommand command = mock(JsonCommand.class);
    private final LocalDate start = LocalDate.of(2025, 1, 1);
    private final LocalDate end = LocalDate.of(2025, 12, 31);

    @Test
    void createsTellerFromCommand() {
        when(command.stringValueOfParameterNamed("name")).thenReturn("Main till");
        when(command.stringValueOfParameterNamed("description")).thenReturn("Branch cash");
        when(command.localDateValueOfParameterNamed("startDate")).thenReturn(start);
        when(command.localDateValueOfParameterNamed("endDate")).thenReturn(end);
        when(command.integerValueOfParameterNamed("status")).thenReturn(300);
        Teller teller = Teller.fromJson(office, command);
        assertSame(office, teller.getOffice());
        assertEquals("Main till", teller.getName());
        assertEquals("Branch cash", teller.getDescription());
        assertEquals(start, teller.getStartDate());
        assertEquals(end, teller.getEndDate());
        assertEquals(300, teller.getStatus());
    }

    @Test
    void unchangedCommandProducesNoAuditChanges() {
        Teller teller = new Teller().setOffice(office).setName("Till").setStatus(300);
        assertTrue(teller.update(office, command).isEmpty());
        assertEquals("Till", teller.getName());
        assertEquals(300, teller.getStatus());
    }

    @Test
    void updatesFieldsAndRetainsDateInputInAudit() {
        Teller teller = new Teller().setOffice(office).setStatus(100);
        Office replacement = mock(Office.class);
        when(command.isChangeInLongParameterNamed("officeId", teller.officeId())).thenReturn(true);
        when(command.longValueOfParameterNamed("officeId")).thenReturn(2L);
        when(command.isChangeInStringParameterNamed("name", null)).thenReturn(true);
        when(command.stringValueOfParameterNamed("name")).thenReturn("New till");
        when(command.isChangeInStringParameterNamed("description", null)).thenReturn(true);
        when(command.stringValueOfParameterNamed("description")).thenReturn("New description");
        when(command.isChangeInLocalDateParameterNamed("startDate", null)).thenReturn(true);
        when(command.isChangeInLocalDateParameterNamed("endDate", null)).thenReturn(true);
        when(command.stringValueOfParameterNamed("startDate")).thenReturn("01 January 2025");
        when(command.stringValueOfParameterNamed("endDate")).thenReturn("31 December 2025");
        when(command.localDateValueOfParameterNamed("startDate")).thenReturn(start);
        when(command.localDateValueOfParameterNamed("endDate")).thenReturn(end);
        when(command.dateFormat()).thenReturn("dd MMMM yyyy");
        when(command.locale()).thenReturn("en");
        when(command.isChangeInIntegerParameterNamed("status", 100)).thenReturn(true);
        when(command.integerValueOfParameterNamed("status")).thenReturn(600);
        Map<String, Object> changes = teller.update(replacement, command);
        assertEquals(Map.of("officeId", 2L, "name", "New till", "description", "New description", "startDate", "01 January 2025", "endDate",
                "31 December 2025", "dateFormat", "dd MMMM yyyy", "locale", "en", "status", 600), changes);
        assertSame(replacement, teller.getOffice());
        assertEquals("New till", teller.getName());
        assertEquals("New description", teller.getDescription());
        assertEquals(start, teller.getStartDate());
        assertEquals(end, teller.getEndDate());
        assertEquals(600, teller.getStatus());
    }

    @Test
    void invalidStatusDoesNotReplaceExistingState() {
        Teller teller = new Teller().setOffice(office).setStatus(300);
        when(command.isChangeInIntegerParameterNamed("status", 300)).thenReturn(true);
        when(command.integerValueOfParameterNamed("status")).thenReturn(999);
        assertEquals(Map.of("status", 999), teller.update(office, command));
        assertEquals(300, teller.getStatus());
    }

    @Test
    void mapsKnownStatesAndRejectsUnknownValues() {
        for (TellerStatus state : TellerStatus.values()) {
            assertSame(state, TellerStatus.fromInt(state.getValue()));
            assertTrue(state.hasStateOf(state));
            assertEquals(state == TellerStatus.ACTIVE, state.isActive());
            assertEquals(state == TellerStatus.PENDING, state.isPending());
            assertEquals(state == TellerStatus.INACTIVE, state.isInactive());
            assertEquals(state == TellerStatus.CLOSED, state.isClosed());
        }
        assertSame(TellerStatus.INVALID, TellerStatus.fromInt(-1));
        assertFalse(TellerStatus.ACTIVE.hasStateOf(TellerStatus.CLOSED));
    }
}
