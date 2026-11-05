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
package org.apache.fineract.portfolio.loanproduct.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.common.domain.DaysInMonthType;
import org.apache.fineract.portfolio.common.domain.DaysInYearType;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModelRepaymentPeriod;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ScheduledDateGenerator;
import org.apache.fineract.portfolio.loanproduct.calc.data.PeriodDueDetails;
import org.apache.fineract.portfolio.loanproduct.calc.data.ProgressiveLoanInterestScheduleModel;
import org.apache.fineract.portfolio.loanproduct.domain.ILoanConfigurationDetails;
import org.apache.fineract.portfolio.loanproduct.domain.InterestCalculationPeriodMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.apache.fineract.portfolio.loanproduct.domain.LoanPreCloseInterestCalculationStrategy;
import org.apache.fineract.portfolio.loanproduct.domain.RecalculationFrequencyType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ProgressiveEMICalculatorBranchTest {

    private static final CurrencyData CURRENCY = new CurrencyData("USD", "USD", 2, 1, "$", "USD");
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_EVEN);
    private ProgressiveEMICalculator calculator;
    private ILoanConfigurationDetails details;
    private static MockedStatic<MoneyHelper> moneyHelper;

    @BeforeAll
    static void initializeMoneyHelper() {
        moneyHelper = Mockito.mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(MC);
    }

    @AfterAll
    static void closeMoneyHelper() {
        moneyHelper.close();
    }

    @BeforeEach
    void setUp() {
        calculator = new ProgressiveEMICalculator(new DefaultScheduledDateGenerator());
        details = mock(ILoanConfigurationDetails.class);
        when(details.getCurrencyData()).thenReturn(CURRENCY);
        when(details.getAnnualNominalInterestRate()).thenReturn(BigDecimal.valueOf(12));
        when(details.getDaysInYearType()).thenReturn(DaysInYearType.DAYS_360.getValue());
        when(details.getDaysInMonthType()).thenReturn(DaysInMonthType.DAYS_30.getValue());
        when(details.getRepaymentPeriodFrequencyType()).thenReturn(PeriodFrequencyType.MONTHS);
        when(details.getRepayEvery()).thenReturn(1);
        when(details.getInterestMethod()).thenReturn(InterestMethod.DECLINING_BALANCE);
        when(details.getInterestCalculationPeriodMethod()).thenReturn(InterestCalculationPeriodMethod.DAILY);
        when(details.isAllowPartialPeriodInterestCalculation()).thenReturn(true);
        when(details.isAllowFullTermForTranche()).thenReturn(false);
        when(details.isInterestRecognitionOnDisbursementDate()).thenReturn(false);
    }

    @Test
    void recalculatesMoneyAfterCapitalizedIncomeAndBalanceCorrection() {
        ProgressiveLoanInterestScheduleModel model = model(3);

        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));
        assertEquals(34.00, amount(model.repaymentPeriods().getFirst().getEmi()), 0.01);
        calculator.addCapitalizedIncome(model, LocalDate.of(2024, 2, 1), money("10.00"));
        calculator.addBalanceCorrection(model, LocalDate.of(2024, 2, 15), money("5.00"));

        assertTrue(amount(model.getTotalDueInterest()) > 0);
        assertTrue(amount(model.getTotalOutstandingPrincipal()) > 0);
        assertEquals(110.00, amount(model.getTotalOutstandingPrincipal()), 0.01);
    }

    @Test
    void creditsAndPaymentsChangeDueAmountsAndOutstandingBalance() {
        ProgressiveLoanInterestScheduleModel model = model(3);
        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate due = LocalDate.of(2024, 2, 1);

        PeriodDueDetails before = calculator.getDueAmounts(model, from, due, due);
        assertTrue(amount(before.getDuePrincipal()) > 0);
        calculator.creditPrincipal(model, LocalDate.of(2024, 1, 15), money("2.00"));
        calculator.creditInterest(model, LocalDate.of(2024, 1, 15), money("0.50"));
        calculator.payInterest(model, from, due, due, money("0.50"));
        calculator.payPrincipal(model, from, due, due, money("5.00"));

        assertTrue(amount(calculator.getPeriodInterestTillDate(model, from, due, due, true)) >= 0);
        assertTrue(amount(calculator.getOutstandingLoanBalanceOfPeriod(model, due)) < 100);
        assertFalse(model.repaymentPeriods().getFirst().isFullyPaid());
    }

    @Test
    void interestPauseAndAdditionalPeriodsPreservePrincipalAccounting() {
        ProgressiveLoanInterestScheduleModel model = model(3);
        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));
        assertTrue(model.updateInterestPeriodsForInterestPause(LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 20)).isPresent());
        calculator.addRepaymentPeriods(model, LocalDate.of(2024, 2, 1), 2, List.<LoanTransaction>of());

        assertEquals(5, model.repaymentPeriods().size());
        assertEquals(100.00, amount(model.getTotalOutstandingPrincipal()), 0.01);
        assertEquals(LocalDate.of(2024, 6, 1), model.getMaturityDate());
    }

    @Test
    void rateChangeUsesNewRateForSubsequentPeriodsAndSupportsEmptyUpdates() {
        ProgressiveLoanInterestScheduleModel model = model(3);
        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));
        double oldInterest = amount(model.repaymentPeriods().get(1).getDueInterest());
        calculator.changeInterestRate(model, LocalDate.of(2024, 2, 2), BigDecimal.valueOf(6));
        double newInterest = amount(model.repaymentPeriods().get(1).getDueInterest());
        assertTrue(newInterest < oldInterest);
        calculator.updateModel(model, List.of(), value -> LocalDate.of(2024, 1, 1), value -> LocalDate.of(2024, 2, 1));
        assertEquals(3, model.repaymentPeriods().size());
    }

    @Test
    void reAmortizationMovesUnpaidPrincipalToFuturePeriods() {
        when(details.getAnnualNominalInterestRate()).thenReturn(BigDecimal.ZERO);
        ProgressiveLoanInterestScheduleModel model = model(3);
        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));
        calculator.payPrincipal(model, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 1), money("10.00"));

        calculator.updateModelRepaymentPeriodsDuringReAmortization(model, LocalDate.of(2024, 2, 1));

        assertEquals(10.00, amount(model.repaymentPeriods().getFirst().getEmi()));
        assertEquals(90.00, amount(model.getTotalOutstandingPrincipal()));
        assertEquals(0.00, amount(model.getTotalDueInterest()));
        assertTrue(model.repaymentPeriods().getFirst().isFullyPaid());
    }

    @Test
    void reAmortizationSplitsPastDueInterestEquallyWithoutLosingPrincipal() {
        ProgressiveLoanInterestScheduleModel model = model(3);
        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));
        assertEquals(1.00, amount(model.repaymentPeriods().getFirst().getDueInterest()));

        calculator.updateModelRepaymentPeriodsDuringReAmortizationWithEqualInterestSplit(model, LocalDate.of(2024, 2, 1));

        assertEquals(0.00, amount(model.repaymentPeriods().getFirst().getEmi()));
        assertEquals(0.50, amount(model.repaymentPeriods().get(1).getReAgedInterest()));
        assertEquals(0.50, amount(model.repaymentPeriods().get(2).getReAgedInterest()));
        assertEquals(100.00, amount(model.getTotalOutstandingPrincipal()));
        assertEquals(0.00, amount(model.getLastRepaymentPeriod().getOutstandingLoanBalance()));
    }

    @Test
    void reAgeReplacesFuturePeriodsAndCarriesUnpaidPrincipal() {
        when(details.getAnnualNominalInterestRate()).thenReturn(BigDecimal.ZERO);
        ScheduledDateGenerator dates = mock(ScheduledDateGenerator.class);
        calculator = new ProgressiveEMICalculator(dates);
        ProgressiveLoanInterestScheduleModel model = model(3);
        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));
        LoanApplicationTerms terms = mock(LoanApplicationTerms.class);
        when(terms.toLoanConfigurationDetails()).thenReturn(details);
        Money principal = money("100.00");
        when(terms.getPrincipal()).thenReturn(principal);
        when(terms.getInstallmentAmountInMultiplesOf()).thenReturn(null);
        LocalDate reAgeDate = LocalDate.of(2024, 2, 15);
        List<LoanScheduleModelRepaymentPeriod> futurePeriods = repaymentPeriods(reAgeDate, 2);
        when(dates.generateRepaymentPeriods(MC, reAgeDate, terms, null)).thenReturn(futurePeriods);

        calculator.updateModelRepaymentPeriodsDuringReAge(model, reAgeDate, reAgeDate.plusMonths(1), reAgeDate, terms, MC);

        assertEquals(LocalDate.of(2024, 4, 15), model.getMaturityDate());
        assertEquals(100.00, amount(model.getTotalOutstandingPrincipal()));
        assertEquals(50.00, amount(model.getLastRepaymentPeriod().getEmi()));
        assertEquals(0.00, amount(model.getLastRepaymentPeriod().getOutstandingLoanBalance()));
        assertEquals(0.00, amount(model.getTotalDueInterest()));
    }

    @ParameterizedTest
    @EnumSource(value = RecalculationFrequencyType.class, names = { "DAILY", "SAME_AS_REPAYMENT_PERIOD" })
    void overdueRecalculationMovesBalanceToConfiguredRestDate(RecalculationFrequencyType restFrequency) {
        when(details.getRestFrequencyType()).thenReturn(restFrequency);
        when(details.getPreCloseInterestCalculationStrategy()).thenReturn(LoanPreCloseInterestCalculationStrategy.TILL_REST_FREQUENCY_DATE);
        ProgressiveLoanInterestScheduleModel model = model(3);
        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));

        assertFalse(calculator.recalculateModelOverdueAmountsTillDate(model, LocalDate.of(2024, 1, 15), false));
        assertTrue(calculator.recalculateModelOverdueAmountsTillDate(model, LocalDate.of(2024, 2, 15), true));
        assertEquals(LocalDate.of(2024, 3, 1), model.lastOverdueBalanceChange());
        assertEquals(100.00, amount(model.getTotalOutstandingPrincipal()));
        assertFalse(calculator.recalculateModelOverdueAmountsTillDate(model, LocalDate.of(2024, 3, 1), true));
    }

    @ParameterizedTest
    @EnumSource(value = RecalculationFrequencyType.class, names = { "INVALID", "WEEKLY", "MONTHLY" })
    void overdueRecalculationRejectsUnsupportedRestFrequency(RecalculationFrequencyType restFrequency) {
        when(details.getRestFrequencyType()).thenReturn(restFrequency);
        ProgressiveLoanInterestScheduleModel model = model(3);
        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));
        assertThrows(IllegalStateException.class, () -> calculator.recalculateModelOverdueAmountsTillDate(model, LocalDate.of(2024, 2, 15), true));
    }

    @Test
    void installmentModelExcludesDownPaymentsAndAdditionalInstallments() {
        LoanRepaymentScheduleInstallment regular = mock(LoanRepaymentScheduleInstallment.class);
        when(regular.getFromDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(regular.getDueDate()).thenReturn(LocalDate.of(2024, 2, 1));
        LoanRepaymentScheduleInstallment downPayment = mock(LoanRepaymentScheduleInstallment.class);
        when(downPayment.isDownPayment()).thenReturn(true);
        LoanRepaymentScheduleInstallment additional = mock(LoanRepaymentScheduleInstallment.class);
        when(additional.isAdditional()).thenReturn(true);
        ProgressiveLoanInterestScheduleModel model = calculator
                .generateInstallmentInterestScheduleModel(List.of(regular, downPayment, additional), details, null, MC);
        assertEquals(1, model.repaymentPeriods().size());
        assertEquals(LocalDate.of(2024, 2, 1), model.getMaturityDate());
        assertEquals(0.00, amount(model.getTotalOutstandingPrincipal()));
    }

    @Test
    void interestPauseBeforeDisbursementHasZeroInterestAndPreservesPrincipal() {
        ProgressiveLoanInterestScheduleModel model = model(3);
        calculator.applyInterestPause(model, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 4, 1));
        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));
        assertEquals(0.00, amount(model.getTotalDueInterest()));
        assertEquals(0.00, amount(calculator.getOutstandingInterestTillDate(model, LocalDate.of(2024, 4, 1))));
        assertEquals(0.00, amount(calculator.getSumOfDueInterestsOnDate(model, LocalDate.of(2024, 4, 1))));
        assertEquals(100.00, amount(model.getTotalOutstandingPrincipal()));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5 })
    void emiRoundingConservesPrincipalAndAdjustsLastInstallment(int multiple) {
        when(details.getAnnualNominalInterestRate()).thenReturn(BigDecimal.ZERO);
        ProgressiveLoanInterestScheduleModel model = calculator.generatePeriodInterestScheduleModel(repaymentPeriods(LocalDate.of(2024, 1, 1), 3), details, multiple, MC);
        calculator.addDisbursement(model, LocalDate.of(2024, 1, 1), money("100.00"));
        assertEquals(multiple == 1 ? 33.00 : 35.00, amount(model.repaymentPeriods().getFirst().getEmi()));
        assertEquals(multiple == 1 ? 34.00 : 30.00, amount(model.getLastRepaymentPeriod().getEmi()));
        assertEquals(100.00, amount(model.getTotalDuePrincipal()));
        assertEquals(0.00, amount(model.getLastRepaymentPeriod().getOutstandingLoanBalance()));
    }

    private ProgressiveLoanInterestScheduleModel model(int count) {
        return calculator.generatePeriodInterestScheduleModel(repaymentPeriods(LocalDate.of(2024, 1, 1), count), details, null, MC);
    }

    private List<LoanScheduleModelRepaymentPeriod> repaymentPeriods(LocalDate start, int count) {
        List<LoanScheduleModelRepaymentPeriod> periods = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            periods.add(LoanScheduleModelRepaymentPeriod.repayment(i + 1, start.plusMonths(i), start.plusMonths(i + 1),
                    Money.zero(CURRENCY), Money.zero(CURRENCY), Money.zero(CURRENCY), Money.zero(CURRENCY), Money.zero(CURRENCY),
                    Money.zero(CURRENCY), false, MC));
        }
        return periods;
    }

    private static Money money(String value) {
        return Money.of(CURRENCY, new BigDecimal(value));
    }

    private static double amount(Money value) {
        return value.getAmount().doubleValue();
    }
}
