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
package org.apache.fineract.portfolio.loanaccount.loanschedule.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.organisation.holiday.domain.Holiday;
import org.apache.fineract.organisation.holiday.domain.RescheduleType;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.workingdays.domain.WorkingDays;
import org.apache.fineract.portfolio.calendar.domain.Calendar;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanaccount.domain.LoanMoneyTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DefaultScheduledDateGeneratorTest extends LoanMoneyTestSupport {

    private final DefaultScheduledDateGenerator generator = new DefaultScheduledDateGenerator();

    private LoanApplicationTerms terms(PeriodFrequencyType frequency, int every, LocalDate seed) {
        LoanApplicationTerms terms = mock(LoanApplicationTerms.class);
        when(terms.getRepaymentPeriodFrequencyType()).thenReturn(frequency);
        when(terms.getLoanTermPeriodFrequencyType()).thenReturn(frequency);
        when(terms.getRepaymentEvery()).thenReturn(every);
        when(terms.getSeedDate()).thenReturn(seed);
        when(terms.getExpectedDisbursementDate()).thenReturn(seed);
        when(terms.getRepaymentStartDate()).thenReturn(seed);
        return terms;
    }

    @ParameterizedTest
    @CsvSource({ "DAYS,3,2024-02-27,2024-03-01", "WEEKS,2,2024-02-20,2024-03-05", "MONTHS,1,2024-01-31,2024-02-29",
            "MONTHS,1,2023-01-31,2023-02-28", "MONTHS,2,2024-01-31,2024-03-31", "YEARS,1,2024-02-29,2025-02-28",
            "YEARS,4,2024-02-29,2028-02-29", "DAYS,0,2024-01-01,2024-01-01" })
    void repaymentPeriodArithmetic(PeriodFrequencyType frequency, int every, LocalDate start, LocalDate expected) {
        assertEquals(expected, generator.getRepaymentPeriodDate(frequency, every, start));
        assertEquals(expected, generator.generateNextRepaymentDate(start, terms(frequency, every, start), false));
    }

    @ParameterizedTest
    @CsvSource({ "DAYS,3,2024-01-04,true", "DAYS,3,2024-01-05,false", "WEEKS,2,2024-01-15,true", "WEEKS,2,2024-01-16,false",
            "WEEKS,2,2024-01-08,false", "MONTHS,2,2024-03-01,true", "MONTHS,2,2024-03-02,false", "MONTHS,2,2024-02-01,false",
            "YEARS,2,2026-01-01,true", "YEARS,2,2026-01-02,false", "YEARS,2,2025-01-01,false", "INVALID,1,2024-01-01,false",
            "WHOLE_TERM,1,2024-01-01,false" })
    void scheduleMembership(PeriodFrequencyType frequency, int every, LocalDate candidate, boolean expected) {
        assertEquals(expected, generator.isDateFallsInSchedule(frequency, every, LocalDate.of(2024, 1, 1), candidate));
    }

    @ParameterizedTest
    @CsvSource({ "DAYS,3,2024-03-01,2024-02-27", "WEEKS,2,2024-03-05,2024-02-20", "MONTHS,1,2024-03-31,2024-02-29",
            "YEARS,1,2025-02-28,2024-02-28" })
    void idealDisbursementReversesFrequency(PeriodFrequencyType frequency, int every, LocalDate first, LocalDate expected) {
        assertEquals(expected, generator.idealDisbursementDateBasedOnFirstRepaymentDate(frequency, every, first, null, null, null));
    }

    @Test
    void monthlyScheduleRecoversSeedDayAfterFebruary() {
        LocalDate seed = LocalDate.of(2024, 1, 31);
        LoanApplicationTerms terms = terms(PeriodFrequencyType.MONTHS, 1, seed);
        when(terms.getNumberOfRepayments()).thenReturn(3);
        when(terms.getFixedLength()).thenReturn(null);
        when(terms.calculateMaxDateForFixedLength()).thenReturn(LocalDate.of(2024, 4, 30));
        when(terms.getCurrency()).thenReturn(new CurrencyData("USD"));
        var periods = generator.generateRepaymentPeriods(MathContext.DECIMAL64, seed, terms, null);
        assertEquals(List.of(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31), LocalDate.of(2024, 4, 30)),
                periods.stream().map(LoanScheduleModelRepaymentPeriod::periodDueDate).toList());
        assertEquals(List.of(1, 2, 3), periods.stream().map(LoanScheduleModelRepaymentPeriod::periodNumber).toList());
        assertEquals(LocalDate.of(2024, 4, 30), generator.getLastRepaymentDate(terms, null));
        assertEquals(LocalDate.of(2024, 3, 31),
                generator.generateNextScheduleDateStartingFromDisburseDate(LocalDate.of(2024, 2, 29), terms, null));
        assertEquals(LocalDate.of(2024, 3, 31),
                generator.generateNextScheduleDateStartingFromDisburseDateOrRescheduleDate(LocalDate.of(2024, 2, 29), terms, null));
    }

    @Test
    void explicitFirstRepaymentAndFixedFinalDateOverrideRegularIntervals() {
        LoanApplicationTerms terms = terms(PeriodFrequencyType.MONTHS, 1, LocalDate.of(2024, 1, 31));
        when(terms.getCalculatedRepaymentsStartingFromLocalDate()).thenReturn(LocalDate.of(2024, 3, 15));
        assertEquals(LocalDate.of(2024, 3, 15), generator.generateNextRepaymentDate(LocalDate.of(2024, 1, 31), terms, true));
        assertEquals(LocalDate.of(2024, 2, 29), generator.generateNextRepaymentDate(LocalDate.of(2024, 1, 31), terms, false));
        when(terms.getNumberOfRepayments()).thenReturn(1);
        when(terms.getFixedLength()).thenReturn(45);
        when(terms.calculateMaxDateForFixedLength()).thenReturn(LocalDate.of(2024, 3, 16));
        assertEquals(LocalDate.of(2024, 3, 16), generator.getLastRepaymentDate(terms, null));
    }

    @ParameterizedTest
    @CsvSource({ "2,2024-06-03", "4,2024-05-31" })
    void weekendAdjustment(int type, LocalDate expected) {
        LoanApplicationTerms terms = terms(PeriodFrequencyType.WEEKS, 1, LocalDate.of(2024, 6, 1));
        WorkingDays workingDays = new WorkingDays("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", type, false, false);
        HolidayDetailDTO holidays = new HolidayDetailDTO(false, List.of(), workingDays);
        assertEquals(expected, generator.adjustRepaymentDate(LocalDate.of(2024, 6, 1), terms, holidays).getChangedScheduleDate());
    }

    @ParameterizedTest
    @CsvSource({ "FREQ=MONTHLY;BYDAY=2MO,2024-02-12", "FREQ=MONTHLY;BYDAY=-1FR,2024-02-23" })
    void calendarNthWeekday(String recurrence, LocalDate expected) {
        LocalDate seed = LocalDate.of(2024, 1, 1);
        LoanApplicationTerms terms = terms(PeriodFrequencyType.MONTHS, 1, seed);
        Calendar calendar = mock(Calendar.class);
        when(calendar.getRecurrence()).thenReturn(recurrence);
        when(calendar.getStartDateLocalDate()).thenReturn(seed);
        when(terms.getLoanCalendar()).thenReturn(calendar);
        assertEquals(expected, generator.generateNextRepaymentDate(LocalDate.of(2024, 1, 31), terms, false));
    }

    @Test
    void holidayRescheduleIsAdjustedAgainWhenItLandsOnAWeekend() {
        LocalDate friday = LocalDate.of(2024, 5, 31);
        LoanApplicationTerms terms = terms(PeriodFrequencyType.WEEKS, 1, friday);
        WorkingDays weekdays = new WorkingDays("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", 2, false, false);
        Holiday holiday = new Holiday().setFromDate(friday).setToDate(friday)
                .setReschedulingType(RescheduleType.RESCHEDULETOSPECIFICDATE.getValue())
                .setRepaymentsRescheduledTo(LocalDate.of(2024, 6, 1));
        HolidayDetailDTO holidays = new HolidayDetailDTO(true, List.of(holiday), weekdays);
        assertEquals(LocalDate.of(2024, 6, 3), generator.adjustRepaymentDate(friday, terms, holidays).getChangedScheduleDate());
        HolidayDetailDTO disabled = new HolidayDetailDTO(false, List.of(holiday), weekdays);
        assertEquals(friday, generator.adjustRepaymentDate(friday, terms, disabled).getChangedScheduleDate());
    }

    @Test
    void holidayCanSkipToNextRepaymentAndRescheduleCanStartFromNewDueDate() {
        LocalDate friday = LocalDate.of(2024, 5, 31);
        LoanApplicationTerms terms = terms(PeriodFrequencyType.WEEKS, 1, friday);
        WorkingDays allDays = new WorkingDays("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR,SA,SU", 1, false, false);
        Holiday holiday = new Holiday().setFromDate(friday).setToDate(friday)
                .setReschedulingType(RescheduleType.RESCHEDULETONEXTREPAYMENTDATE.getValue());
        assertEquals(LocalDate.of(2024, 6, 7), generator
                .adjustRepaymentDate(friday, terms, new HolidayDetailDTO(true, List.of(holiday), allDays)).getChangedScheduleDate());
        when(terms.getNewScheduledDueDateStart()).thenReturn(LocalDate.of(2024, 6, 10));
        assertEquals(LocalDate.of(2024, 6, 17),
                generator.generateNextScheduleDateStartingFromDisburseDateOrRescheduleDate(LocalDate.of(2024, 6, 10), terms, null));
    }

    @Test
    void emptyScheduleAndInvalidFrequency() {
        LoanApplicationTerms terms = terms(PeriodFrequencyType.DAYS, 1, LocalDate.of(2024, 1, 1));
        when(terms.getCurrency()).thenReturn(new CurrencyData("USD"));
        assertTrue(generator.generateRepaymentPeriods(MathContext.DECIMAL64, LocalDate.of(2024, 1, 1), terms, null).isEmpty());
        assertFalse(generator.isDateFallsInSchedule(PeriodFrequencyType.INVALID, 1, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1)));
        assertThrows(ArithmeticException.class,
                () -> generator.isDateFallsInSchedule(PeriodFrequencyType.DAYS, 0, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)));
    }
}
