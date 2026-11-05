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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.MonthDay;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.charge.exception.SavingsAccountChargeWithoutMandatoryFieldException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

class SavingsAccountChargeTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("USD", 2, 1);
    private MockedStatic<MoneyHelper> moneyHelper;

    @BeforeEach
    void setUp() {
        moneyHelper = mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
    }

    @AfterEach
    void tearDown() {
        moneyHelper.close();
    }

    @Test
    void partialPaymentAndUndoRestoreOutstandingWithoutMarkingPaid() {
        SavingsAccountCharge charge = charge(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeCalculationType.FLAT, "10.00",
                LocalDate.of(2024, 1, 31), null, null);
        assertAmount("6.00", charge.pay(currency, money("4.00")).getAmount());
        assertFalse(charge.isPaid());
        assertTrue(charge.isPaidOrPartiallyPaid(currency));
        charge.undoPayment(currency, money("4.00"));
        assertAmount("10.00", charge.getAmountOutstanding(currency).getAmount());
        assertFalse(charge.isPaidOrPartiallyPaid(currency));
        assertTrue(charge.isActive());
    }

    @Test
    void cappedPaymentReturnsOnlyOutstandingAndCanBeReset() {
        SavingsAccountCharge charge = charge(ChargeTimeType.SAVINGS_ACTIVATION, ChargeCalculationType.FLAT, "10.00", null, null, null);
        assertAmount("3.00", charge.updatePaidAmountBy(money("3.00")).getAmount());
        assertAmount("7.00", charge.updatePaidAmountBy(money("20.00")).getAmount());
        assertTrue(charge.isPaid());
        assertAmount("0", charge.amoutOutstanding());
        charge.resetToOriginal(currency);
        assertFalse(charge.isPaid());
        assertFalse(charge.isWaived());
        assertAmount("10.00", charge.amoutOutstanding());
    }

    @Test
    void waiverOnlyWaivesUnpaidBalanceAndUndoRestoresIt() {
        SavingsAccountCharge charge = charge(ChargeTimeType.SAVINGS_CLOSURE, ChargeCalculationType.FLAT, "10.00", null, null, null);
        charge.pay(currency, money("4.00"));
        assertAmount("6.00", charge.waive(currency).getAmount());
        assertAmount("6.00", charge.getAmountWaived(currency).getAmount());
        assertTrue(charge.isWaived());
        charge.undoWaiver(currency, money("6.00"));
        assertFalse(charge.isWaived());
        assertAmount("6.00", charge.amoutOutstanding());
    }

    @ParameterizedTest
    @CsvSource({ "MONTHLY_FEE,2024-01-31,1,2024-02-29", "MONTHLY_FEE,2023-01-31,1,2023-02-28", "MONTHLY_FEE,2024-01-31,2,2024-03-31",
            "ANNUAL_FEE,2024-02-29,1,2025-02-28", "WEEKLY_FEE,2024-01-29,2,2024-02-12" })
    void fullyPaidRecurringFeeAdvancesAndUndoRestoresCalendarDate(ChargeTimeType type, LocalDate due, int interval, LocalDate next) {
        SavingsAccountCharge charge = charge(type, ChargeCalculationType.FLAT, "10.00", due, MonthDay.from(due), interval);
        charge.pay(currency, money("10.00"));
        assertEquals(next, charge.getDueDate());
        assertAmount("10.00", charge.amoutOutstanding());
        assertFalse(charge.isPaid());
        charge.undoPayment(currency, money("10.00"));
        assertEquals(due, charge.getDueDate());
        assertAmount("10.00", charge.amoutOutstanding());
    }

    @Test
    void recurringWaiverAdvancesThenUndoRestoresSchedule() {
        LocalDate due = LocalDate.of(2024, 1, 31);
        SavingsAccountCharge charge = charge(ChargeTimeType.MONTHLY_FEE, ChargeCalculationType.FLAT, "10", due, MonthDay.from(due), 1);
        charge.waive(currency);
        assertEquals(LocalDate.of(2024, 2, 29), charge.getDueDate());
        assertFalse(charge.isWaived());
        assertAmount("10", charge.amoutOutstanding());
        charge.undoWaiver(currency, money("10"));
        assertEquals(due, charge.getDueDate());
        assertAmount("0", charge.getAmountWaived(currency).getAmount());
    }

    @ParameterizedTest
    @CsvSource({ "MONTHLY_FEE,2024-01-31,2024-03-01,2024-03-31", "ANNUAL_FEE,2024-02-29,2025-03-01,2026-02-28",
            "WEEKLY_FEE,2024-01-29,2024-02-01,2024-02-05" })
    void nextDueDateNeverPrecedesStartingDate(ChargeTimeType type, LocalDate due, LocalDate start, LocalDate expected) {
        SavingsAccountCharge charge = charge(type, ChargeCalculationType.FLAT, "10", due, MonthDay.from(due), 1);
        assertEquals(expected, charge.getNextDueDateFrom(start));
        charge.updateToNextDueDateFrom(start);
        assertEquals(expected, charge.getDueDate());
    }

    @ParameterizedTest
    @CsvSource({ "SPECIFIED_DUE_DATE", "WEEKLY_FEE", "MONTHLY_FEE", "ANNUAL_FEE" })
    void rejectsChargesWithoutMandatorySchedule(ChargeTimeType type) {
        assertThrows(SavingsAccountChargeWithoutMandatoryFieldException.class,
                () -> charge(type, ChargeCalculationType.FLAT, "10", null, null, null));
    }

    @ParameterizedTest
    @CsvSource({ "100.00,2.5000,2.50", "1.00,0.0250,0.02", "3.00,0.0750,0.08", "0.00,0.0000,0.00" })
    void percentageWithdrawalFeePreservesPrecisionUntilRoundedToMoney(String withdrawal, String exact, String expected) {
        SavingsAccountCharge charge = charge(ChargeTimeType.WITHDRAWAL_FEE, ChargeCalculationType.PERCENT_OF_AMOUNT, "2.50", null, null,
                null);
        assertAmount("0", charge.amoutOutstanding());
        assertAmount(exact, charge.calculateWithdralFeeAmount(new BigDecimal(withdrawal)));
        assertAmount(exact, charge.updateWithdralFeeAmount(new BigDecimal(withdrawal)));
        assertAmount(expected, charge.getAmountOutstanding(currency).getAmount());
        assertAmount("0", charge.updateNoWithdrawalFee());
    }

    @Test
    void flatWithdrawalFeeDoesNotDependOnTransactionAmount() {
        SavingsAccountCharge charge = charge(ChargeTimeType.WITHDRAWAL_FEE, ChargeCalculationType.FLAT, "2.50", null, null, null);
        assertAmount("2.50", charge.calculateWithdralFeeAmount(new BigDecimal("1000")));
        assertFalse(charge.canOverriteSavingAccountRules());
    }

    @Test
    void inactivationSettlesOutstandingAndBlocksCollection() {
        SavingsAccountCharge charge = charge(ChargeTimeType.SAVINGS_CLOSURE, ChargeCalculationType.FLAT, "10", null, null, null);
        assertTrue(charge.canOverriteSavingAccountRules());
        charge.inactiavateCharge(LocalDate.of(2024, 1, 1));
        assertTrue(charge.isNotActive());
        assertTrue(charge.isPaid());
        assertAmount("0", charge.amoutOutstanding());
    }

    private SavingsAccountCharge charge(ChargeTimeType type, ChargeCalculationType calculation, String amount, LocalDate due,
            MonthDay monthDay, Integer interval) {
        Charge definition = mock(Charge.class);
        when(definition.getChargeCalculation()).thenReturn(calculation.getValue());
        when(definition.getAmount()).thenReturn(new BigDecimal(amount));
        return SavingsAccountCharge.createNewWithoutSavingsAccount(definition, new BigDecimal(amount), type, calculation, due, true,
                monthDay, interval);
    }

    private Money money(String amount) {
        return Money.of(currency, new BigDecimal(amount));
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
