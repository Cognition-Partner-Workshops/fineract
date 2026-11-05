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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.LocalDateInterval;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.savings.domain.interest.EndOfDayBalance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class SavingsAccountTransactionTest {

    private static final LocalDate DATE = LocalDate.of(2024, 2, 28);
    private final MonetaryCurrency currency = new MonetaryCurrency("USD", 2, 1);
    private MockedStatic<MoneyHelper> moneyHelper;

    @BeforeEach
    void setUp() {
        moneyHelper = mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DATE.plusDays(1))));
    }

    @AfterEach
    void tearDown() {
        moneyHelper.close();
        ThreadLocalContextUtil.reset();
    }

    @Test
    void reversalPreservesAmountAndDateButCannotBeCountedAsCreditOrDebit() {
        SavingsAccountTransaction deposit = deposit("12.34");
        assertTrue(deposit.isCredit());
        assertFalse(deposit.isDebit());
        assertEquals(DATE.plusDays(1), deposit.getSubmittedOnDate());
        SavingsAccountTransaction reversal = SavingsAccountTransaction.reversal(deposit);
        assertTrue(reversal.isReversalTransaction());
        assertTrue(reversal.isNotReversed());
        assertFalse(reversal.isCredit());
        assertFalse(reversal.isDebit());
        assertEquals(deposit.getAmount(), reversal.getAmount());
        assertEquals(DATE, reversal.getTransactionDate());
        deposit.reverse();
        assertFalse(deposit.isCredit());
        assertTrue(deposit.isReversed());
    }

    @Test
    void cumulativeBalanceIncludesLeapDayAndCanBeCleared() {
        SavingsAccountTransaction deposit = deposit("10");
        deposit.setRunningBalance(money("110"));
        deposit.updateCumulativeBalanceAndDates(currency, LocalDate.of(2024, 3, 1));
        assertEquals(3, deposit.getBalanceNumberOfDays());
        assertAmount("330", deposit.getCumulativeBalance());
        LocalDateInterval interval = LocalDateInterval.create(DATE, DATE.plusDays(2));
        assertTrue(deposit.isAcceptableForDailyBalance(interval));
        assertTrue(deposit.fallsWithin(interval));
        assertTrue(deposit.spansAnyPortionOf(LocalDateInterval.create(DATE.plusDays(1), DATE.plusDays(3))));
        assertTrue(deposit.isBalanceInExistencesForOneDayOrMore());
        deposit.reverse();
        assertFalse(deposit.isAcceptableForDailyBalance(interval));
        deposit.zeroBalanceFields();
        assertNull(deposit.getRunningBalance());
        assertNull(deposit.getCumulativeBalance());
        assertNull(deposit.getBalanceEndDate());
        assertFalse(deposit.isBalanceInExistencesForOneDayOrMore());
    }

    @Test
    void depositEndOfDayBalanceStopsBeforeNextTransaction() {
        SavingsAccountTransaction deposit = deposit("10");
        EndOfDayBalance balance = deposit.toEndOfDayBalance(money("100"), DATE.plusDays(3));
        assertAmount("110", balance.closingBalance().getAmount());
        assertEquals(3, balance.getNumberOfDays());
        assertEquals(1, deposit.toEndOfDayBalance(money("100"), DATE).getNumberOfDays());
    }

    @Test
    void withdrawalEndOfDayBalanceSubtractsFromOpeningBalance() {
        SavingsAccountTransaction withdrawal = SavingsAccountTransaction.withdrawal(null, null, null, DATE, money("25"), null);
        EndOfDayBalance balance = withdrawal.toEndOfDayBalance(money("100"), DATE.plusDays(2));
        assertAmount("75", balance.closingBalance().getAmount());
        assertEquals(2, balance.getNumberOfDays());
        assertTrue(withdrawal.isDebit());
        assertTrue(withdrawal.canProcessBalanceCheck());
        withdrawal.reverse();
        assertFalse(withdrawal.isDebit());
        assertFalse(withdrawal.canProcessBalanceCheck());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void zeroOpeningBalanceUsesOverdraftPolicy(boolean allowOverdraft) {
        SavingsAccount account = mock(SavingsAccount.class);
        when(account.allowOverdraft()).thenReturn(allowOverdraft);
        SavingsAccountTransaction withdrawal = SavingsAccountTransaction.withdrawal(account, null, null, DATE, money("25"), null);
        withdrawal.setRunningBalance(money("7"));
        withdrawal.updateCumulativeBalanceAndDates(currency, DATE);
        EndOfDayBalance balance = withdrawal.toEndOfDayBalance(money("0"));
        assertAmount(allowOverdraft ? "-25" : "7", balance.closingBalance().getAmount());
        assertEquals(1, balance.getNumberOfDays());
    }

    @Test
    void periodBoundariesClipExistingRunningBalanceWithoutAddingDepositAgain() {
        SavingsAccountTransaction deposit = deposit("10");
        deposit.setRunningBalance(money("110"));
        deposit.updateCumulativeBalanceAndDates(currency, DATE.plusDays(10));
        LocalDateInterval bounds = LocalDateInterval.create(DATE.plusDays(2), DATE.plusDays(4));
        EndOfDayBalance balance = deposit.toEndOfDayBalanceBoundedBy(money("110"), bounds);
        assertEquals(bounds.startDate(), balance.date());
        assertEquals(3, balance.getNumberOfDays());
        assertAmount("110", balance.closingBalance().getAmount());
        EndOfDayBalance periodBalance = deposit.toEndOfDayBalance(bounds, currency);
        assertEquals(3, periodBalance.getNumberOfDays());
        assertAmount("110", periodBalance.closingBalance().getAmount());
    }

    @Test
    void holdAndReleasePreserveAmountAndTrackReleaseSeparately() {
        SavingsAccountTransaction hold = SavingsAccountTransaction.holdAmount(null, null, null, DATE, money("75"), true);
        assertTrue(hold.isAmountOnHold());
        assertTrue(hold.isAmountOnHoldNotReleased());
        SavingsAccountTransaction release = SavingsAccountTransaction.releaseAmount(hold, DATE.plusDays(1));
        assertTrue(release.isAmountRelease());
        assertEquals(hold.getAmount(), release.getAmount());
        assertEquals(DATE.plusDays(1), release.getTransactionDate());
        hold.updateReleaseId(42L);
        assertFalse(hold.isAmountOnHoldNotReleased());
        assertEquals(42L, hold.getReleaseIdOfHoldAmountTransaction());
    }

    @Test
    void accountingMapIncludesCurrencyOfficeAndOverdraftAmounts() {
        Office office = mock(Office.class);
        when(office.getId()).thenReturn(2L);
        SavingsAccountTransaction transaction = SavingsAccountTransaction.withHoldTax(null, office, DATE, money("1.25"), null);
        transaction.setOverdraftAmount(money("0.50"));
        Map<String, Object> data = transaction.toMapData("USD");
        assertEquals(2L, data.get("officeId"));
        assertEquals("USD", data.get("currencyCode"));
        assertEquals(DATE, data.get("date"));
        assertAmount("1.25", (BigDecimal) data.get("amount"));
        assertAmount("0.50", (BigDecimal) data.get("overdraftAmount"));
        assertEquals(false, data.get("reversed"));
    }

    @Test
    void chargeLinksDeterminePenaltyAndMinimumBalanceOverride() {
        SavingsAccountCharge charge = mock(SavingsAccountCharge.class);
        when(charge.isPenaltyCharge()).thenReturn(true);
        when(charge.canOverriteSavingAccountRules()).thenReturn(true);
        SavingsAccountTransaction transaction = SavingsAccountTransaction.charge(null, null, DATE, money("5"));
        assertTrue(transaction.canProcessBalanceCheck());
        transaction.getSavingsAccountChargesPaid().add(SavingsAccountChargePaidBy.instance(transaction, charge, new BigDecimal("5")));
        assertTrue(transaction.isPenaltyChargeAndNotReversed());
        assertFalse(transaction.isFeeChargeAndNotReversed());
        assertTrue(transaction.isPaymentForCurrentCharge(charge));
        assertFalse(transaction.canProcessBalanceCheck());
        transaction.reverse();
        assertFalse(transaction.isPenaltyChargeAndNotReversed());
    }

    private SavingsAccountTransaction deposit(String amount) {
        return SavingsAccountTransaction.deposit(null, null, null, DATE, money(amount), null);
    }

    private Money money(String amount) {
        return Money.of(currency, new BigDecimal(amount));
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
