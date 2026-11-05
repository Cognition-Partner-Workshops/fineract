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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.apache.fineract.organisation.monetary.mapper.CurrencyMapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDays;
import org.apache.fineract.portfolio.common.domain.DayOfWeekType;
import org.apache.fineract.portfolio.common.domain.DaysInMonthType;
import org.apache.fineract.portfolio.common.domain.DaysInYearType;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanaccount.domain.LoanMoneyTestSupport;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanproduct.domain.AmortizationMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestCalculationPeriodMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.apache.fineract.portfolio.loanproduct.domain.LoanPreCloseInterestCalculationStrategy;
import org.apache.fineract.portfolio.loanproduct.domain.RepaymentStartDateType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CumulativeLoanScheduleTest extends LoanMoneyTestSupport {

    private final MathContext mc = new MathContext(12, RoundingMode.HALF_EVEN);
    private final LocalDate start = LocalDate.of(2023, 1, 1);
    private final HolidayDetailDTO holidays = new HolidayDetailDTO(false, List.of(),
            new WorkingDays("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR,SA,SU", 1, false, false));

    private LoanApplicationTerms terms(InterestMethod method, AmortizationMethod amortization, int principalGrace, int interestGrace,
            int freePeriods, InterestCalculationPeriodMethod calculation, DaysInMonthType month, DaysInYearType year, String principal,
            int repayments) {
        return LoanApplicationTerms.assembleFrom(currency.toData(), repayments, PeriodFrequencyType.MONTHS, repayments, 1,
                PeriodFrequencyType.MONTHS, null, DayOfWeekType.INVALID, amortization, method, BigDecimal.ONE, PeriodFrequencyType.MONTHS,
                new BigDecimal("12"), calculation, false, money(principal), start, null, start.plusMonths(1), principalGrace, 0,
                interestGrace, freePeriods, null, money("0"), false, null, List.of(), null, null, month, year, false, null, null, null,
                null, null, BigDecimal.ZERO, null, LoanPreCloseInterestCalculationStrategy.NONE, null, money(principal).getAmount(),
                new ArrayList<>(), true, 0, false, holidays, false, false, false, null, false, false, null, false,
                RepaymentStartDateType.DISBURSEMENT_DATE, start, LoanScheduleType.CUMULATIVE, LoanScheduleProcessingType.HORIZONTAL, null,
                false, null, null, false, null, false, null, null, null, false, null, null, null, false, false);
    }

    private LoanScheduleModel generate(LoanApplicationTerms terms) {
        var dates = new DefaultScheduledDateGenerator();
        var calculator = new DefaultPaymentPeriodsInOneYearCalculator();
        var transactions = mock(LoanTransactionRepository.class);
        var mapper = mock(CurrencyMapper.class);
        AbstractCumulativeLoanScheduleGenerator generator = terms.getInterestMethod() == InterestMethod.FLAT
                ? new CumulativeFlatInterestLoanScheduleGenerator(dates, calculator, transactions, mapper)
                : new CumulativeDecliningBalanceInterestLoanScheduleGenerator(dates, calculator, transactions, mapper);
        return generator.generate(mc, terms, new HashSet<>(), holidays);
    }

    private List<LoanScheduleModelPeriod> repayments(LoanScheduleModel model) {
        return model.getPeriods().stream().filter(LoanScheduleModelPeriod::isRepaymentPeriod).toList();
    }

    private void assertPrincipalAndDates(LoanScheduleModel model, String principal, int count) {
        var periods = repayments(model);
        assertEquals(count, periods.size());
        assertDecimal(principal, periods.stream().map(LoanScheduleModelPeriod::principalDue).reduce(BigDecimal.ZERO, BigDecimal::add));
        for (int i = 0; i < count; i++) {
            assertEquals(start.plusMonths(i), periods.get(i).periodFromDate());
            assertEquals(start.plusMonths(i + 1), periods.get(i).periodDueDate());
            assertTrue(periods.get(i).principalDue().signum() >= 0);
            assertTrue(periods.get(i).interestDue().signum() >= 0);
        }
        assertDecimal(model.getTotalInterestCharged().toPlainString(),
                periods.stream().map(LoanScheduleModelPeriod::interestDue).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @ParameterizedTest
    @CsvSource({ "FLAT,1200", "DECLINING_BALANCE,650" })
    void twelveMonthlyEqualPrincipalPaymentsHaveKnownInterest(InterestMethod method, String interest) {
        var terms = terms(method, AmortizationMethod.EQUAL_PRINCIPAL, 0, 0, 0, InterestCalculationPeriodMethod.SAME_AS_REPAYMENT_PERIOD,
                DaysInMonthType.DAYS_30, DaysInYearType.DAYS_360, "10000", 12);
        var model = generate(terms);
        assertPrincipalAndDates(model, "10000", 12);
        assertDecimal(interest, model.getTotalInterestCharged());
        var installments = repayments(model);
        assertDecimal("833.33", installments.getFirst().principalDue());
        assertDecimal("833.37", installments.getLast().principalDue());
        assertDecimal("100", installments.getFirst().interestDue());
        assertDecimal(method == InterestMethod.FLAT ? "100" : "8.33", installments.getLast().interestDue());
    }

    @ParameterizedTest
    @CsvSource({ "FLAT,0,0,0,1200", "FLAT,2,0,0,1200", "FLAT,0,2,0,1200", "FLAT,0,0,2,1000", "DECLINING_BALANCE,2,0,0,750",
            "DECLINING_BALANCE,0,2,0,650", "DECLINING_BALANCE,0,0,2,458.33" })
    void graceDefersPaymentsWhileFreePeriodsForgiveInterest(InterestMethod method, int principalGrace, int interestGrace, int freePeriods,
            String expectedInterest) {
        var terms = terms(method, AmortizationMethod.EQUAL_PRINCIPAL, principalGrace, interestGrace, freePeriods,
                InterestCalculationPeriodMethod.SAME_AS_REPAYMENT_PERIOD, DaysInMonthType.DAYS_30, DaysInYearType.DAYS_360, "10000", 12);
        var model = generate(terms);
        assertPrincipalAndDates(model, "10000", 12);
        assertDecimal(expectedInterest, model.getTotalInterestCharged());
        var periods = repayments(model);
        for (int i = 0; i < principalGrace; i++) {
            assertDecimal("0", periods.get(i).principalDue());
        }
        for (int i = 0; i < Math.max(interestGrace, freePeriods); i++) {
            assertDecimal("0", periods.get(i).interestDue());
        }
        if (interestGrace > 0) {
            assertTrue(periods.get(interestGrace).interestDue().compareTo(new BigDecimal("100")) > 0);
        }
    }

    @ParameterizedTest
    @CsvSource({ "ACTUAL,DAYS_365,365", "ACTUAL,DAYS_360,360", "DAYS_30,DAYS_360,360", "ACTUAL,ACTUAL,365" })
    void dailyDecliningInterestUsesConfiguredDayCount(DaysInMonthType month, DaysInYearType year, int denominator) {
        var model = generate(terms(InterestMethod.DECLINING_BALANCE, AmortizationMethod.EQUAL_PRINCIPAL, 0, 0, 0,
                InterestCalculationPeriodMethod.DAILY, month, year, "12000", 12));
        assertPrincipalAndDates(model, "12000", 12);
        BigDecimal balance = new BigDecimal("12000");
        for (var period : repayments(model)) {
            long days = month == DaysInMonthType.DAYS_30 ? 30 : ChronoUnit.DAYS.between(period.periodFromDate(), period.periodDueDate());
            BigDecimal expected = balance.multiply(new BigDecimal("0.12")).multiply(BigDecimal.valueOf(days))
                    .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_EVEN);
            assertDecimal(expected.toPlainString(), period.interestDue());
            balance = balance.subtract(period.principalDue());
        }
        assertDecimal("0", balance);
    }

    @ParameterizedTest
    @CsvSource({ "FLAT,933.33", "DECLINING_BALANCE,888.49" })
    void equalInstallmentsConservePrincipalAndUseKnownFirstPayment(InterestMethod method, String firstPayment) {
        var model = generate(terms(method, AmortizationMethod.EQUAL_INSTALLMENTS, 0, 0, 0,
                InterestCalculationPeriodMethod.SAME_AS_REPAYMENT_PERIOD, DaysInMonthType.DAYS_30, DaysInYearType.DAYS_360, "10000", 12));
        assertPrincipalAndDates(model, "10000", 12);
        var periods = repayments(model);
        assertDecimal(firstPayment, periods.getFirst().principalDue().add(periods.getFirst().interestDue()));
        BigDecimal first = periods.getFirst().principalDue().add(periods.getFirst().interestDue());
        for (var period : periods.subList(0, periods.size() - 1)) {
            assertDecimal(first.toPlainString(), period.principalDue().add(period.interestDue()));
        }
        assertTrue(periods.getLast().principalDue().add(periods.getLast().interestDue()).subtract(first).abs()
                .compareTo(new BigDecimal("0.10")) < 0);
    }
}
