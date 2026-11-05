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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanBuyDownFeeBalance;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCapitalizedIncomeBalance;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.exception.LoanTransactionProcessingException;
import org.apache.fineract.portfolio.loanaccount.repository.LoanBuyDownFeeBalanceRepository;
import org.apache.fineract.portfolio.loanaccount.repository.LoanCapitalizedIncomeBalanceRepository;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanTransactionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ProgressiveLoanTransactionValidatorImplTest {

    private FromJsonHelper fromJsonHelper;
    private LoanTransactionValidator loanTransactionValidator;
    private ProgressiveLoanTransactionValidatorImpl validator;

    @BeforeEach
    void setUp() {
        fromJsonHelper = mock(FromJsonHelper.class);
        loanTransactionValidator = mock(LoanTransactionValidator.class);
        validator = new ProgressiveLoanTransactionValidatorImpl(fromJsonHelper, loanTransactionValidator, mock(LoanRepositoryWrapper.class),
                mock(LoanCapitalizedIncomeBalanceRepository.class), mock(LoanBuyDownFeeBalanceRepository.class),
                mock(LoanTransactionRepository.class), mock(LoanMaximumAmountCalculator.class));
    }

    @Test
    void rejectsBlankCapitalizedIncomeAndBuyDownFeeCommands() {
        JsonCommand command = mock(JsonCommand.class);
        when(command.json()).thenReturn(" ");

        assertThrows(RuntimeException.class, () -> validator.validateCapitalizedIncome(command, 1L));
        assertThrows(RuntimeException.class, () -> validator.validateCapitalizedIncomeAdjustment(command, 1L, 2L));
        assertThrows(RuntimeException.class, () -> validator.validateBuyDownFee(command, 1L));
        assertThrows(RuntimeException.class, () -> validator.validateBuyDownFeeAdjustment(command, 1L, 2L));
    }

    @Test
    void acceptsBuyDownFeeForActiveLoan() {
        Loan loan = mock(Loan.class);
        LoanStatus status = mock(LoanStatus.class);
        when(loan.getStatus()).thenReturn(status);
        when(status.isActive()).thenReturn(true);

        assertDoesNotThrow(() -> validator.validateBuyDownFeeEligibility(loan));
    }

    @Test
    void rejectsBuyDownFeeForInactiveLoan() {
        Loan loan = mock(Loan.class);
        LoanStatus status = mock(LoanStatus.class);
        when(loan.getStatus()).thenReturn(status);
        when(status.isActive()).thenReturn(false);

        LoanTransactionProcessingException exception = assertThrows(LoanTransactionProcessingException.class,
                () -> validator.validateBuyDownFeeEligibility(loan));
        org.junit.jupiter.api.Assertions.assertEquals("Buy Down fees can only be added to active loans", exception.getMessage());
    }

    @Test
    void delegatesCommandValidationToCoreValidator() {
        validator.validateUndoChargeOff("{}");
        validator.validateTransaction("{}");
        validator.validateChargebackTransaction("{}");
        validator.validateNewRepaymentTransaction("{}");
        validator.validateTransactionWithNoAmount("{}");
        validator.validateChargeOffTransaction("{}");
        validator.validateUpdateOfLoanOfficer("{}");
        validator.validateForBulkLoanReassignment("{}");
        validator.validateMarkAsFraudLoan("{}");
        validator.validateNewRefundTransaction("{}");
        validator.validateLoanForeclosure("{}");
        validator.validateManualInterestRefundTransaction("{}");

        verify(loanTransactionValidator).validateUndoChargeOff("{}");
        verify(loanTransactionValidator).validateTransaction("{}");
        verify(loanTransactionValidator).validateChargebackTransaction("{}");
        verify(loanTransactionValidator).validateNewRepaymentTransaction("{}");
        verify(loanTransactionValidator).validateTransactionWithNoAmount("{}");
        verify(loanTransactionValidator).validateChargeOffTransaction("{}");
        verify(loanTransactionValidator).validateUpdateOfLoanOfficer("{}");
        verify(loanTransactionValidator).validateForBulkLoanReassignment("{}");
        verify(loanTransactionValidator).validateMarkAsFraudLoan("{}");
        verify(loanTransactionValidator).validateNewRefundTransaction("{}");
        verify(loanTransactionValidator).validateLoanForeclosure("{}");
        verify(loanTransactionValidator).validateManualInterestRefundTransaction("{}");
    }

    @Test
    void validatesCapitalizedIncomeAgainstApprovedAndOverAppliedLimits() {
        CommandFixture fixture = new CommandFixture();
        try (MockedStatic<DateUtils> dates = Mockito.mockStatic(DateUtils.class, Mockito.CALLS_REAL_METHODS)) {
            dates.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2024, 2, 1));
            assertDoesNotThrow(() -> fixture.validator.validateCapitalizedIncome(command("2024-01-15", "10.00"), 1L));
            assertError("exceeds.approved.amount", () -> fixture.validator.validateCapitalizedIncome(command("2024-01-15", "10.01"), 1L));
            when(fixture.loan.loanProduct().isAllowApprovedDisbursedAmountsOverApplied()).thenReturn(true);
            when(fixture.maximum.getOverAppliedMax(fixture.loan)).thenReturn(new BigDecimal("120.00"));
            assertDoesNotThrow(() -> fixture.validator.validateCapitalizedIncome(command("2024-01-15", "30.00"), 1L));
            assertError("exceeds.approved.amount", () -> fixture.validator.validateCapitalizedIncome(command("2024-01-15", "30.01"), 1L));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "capitalized", "capitalizedAdjustment", "buyDownAdjustment" })
    void rejectsInvalidLoanEligibilityAndTransactionDates(String operation) {
        CommandFixture fixture = new CommandFixture();
        when(fixture.loan.isDisbursed()).thenReturn(false);
        when(fixture.loan.isProgressiveSchedule()).thenReturn(false);
        when(fixture.loan.getLoanProductRelatedDetail().isEnableIncomeCapitalization()).thenReturn(false);
        when(fixture.loan.getLoanProductRelatedDetail().isEnableBuyDownFee()).thenReturn(false);
        when(fixture.loan.getStatus()).thenReturn(LoanStatus.APPROVED);
        try (MockedStatic<DateUtils> dates = Mockito.mockStatic(DateUtils.class, Mockito.CALLS_REAL_METHODS)) {
            dates.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2024, 1, 1));
            assertError("not.progressive.loan", () -> fixture.validate(operation, command("2024-01-15", "10.00")));
            assertError("not.valid.loan.status", () -> fixture.validate(operation, command("2024-01-15", "10.00")));
            assertError("cannot.be.in.the.future", () -> fixture.validate(operation, command("2024-01-15", "10.00")));
            assertError("before.disbursement.date", () -> fixture.validate(operation, command("2023-12-31", "10.00")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "capitalizedAdjustment", "buyDownAdjustment" })
    void adjustmentCannotExceedRemainingAmountAndMustReferenceExistingTransaction(String operation) {
        CommandFixture fixture = new CommandFixture();
        try (MockedStatic<DateUtils> dates = Mockito.mockStatic(DateUtils.class, Mockito.CALLS_REAL_METHODS)) {
            dates.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2024, 2, 1));
            assertDoesNotThrow(() -> fixture.validate(operation, command("2024-01-15", "8.00")));
            assertError("cannot.be.more.than.remaining.amount", () -> fixture.validate(operation, command("2024-01-15", "8.01")));
            when(fixture.transactions.findById(2L)).thenReturn(Optional.empty());
            assertError("loan.transaction.not.found", () -> fixture.validate(operation, command("2024-01-15", "8.00")));
        }
    }

    @Test
    void buyDownFeeValidatesEnabledFeatureDateAmountAndAdjustmentType() {
        CommandFixture fixture = new CommandFixture();
        assertDoesNotThrow(() -> fixture.validator.validateBuyDownFee(command("2024-01-15", "10.00"), 1L));
        when(fixture.loan.getLoanProductRelatedDetail().isEnableBuyDownFee()).thenReturn(false);
        assertError("buy.down.fee.not.enabled", () -> fixture.validator.validateBuyDownFee(command("2024-01-15", "10.00"), 1L));
        assertError("cannot.be.before.first.disbursement.date",
                () -> fixture.validator.validateBuyDownFee(command("2023-12-31", "10.00"), 1L));
        try (MockedStatic<DateUtils> dates = Mockito.mockStatic(DateUtils.class, Mockito.CALLS_REAL_METHODS)) {
            dates.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2024, 2, 1));
            when(fixture.transaction.isBuyDownFee()).thenReturn(false);
            assertError("not.buyDownFee.transaction",
                    () -> fixture.validator.validateBuyDownFeeAdjustment(command("2024-01-15", "8.00"), 1L, 2L));
            when(fixture.buyDownBalances.findByLoanIdAndLoanTransactionIdAndDeletedFalseAndClosedFalse(1L, 2L)).thenReturn(null);
            assertError("buydown.fee.balance.not.found",
                    () -> fixture.validator.validateBuyDownFeeAdjustment(command("2024-01-15", "8.00"), 1L, 2L));
        }
    }

    @Test
    void undoContractTerminationRequiresOpenTerminatedLoanAndLastUserTransaction() {
        CommandFixture fixture = new CommandFixture();
        JsonCommand command = mock(JsonCommand.class);
        when(command.json()).thenReturn("{}");
        when(fixture.loan.isOpen()).thenReturn(false);
        assertThrows(GeneralPlatformDomainRuleException.class, () -> fixture.validator.validateContractTerminationUndo(command, 1L));
        when(fixture.loan.isOpen()).thenReturn(true);
        when(fixture.loan.isContractTermination()).thenReturn(false);
        assertThrows(GeneralPlatformDomainRuleException.class, () -> fixture.validator.validateContractTerminationUndo(command, 1L));
        when(fixture.loan.isContractTermination()).thenReturn(true);
        when(fixture.loan.findContractTerminationTransaction()).thenReturn(null);
        assertThrows(GeneralPlatformDomainRuleException.class, () -> fixture.validator.validateContractTerminationUndo(command, 1L));
        when(fixture.loan.findContractTerminationTransaction()).thenReturn(fixture.transaction);
        when(fixture.loan.getLastUserTransaction()).thenReturn(mock(LoanTransaction.class));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> fixture.validator.validateContractTerminationUndo(command, 1L));
        when(fixture.loan.getLastUserTransaction()).thenReturn(fixture.transaction);
        assertDoesNotThrow(() -> fixture.validator.validateContractTerminationUndo(command, 1L));
    }

    private static JsonCommand command(String date, String amount) {
        JsonCommand command = mock(JsonCommand.class);
        when(command.json()).thenReturn("{\"transactionDate\":\"" + date + "\",\"transactionAmount\":" + amount
                + ",\"dateFormat\":\"yyyy-MM-dd\",\"locale\":\"en\"}");
        return command;
    }

    private static void assertError(String suffix, org.junit.jupiter.api.function.Executable action) {
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class, action);
        assertTrue(exception.getErrors().stream().anyMatch(error -> error.getUserMessageGlobalisationCode().endsWith(suffix)),
                () -> exception.getErrors().toString());
    }

    private static class CommandFixture {

        private final Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        private final LoanTransaction transaction = mock(LoanTransaction.class);
        private final LoanTransactionRepository transactions = mock(LoanTransactionRepository.class);
        private final LoanBuyDownFeeBalanceRepository buyDownBalances = mock(LoanBuyDownFeeBalanceRepository.class);
        private final LoanMaximumAmountCalculator maximum = mock(LoanMaximumAmountCalculator.class);
        private final ProgressiveLoanTransactionValidatorImpl validator;

        CommandFixture() {
            LoanRepositoryWrapper loans = mock(LoanRepositoryWrapper.class);
            LoanCapitalizedIncomeBalanceRepository balances = mock(LoanCapitalizedIncomeBalanceRepository.class);
            validator = new ProgressiveLoanTransactionValidatorImpl(new FromJsonHelper(), mock(LoanTransactionValidator.class), loans,
                    balances, buyDownBalances, transactions, maximum);
            when(loans.findOneWithNotFoundDetection(1L, true)).thenReturn(loan);
            when(loan.isDisbursed()).thenReturn(true);
            when(loan.isProgressiveSchedule()).thenReturn(true);
            when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
            when(loan.getLoanProductRelatedDetail().isEnableIncomeCapitalization()).thenReturn(true);
            when(loan.getLoanProductRelatedDetail().isEnableBuyDownFee()).thenReturn(true);
            when(loan.getDisbursementDate()).thenReturn(LocalDate.of(2024, 1, 1));
            when(loan.getDisbursedAmount()).thenReturn(new BigDecimal("80.00"));
            when(loan.getSummary().getTotalCapitalizedIncome()).thenReturn(new BigDecimal("10.00"));
            when(loan.getApprovedPrincipal()).thenReturn(new BigDecimal("100.00"));
            when(transactions.findById(2L)).thenReturn(Optional.of(transaction));
            when(transaction.getTransactionDate()).thenReturn(LocalDate.of(2024, 1, 10));
            when(transaction.isBuyDownFee()).thenReturn(true);
            LoanCapitalizedIncomeBalance incomeBalance = mock(LoanCapitalizedIncomeBalance.class);
            when(incomeBalance.getAmount()).thenReturn(new BigDecimal("10.00"));
            when(incomeBalance.getAmountAdjustment()).thenReturn(new BigDecimal("2.00"));
            when(balances.findByLoanIdAndLoanTransactionIdAndDeletedFalseAndClosedFalse(1L, 2L)).thenReturn(incomeBalance);
            LoanBuyDownFeeBalance feeBalance = mock(LoanBuyDownFeeBalance.class);
            when(feeBalance.getAmount()).thenReturn(new BigDecimal("10.00"));
            when(feeBalance.getAmountAdjustment()).thenReturn(new BigDecimal("2.00"));
            when(buyDownBalances.findByLoanIdAndLoanTransactionIdAndDeletedFalseAndClosedFalse(1L, 2L)).thenReturn(feeBalance);
        }

        void validate(String operation, JsonCommand command) {
            switch (operation) {
                case "capitalized" -> validator.validateCapitalizedIncome(command, 1L);
                case "capitalizedAdjustment" -> validator.validateCapitalizedIncomeAdjustment(command, 1L, 2L);
                case "buyDownAdjustment" -> validator.validateBuyDownFeeAdjustment(command, 1L, 2L);
                default -> throw new IllegalArgumentException(operation);
            }
        }
    }
}
