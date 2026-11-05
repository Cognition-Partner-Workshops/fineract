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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.apache.fineract.portfolio.savings.domain.interest.PostingPeriod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

class SavingsAccountSummaryTest {

    private static final LocalDate DATE = LocalDate.of(2024, 2, 29);
    private final MonetaryCurrency currency = new MonetaryCurrency("USD", 2, 1);
    private final SavingsAccountTransactionSummaryWrapper wrapper = new SavingsAccountTransactionSummaryWrapper();
    private MockedStatic<MoneyHelper> moneyHelper;

    @BeforeEach
    void setUp() {
        moneyHelper = mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DATE)));
    }

    @AfterEach
    void tearDown() {
        moneyHelper.close();
        ThreadLocalContextUtil.reset();
    }

    @Test
    void fullSummaryNetsInterestChargesTaxAndWithdrawalsIgnoringReversedTransactions() {
        List<SavingsAccountTransaction> transactions = new ArrayList<>();
        transactions.add(transaction(SavingsAccountTransactionType.DEPOSIT, "1000"));
        transactions.add(transaction(SavingsAccountTransactionType.WITHDRAWAL, "200"));
        transactions.add(transaction(SavingsAccountTransactionType.INTEREST_POSTING, "12.35"));
        transactions.add(transaction(SavingsAccountTransactionType.WITHDRAWAL_FEE, "2"));
        transactions.add(transaction(SavingsAccountTransactionType.ANNUAL_FEE, "3"));
        transactions.add(charged(false, false, "4"));
        transactions.add(charged(true, false, "5"));
        transactions.add(charged(false, true, "6"));
        transactions.add(charged(true, true, "7"));
        transactions.add(transaction(SavingsAccountTransactionType.OVERDRAFT_INTEREST, "1.25"));
        transactions.add(transaction(SavingsAccountTransactionType.WITHHOLD_TAX, "2.10"));
        SavingsAccountTransaction reversed = transaction(SavingsAccountTransactionType.DEPOSIT, "9999");
        reversed.reverse();
        transactions.add(reversed);
        transactions.add(SavingsAccountTransaction.reversal(transactions.get(0)));

        SavingsAccountSummary summary = new SavingsAccountSummary();
        summary.updateSummary(currency, wrapper, transactions);

        assertAmount("795.00", summary.getAccountBalance());
        assertAmount("1000", summary.getTotalDeposits());
        assertAmount("200", summary.getTotalWithdrawals());
        assertAmount("12.35", summary.getTotalInterestPosted());
        assertAmount("2", summary.getTotalWithdrawalFees());
        assertAmount("3", summary.getTotalAnnualFees());
        assertAmount("4", summary.getTotalFeeCharge());
        assertAmount("5", summary.getTotalPenaltyCharge());
        assertAmount("1.25", summary.getTotalOverdraftInterestDerived());
        assertAmount("2.10", summary.getTotalWithholdTax());
        assertEquals(DATE, summary.getInterestPostedTillDate());
        assertAmount("6", wrapper.calculateTotalFeesChargeWaived(currency, transactions));
        assertAmount("7", wrapper.calculateTotalPenaltyChargeWaived(currency, transactions));
        assertTrue(summary.isLessThanOrEqualToAccountBalance(money("795")));
        assertFalse(summary.isLessThanOrEqualToAccountBalance(money("795.01")));
    }

    @ParameterizedTest
    @EnumSource(value = SavingsAccountTransactionType.class, names = { "DEPOSIT", "WITHDRAWAL", "INTEREST_POSTING", "WITHDRAWAL_FEE",
            "ANNUAL_FEE", "OVERDRAFT_INTEREST", "WITHHOLD_TAX" })
    void totalsExcludeBothReversedOriginalsAndReversalEntries(SavingsAccountTransactionType type) {
        SavingsAccountTransaction active = transaction(type, "12.34");
        SavingsAccountTransaction reversed = transaction(type, "100");
        reversed.reverse();
        List<SavingsAccountTransaction> transactions = List.of(active, reversed, SavingsAccountTransaction.reversal(active));
        BigDecimal total = switch (type) {
            case DEPOSIT -> wrapper.calculateTotalDeposits(currency, transactions);
            case WITHDRAWAL -> wrapper.calculateTotalWithdrawals(currency, transactions);
            case INTEREST_POSTING -> wrapper.calculateTotalInterestPosted(currency, transactions);
            case WITHDRAWAL_FEE -> wrapper.calculateTotalWithdrawalFees(currency, transactions);
            case ANNUAL_FEE -> wrapper.calculateTotalAnnualFees(currency, transactions);
            case OVERDRAFT_INTEREST -> wrapper.calculateTotalOverdraftInterest(currency, transactions);
            case WITHHOLD_TAX -> wrapper.calculateTotalWithholdTaxWithdrawal(currency, transactions);
            default -> throw new IllegalArgumentException(type.name());
        };
        assertAmount("12.34", total);
    }

    @ParameterizedTest
    @EnumSource(value = SavingsAccountTransactionType.class, names = { "DEPOSIT", "WITHDRAWAL", "WITHDRAWAL_FEE", "ANNUAL_FEE",
            "OVERDRAFT_INTEREST", "WITHHOLD_TAX" })
    void pivotUpdatesBalanceOnceAndIgnoresReversals(SavingsAccountTransactionType type) {
        SavingsAccountSummary summary = new SavingsAccountSummary();
        summary.setAccountBalance(new BigDecimal("100"));
        SavingsAccountTransaction transaction = transaction(type, "10");
        summary.updateSummaryWithPivotConfig(currency, wrapper, transaction, List.of());
        String expected = type == SavingsAccountTransactionType.DEPOSIT ? "110" : "90";
        assertAmount(expected, summary.getAccountBalance());
        summary.updateSummaryWithPivotConfig(currency, wrapper, SavingsAccountTransaction.reversal(transaction), List.of());
        assertAmount(expected, summary.getAccountBalance());
        transaction.reverse();
        summary.updateSummaryWithPivotConfig(currency, wrapper, transaction, List.of());
        assertAmount(expected, summary.getAccountBalance());
    }

    @Test
    void pivotInterestRecalculationStartsFromStoredBalanceAndAddsOnlyLiveTransactions() {
        SavingsAccountSummary summary = new SavingsAccountSummary();
        summary.setRunningBalanceOnPivotDate(new BigDecimal("500"));
        List<SavingsAccountTransaction> transactions = List.of(transaction(SavingsAccountTransactionType.DEPOSIT, "100"),
                transaction(SavingsAccountTransactionType.WITHDRAWAL, "20"),
                transaction(SavingsAccountTransactionType.INTEREST_POSTING, "5"),
                transaction(SavingsAccountTransactionType.OVERDRAFT_INTEREST, "2"),
                transaction(SavingsAccountTransactionType.WITHHOLD_TAX, "1"));
        summary.updateSummaryWithPivotConfig(currency, wrapper, null, transactions);
        assertAmount("582", summary.getAccountBalance());
        assertEquals(DATE, summary.getInterestPostedTillDate());
        assertAmount("5", wrapper.calculateTotalInterestPosted(currency, new BigDecimal("0"), transactions));
        assertAmount("12", wrapper.calculateTotalOverdraftInterest(currency, new BigDecimal("10"), transactions));
    }

    @Test
    void earnedInterestTreatsNullPeriodInterestAsZeroAndUsesBusinessDate() {
        PostingPeriod first = mock(PostingPeriod.class);
        PostingPeriod second = mock(PostingPeriod.class);
        Money earned = money("1.23");
        when(first.interest()).thenReturn(earned);
        SavingsAccountSummary summary = new SavingsAccountSummary();
        summary.updateFromInterestPeriodSummaries(currency, List.of(first, second));
        assertAmount("1.23", summary.getTotalInterestEarned());
        assertEquals(DATE, summary.getLastInterestCalculationDate());
    }

    @Test
    void emptyHistoryHasZeroBalanceAndNullDerivedTotals() {
        SavingsAccountSummary summary = new SavingsAccountSummary();
        summary.updateSummary(currency, wrapper, List.of());
        assertAmount("0", summary.getAccountBalance());
        assertNull(summary.getTotalDeposits());
        assertNull(summary.getTotalWithdrawals());
        assertNull(summary.getTotalInterestPosted());
        assertNull(summary.getInterestPostedTillDate());
    }

    private SavingsAccountTransaction transaction(SavingsAccountTransactionType type, String amount) {
        Money money = money(amount);
        return switch (type) {
            case DEPOSIT -> SavingsAccountTransaction.deposit(null, null, null, DATE, money, null);
            case WITHDRAWAL -> SavingsAccountTransaction.withdrawal(null, null, null, DATE, money, null);
            case INTEREST_POSTING -> SavingsAccountTransaction.interestPosting(null, null, DATE, money, false);
            case WITHDRAWAL_FEE -> SavingsAccountTransaction.withdrawalFee(null, null, DATE, money, null);
            case ANNUAL_FEE -> SavingsAccountTransaction.annualFee(null, null, DATE, money);
            case OVERDRAFT_INTEREST -> SavingsAccountTransaction.overdraftInterest(null, null, DATE, money, false);
            case WITHHOLD_TAX -> SavingsAccountTransaction.withHoldTax(null, null, DATE, money, null);
            default -> throw new IllegalArgumentException(type.name());
        };
    }

    private SavingsAccountTransaction charged(boolean penalty, boolean waiver, String amount) {
        SavingsAccountTransaction transaction = waiver ? SavingsAccountTransaction.waiver(null, null, DATE, money(amount))
                : SavingsAccountTransaction.charge(null, null, DATE, money(amount));
        SavingsAccountCharge charge = mock(SavingsAccountCharge.class);
        when(charge.isFeeCharge()).thenReturn(!penalty);
        when(charge.isPenaltyCharge()).thenReturn(penalty);
        transaction.getSavingsAccountChargesPaid().add(SavingsAccountChargePaidBy.instance(transaction, charge, new BigDecimal(amount)));
        return transaction;
    }

    private Money money(String amount) {
        return Money.of(currency, new BigDecimal(amount));
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
