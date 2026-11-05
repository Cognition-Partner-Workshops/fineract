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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.accountdetails.domain.AccountType;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.savings.SavingsCompoundingInterestPeriodType;
import org.apache.fineract.portfolio.savings.SavingsInterestCalculationDaysInYearType;
import org.apache.fineract.portfolio.savings.SavingsInterestCalculationType;
import org.apache.fineract.portfolio.savings.SavingsPeriodFrequencyType;
import org.apache.fineract.portfolio.savings.SavingsPostingInterestPeriodType;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

class SavingsAccountTest {

    private static final LocalDate SUBMITTED = LocalDate.of(2024, 1, 31);
    private static final LocalDate ACTIVATED = LocalDate.of(2024, 2, 1);
    private static final LocalDate POSTING_DATE = LocalDate.of(2024, 3, 1);
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_EVEN);
    private final MonetaryCurrency currency = new MonetaryCurrency("USD", 2, 1);
    private final AppUser user = mock(AppUser.class);
    private MockedStatic<MoneyHelper> moneyHelper;

    @BeforeEach
    void setUp() {
        moneyHelper = mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(MC);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, POSTING_DATE.plusDays(4))));
    }

    @AfterEach
    void tearDown() {
        moneyHelper.close();
        ThreadLocalContextUtil.reset();
    }

    @Test
    void approvalAndUndoRestorePendingApplication() {
        SavingsAccount account = application("0", "0", SavingsPeriodFrequencyType.MONTHS);
        assertTrue(account.isSubmittedAndPendingApproval());
        assertTrue(account.approveApplication(user, command("approvedOnDate", SUBMITTED)).containsKey("status"));
        assertTrue(account.isApproved());
        assertEquals(SUBMITTED, account.getApprovedOnDate());
        assertTrue(account.undoApplicationApproval().containsKey("approvedOnDate"));
        assertTrue(account.isSubmittedAndPendingApproval());
        assertNull(account.getApprovedOnDate());
        assertThrows(PlatformApiDataValidationException.class, account::undoApplicationApproval);
    }

    @ParameterizedTest
    @CsvSource({ "DAYS,2024-02-02", "WEEKS,2024-02-08", "MONTHS,2024-03-01", "YEARS,2025-02-01" })
    void activationSetsCalendarLockInAndRejectsRepeatedApprovalOrActivation(SavingsPeriodFrequencyType type, LocalDate lockedUntil) {
        SavingsAccount account = application("0", "0", type);
        assertThrows(PlatformApiDataValidationException.class, () -> account.activate(user, command("activatedOnDate", ACTIVATED)));
        account.approveApplication(user, command("approvedOnDate", SUBMITTED));
        account.activate(user, command("activatedOnDate", ACTIVATED));
        assertTrue(account.isActive());
        assertEquals(ACTIVATED, account.getActivationDate());
        assertEquals(lockedUntil, account.getLockedInUntilDate());
        assertThrows(PlatformApiDataValidationException.class, () -> account.activate(user, command("activatedOnDate", ACTIVATED)));
        assertThrows(PlatformApiDataValidationException.class,
                () -> account.approveApplication(user, command("approvedOnDate", ACTIVATED)));
        assertThrows(PlatformApiDataValidationException.class, account::undoApplicationApproval);
    }

    @ParameterizedTest
    @CsvSource({ "approvedOnDate,2024-01-30", "approvedOnDate,2024-03-06", "rejectedOnDate,2024-01-30", "rejectedOnDate,2024-03-06",
            "withdrawnOnDate,2024-01-30", "withdrawnOnDate,2024-03-06" })
    void applicationTransitionsRejectDatesBeforeSubmissionOrAfterBusinessDate(String parameter, LocalDate date) {
        SavingsAccount account = application("0", "0", SavingsPeriodFrequencyType.MONTHS);
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class, () -> {
            switch (parameter) {
                case "approvedOnDate" -> account.approveApplication(user, command(parameter, date));
                case "rejectedOnDate" -> account.rejectApplication(user, command(parameter, date));
                case "withdrawnOnDate" -> account.applicantWithdrawsFromApplication(user, command(parameter, date));
                default -> throw new IllegalArgumentException(parameter);
            }
        });
        assertTrue(error.getErrors().stream().anyMatch(e -> parameter.equals(e.getParameterName())));
    }

    @Test
    void rejectionAndWithdrawalCloseApplicationsAndPreventApproval() {
        SavingsAccount rejected = application("0", "0", SavingsPeriodFrequencyType.MONTHS);
        Map<String, Object> changes = rejected.rejectApplication(user, command("rejectedOnDate", ACTIVATED));
        assertEquals(SavingsAccountStatusType.REJECTED, rejected.getStatus());
        assertTrue(changes.containsKey("closedOnDate"));
        assertThrows(PlatformApiDataValidationException.class,
                () -> rejected.approveApplication(user, command("approvedOnDate", ACTIVATED)));
        SavingsAccount withdrawn = application("0", "0", SavingsPeriodFrequencyType.MONTHS);
        withdrawn.applicantWithdrawsFromApplication(user, command("withdrawnOnDate", ACTIVATED));
        assertEquals(SavingsAccountStatusType.WITHDRAWN_BY_APPLICANT, withdrawn.getStatus());
        assertEquals(ACTIVATED, withdrawn.getWithdrawnOnDate());
        assertEquals(user, withdrawn.getWithdrawnBy());
        assertThrows(PlatformApiDataValidationException.class,
                () -> withdrawn.applicantWithdrawsFromApplication(user, command("withdrawnOnDate", ACTIVATED)));
    }

    @ParameterizedTest
    @CsvSource({ "2024-01-30", "2024-03-06" })
    void activationRejectsDatesBeforeApprovalOrAfterBusinessDate(LocalDate date) {
        SavingsAccount account = application("0", "0", SavingsPeriodFrequencyType.MONTHS);
        account.approveApplication(user, command("approvedOnDate", SUBMITTED));
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> account.activate(user, command("activatedOnDate", date)));
        assertTrue(error.getErrors().stream().anyMatch(e -> "activatedOnDate".equals(e.getParameterName())));
    }

    @Test
    void realInterestPostingIncludesLeapDayAndDoesNotDuplicateExistingPosting() {
        SavingsAccount account = active("3.65", "0");
        account.addTransaction(SavingsAccountTransaction.deposit(account, account.office(), null, ACTIVATED, money("1000"), null));
        postInterest(account, false);
        assertAmount("2.90", account.summary.getTotalInterestPosted());
        assertAmount("1002.90", account.getAccountBalance());
        List<SavingsAccountTransaction> posted = account.getTransactions().stream()
                .filter(SavingsAccountTransaction::isInterestPostingAndNotReversed).toList();
        assertEquals(1, posted.size());
        assertEquals(POSTING_DATE, posted.get(0).getTransactionDate());
        postInterest(account, false);
        assertEquals(2, account.getTransactions().size());
        assertAmount("1002.90", account.getAccountBalance());
    }

    @Test
    void backdatedDepositCorrectsInterestByReversingOldPosting() {
        SavingsAccount account = active("3.65", "0");
        account.addTransaction(SavingsAccountTransaction.deposit(account, account.office(), null, ACTIVATED, money("1000"), null));
        SavingsAccountTransaction withdrawal = SavingsAccountTransaction.withdrawal(account, account.office(), null,
                LocalDate.of(2024, 2, 15), money("100"), null);
        account.addTransaction(withdrawal);
        postInterest(account, false);
        SavingsAccountTransaction originalPosting = account.getTransactions().stream()
                .filter(SavingsAccountTransaction::isInterestPostingAndNotReversed).findFirst().orElseThrow();
        assertAmount("2.75", originalPosting.getAmount());
        SavingsAccountTransaction backdatedDeposit = SavingsAccountTransaction.deposit(account, account.office(), null,
                LocalDate.of(2024, 2, 10), money("1000"), null);
        account.addTransaction(backdatedDeposit);
        postInterest(account, true);
        assertTrue(originalPosting.isReversed());
        assertEquals(1, account.getTransactions().stream().filter(SavingsAccountTransaction::isReversalTransaction).count());
        assertEquals(1, account.getTransactions().stream().filter(SavingsAccountTransaction::isInterestPostingAndNotReversed)
                .filter(transaction -> !transaction.isReversalTransaction()).count());
        assertAmount("2000", backdatedDeposit.getRunningBalance());
        assertAmount("1900", withdrawal.getRunningBalance());
        assertAmount("4.75", account.summary.getTotalInterestPosted());
        assertAmount("1904.75", account.getAccountBalance());
    }

    @Test
    void overdraftInterestPostsAsDebitWithPositiveTransactionAmount() {
        SavingsAccount account = active("0", "36.5");
        account.addTransaction(SavingsAccountTransaction.withdrawal(account, account.office(), null, ACTIVATED, money("100"), null));
        postInterest(account, false);
        SavingsAccountTransaction interest = account.getTransactions().stream()
                .filter(SavingsAccountTransaction::isOverdraftInterestAndNotReversed).findFirst().orElseThrow();
        assertAmount("2.90", interest.getAmount());
        assertTrue(interest.isDebit());
        assertAmount("-102.90", account.getAccountBalance());
        assertFalse(account.getTransactions().stream().anyMatch(SavingsAccountTransaction::isInterestPostingAndNotReversed));
    }

    @Test
    void zeroRateRecalculatesBalancesWithoutPostingInterestOrCountingReversedDeposit() {
        SavingsAccount account = active("0", "0");
        SavingsAccountTransaction reversed = SavingsAccountTransaction.deposit(account, account.office(), null, ACTIVATED, money("99"),
                null);
        reversed.reverse();
        account.addTransaction(reversed);
        account.addTransaction(SavingsAccountTransaction.deposit(account, account.office(), null, ACTIVATED, money("100"), null));
        postInterest(account, false);
        assertEquals(2, account.getTransactions().size());
        assertAmount("100", account.getAccountBalance());
        assertNull(reversed.getRunningBalance());
    }

    private void postInterest(SavingsAccount account, boolean reversals) {
        account.postInterest(MC, POSTING_DATE, false, false, 1, null, false, reversals);
    }

    private SavingsAccount active(String rate, String overdraftRate) {
        SavingsAccount account = application(rate, overdraftRate, SavingsPeriodFrequencyType.INVALID);
        account.approveApplication(user, command("approvedOnDate", SUBMITTED));
        account.activate(user, command("activatedOnDate", ACTIVATED));
        return account;
    }

    private SavingsAccount application(String rate, String overdraftRate, SavingsPeriodFrequencyType lockType) {
        SavingsProduct product = mock(SavingsProduct.class);
        when(product.currency()).thenReturn(currency);
        when(product.minBalanceForInterestCalculation()).thenReturn(BigDecimal.ZERO);
        Client client = mock(Client.class);
        when(client.getOffice()).thenReturn(mock(Office.class));
        SavingsAccount account = SavingsAccount.createNewApplicationForSubmittal(client, null, product, null, "SAV-001", ExternalId.empty(),
                AccountType.INDIVIDUAL, SUBMITTED, user, new BigDecimal(rate), SavingsCompoundingInterestPeriodType.MONTHLY,
                SavingsPostingInterestPeriodType.MONTHLY, SavingsInterestCalculationType.DAILY_BALANCE,
                SavingsInterestCalculationDaysInYearType.DAYS_365, BigDecimal.ZERO, 1, lockType, false, Set.of(),
                new BigDecimal(overdraftRate).signum() > 0, new BigDecimal("1000"), false, BigDecimal.ZERO, BigDecimal.ZERO, false,
                new BigDecimal(overdraftRate), BigDecimal.ZERO, false);
        account.setHelpers(new SavingsAccountTransactionSummaryWrapper(),
                new SavingsHelper(mock(AccountTransfersReadPlatformService.class)));
        return account;
    }

    private JsonCommand command(String parameter, LocalDate date) {
        JsonCommand command = mock(JsonCommand.class);
        when(command.localDateValueOfParameterNamed(parameter)).thenReturn(date);
        when(command.stringValueOfParameterNamed(parameter)).thenReturn(date.toString());
        when(command.dateFormat()).thenReturn("yyyy-MM-dd");
        when(command.locale()).thenReturn("en");
        when(command.extractLocale()).thenReturn(Locale.ENGLISH);
        return command;
    }

    private Money money(String amount) {
        return Money.of(currency, new BigDecimal(amount));
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
