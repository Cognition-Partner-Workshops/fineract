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
package org.apache.fineract.organisation.teller.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.teller.domain.Cashier;
import org.apache.fineract.organisation.teller.domain.Teller;
import org.apache.fineract.organisation.teller.exception.CashierAlreadyAllocated;
import org.apache.fineract.organisation.teller.exception.CashierDateRangeOutOfTellerDateRangeException;
import org.apache.fineract.organisation.teller.exception.CashierInsufficientAmountException;
import org.apache.fineract.organisation.teller.service.TellerManagementReadPlatformService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CashierTransactionDataValidatorTest {

    private final TellerManagementReadPlatformService readService = mock(TellerManagementReadPlatformService.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CashierTransactionDataValidator validator = new CashierTransactionDataValidator(readService, jdbc);
    private final LocalDate start = LocalDate.of(2025, 1, 1);
    private final LocalDate end = LocalDate.of(2025, 1, 31);

    private Cashier cashier() {
        Staff staff = mock(Staff.class);
        when(staff.getId()).thenReturn(7L);
        return new Cashier().setStaff(staff).setStartDate(start).setEndDate(end).setIsFullDay(true);
    }

    private Teller teller() {
        return new Teller().setStartDate(start).setEndDate(end);
    }

    private void balance(String value) {
        CashierTransactionsWithSummaryData summary = mock(CashierTransactionsWithSummaryData.class);
        when(summary.getNetCash()).thenReturn(new BigDecimal(value));
        when(readService.retrieveCashierTransactionsWithSummary(eq(1L), eq(false), isNull(), isNull(), eq("USD"),
                any(SearchParameters.class))).thenReturn(summary);
    }

    @Test
    void acceptsExactBalanceAndSmallerAmountsWithoutRounding() {
        balance("100.000001");
        assertDoesNotThrow(() -> validator.validateSettleCashAndCashOutTransactions(1L, "USD", new BigDecimal("100.000001")));
        assertDoesNotThrow(() -> validator.validateSettleCashAndCashOutTransactions(1L, "USD", new BigDecimal("100.000000")));
    }

    @Test
    void rejectsOneMillionthOverBalance() {
        balance("100.000001");
        assertThrows(CashierInsufficientAmountException.class,
                () -> validator.validateSettleCashAndCashOutTransactions(1L, "USD", new BigDecimal("100.000002")));
    }

    @Test
    void zeroBalanceRejectsPositiveWithdrawalButAllowsZero() {
        balance("0");
        assertDoesNotThrow(() -> validator.validateSettleCashAndCashOutTransactions(1L, "USD", BigDecimal.ZERO));
        assertThrows(CashierInsufficientAmountException.class,
                () -> validator.validateSettleCashAndCashOutTransactions(1L, "USD", new BigDecimal("0.000001")));
    }

    @Test
    void commandOverloadChecksCurrencyAndAmount() {
        balance("10");
        JsonCommand command = mock(JsonCommand.class);
        when(command.stringValueOfParameterNamed("currencyCode")).thenReturn("USD");
        when(command.bigDecimalValueOfParameterNamed("txnAmount")).thenReturn(new BigDecimal("11"));
        assertThrows(CashierInsufficientAmountException.class, () -> validator.validateSettleCashAndCashOutTransactions(1L, command));
    }

    @Test
    void acceptsInclusiveTellerDateBoundaries() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(0);
        assertDoesNotThrow(() -> validator.validateCashierAllowedDateAndTime(cashier(), teller()));
        verify(jdbc).queryForObject(anyString(), eq(Map.of("staffId", 7L, "fromDate", start, "endDate", end)), eq(Integer.class));
    }

    @Test
    void rejectsEachDateOutsideTellerBeforeQueryingAllocations() {
        Cashier cashier = cashier();
        Teller teller = teller();
        cashier.setStartDate(start.minusDays(1));
        assertThrows(CashierDateRangeOutOfTellerDateRangeException.class,
                () -> validator.validateCashierAllowedDateAndTime(cashier, teller));
        cashier.setStartDate(start).setEndDate(start.minusDays(1));
        assertThrows(CashierDateRangeOutOfTellerDateRangeException.class,
                () -> validator.validateCashierAllowedDateAndTime(cashier, teller));
        cashier.setStartDate(end.plusDays(1)).setEndDate(end);
        assertThrows(CashierDateRangeOutOfTellerDateRangeException.class,
                () -> validator.validateCashierAllowedDateAndTime(cashier, teller));
        cashier.setStartDate(start).setEndDate(end.plusDays(1));
        assertThrows(CashierDateRangeOutOfTellerDateRangeException.class,
                () -> validator.validateCashierAllowedDateAndTime(cashier, teller));
        verifyNoInteractions(jdbc);
    }

    @Test
    void openEndedTellerAllowsFutureAllocationAndNullOverlapCount() {
        Teller teller = teller().setEndDate(null);
        Cashier cashier = cashier().setEndDate(end.plusYears(5));
        assertDoesNotThrow(() -> validator.validateCashierAllowedDateAndTime(cashier, teller));
    }

    @Test
    void rejectsExistingFullDayAllocation() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(1);
        assertThrows(CashierAlreadyAllocated.class, () -> validator.validateCashierAllowedDateAndTime(cashier(), teller()));
    }

    @Test
    void partDayOverlapQueryIncludesShiftTimes() {
        Cashier cashier = cashier().setIsFullDay(false).setStartTime("09:00").setEndTime("17:30");
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(2);
        assertThrows(CashierAlreadyAllocated.class, () -> validator.validateCashierAllowedDateAndTime(cashier, teller()));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(),
                eq(Map.of("staffId", 7L, "fromDate", start, "endDate", end, "startTime", "09:00", "endTime", "17:30")), eq(Integer.class));
        assertTrue(sql.getValue().contains("TIME(:startTime)"));
        assertTrue(sql.getValue().contains("TIME(:endTime)"));
    }

    @Test
    void summaryAddsInflowsAndSubtractsOutflowsWithSixDecimalPrecision() {
        CashierTransactionsWithSummaryData summary = CashierTransactionsWithSummaryData.instance(null, new BigDecimal("100.000001"),
                new BigDecimal("20.000002"), new BigDecimal("5.000003"), new BigDecimal("10.000004"), "Office", 1L, "Teller", 2L,
                "Cashier");
        assertEquals(new BigDecimal("104.999996"), summary.getNetCash());
        assertEquals(new BigDecimal("100.000001"), summary.getSumCashAllocation());
        assertEquals(new BigDecimal("20.000002"), summary.getSumInwardCash());
        assertEquals(new BigDecimal("5.000003"), summary.getSumOutwardCash());
        assertEquals(new BigDecimal("10.000004"), summary.getSumCashSettlement());
    }

    @Test
    void summarySupportsZeroAndOverdrawnBalances() {
        assertEquals(BigDecimal.ZERO, CashierTransactionsWithSummaryData
                .instance(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "", 1L, "", 2L, "").getNetCash());
        assertEquals(new BigDecimal("-1"), CashierTransactionsWithSummaryData
                .instance(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, "", 1L, "", 2L, "").getNetCash());
    }
}
