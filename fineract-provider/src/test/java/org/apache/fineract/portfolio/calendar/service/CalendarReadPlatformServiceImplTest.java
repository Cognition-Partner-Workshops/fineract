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
package org.apache.fineract.portfolio.calendar.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.portfolio.calendar.data.CalendarData;
import org.apache.fineract.portfolio.calendar.domain.CalendarEntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class CalendarReadPlatformServiceImplTest {

    private static final Long LOAN_ID = 11L;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CalendarReadPlatformServiceImpl underTest;

    private void givenCalendars(final List<CalendarData> calendars) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(LOAN_ID), eq(CalendarEntityType.LOANS.getValue())))
                .thenReturn(calendars);
    }

    @Test
    void retrieveLoanCalendarReturnsTheFirstCalendar() {
        final CalendarData first = mock(CalendarData.class);
        givenCalendars(List.of(first, mock(CalendarData.class)));

        assertSame(first, underTest.retrieveLoanCalendar(LOAN_ID));
    }

    @Test
    void retrieveLoanCalendarReturnsNullWhenLoanHasNoCalendar() {
        givenCalendars(List.of());

        assertNull(underTest.retrieveLoanCalendar(LOAN_ID));
    }
}
