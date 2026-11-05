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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.common.domain.DaysInMonthType;
import org.apache.fineract.portfolio.common.domain.DaysInYearType;
import org.apache.fineract.portfolio.loanaccount.data.OutstandingAmountsDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.LoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.AdvancedPaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.service.InterestScheduleModelRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.apache.fineract.portfolio.loanproduct.calc.EMICalculator;
import org.apache.fineract.portfolio.loanproduct.calc.ProgressiveEMICalculator;
import org.apache.fineract.portfolio.loanproduct.calc.data.OutstandingDetails;
import org.apache.fineract.portfolio.loanproduct.calc.data.ProgressiveLoanInterestScheduleModel;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.apache.fineract.portfolio.loanproduct.domain.LoanPreCloseInterestCalculationStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ProgressiveLoanScheduleGeneratorTest {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_EVEN);
    private static final MonetaryCurrency CURRENCY = new MonetaryCurrency("USD", 2, 1);
    private static final LocalDate ON_DATE = LocalDate.of(2024, 1, 15);
    private static final LocalDate DUE_DATE = LocalDate.of(2024, 2, 1);
    private EMICalculator calculator;
    private InterestScheduleModelRepositoryWrapper repository;
    private LoanTransactionProcessingService processingService;
    private ProgressiveLoanScheduleGenerator generator;
    private MockedStatic<MoneyHelper> moneyHelper;

    @BeforeEach
    void setUp() {
        moneyHelper = Mockito.mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(MC);
        calculator = mock(EMICalculator.class);
        repository = mock(InterestScheduleModelRepositoryWrapper.class);
        processingService = mock(LoanTransactionProcessingService.class);
        generator = new ProgressiveLoanScheduleGenerator(mock(ScheduledDateGenerator.class), calculator, repository);
        generator.setLoanTransactionProcessingService(processingService);
    }

    @AfterEach
    void tearDown() {
        moneyHelper.close();
    }

    @ParameterizedTest
    @EnumSource(value = LoanPreCloseInterestCalculationStrategy.class, names = { "TILL_PRE_CLOSURE_DATE", "TILL_REST_FREQUENCY_DATE" })
    void prepaymentIncludesPrincipalInterestAdditionalInstallmentsAndUnpaidCharges(LoanPreCloseInterestCalculationStrategy strategy) {
        Loan loan = mock(Loan.class);
        LoanApplicationTerms terms = mock(LoanApplicationTerms.class);
        LoanRepaymentScheduleInstallment installment = installment();
        LoanRepaymentScheduleInstallment additional = installment();
        when(additional.isAdditional()).thenReturn(true);
        when(additional.getPrincipalOutstanding(CURRENCY)).thenReturn(money("3.00"));
        when(additional.getInterestOutstanding(CURRENCY)).thenReturn(money("0.25"));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(installment, additional));
        when(loan.isInterestRecalculationEnabled()).thenReturn(true);
        when(terms.getPreClosureInterestCalculationStrategy()).thenReturn(strategy);
        LocalDate valuationDate = strategy == LoanPreCloseInterestCalculationStrategy.TILL_PRE_CLOSURE_DATE ? ON_DATE : DUE_DATE;
        ProgressiveLoanInterestScheduleModel model = mock(ProgressiveLoanInterestScheduleModel.class);
        when(repository.getSavedModel(loan, valuationDate)).thenReturn(Optional.of(model));
        when(calculator.getOutstandingAmountsTillDate(model, valuationDate))
                .thenReturn(new OutstandingDetails(money("80.00"), money("1.50")));

        OutstandingAmountsDTO result = generator.calculatePrepaymentAmount(CURRENCY, ON_DATE, terms, MC, loan, null,
                mock(AdvancedPaymentScheduleTransactionProcessor.class));

        assertEquals(new BigDecimal("83.00"), result.principal().getAmount());
        assertEquals(new BigDecimal("1.75"), result.interest().getAmount());
        assertEquals(new BigDecimal("4.00"), result.feeCharges().getAmount());
        assertEquals(new BigDecimal("1.00"), result.penaltyCharges().getAmount());
        assertEquals(new BigDecimal("89.75"), result.getTotalOutstanding().getAmount());
        verify(repository).getSavedModel(loan, valuationDate);
    }

    @Test
    void periodInterestUsesSavedModelAndExcludesCreditedInterest() {
        Loan loan = mock(Loan.class);
        LoanRepaymentScheduleInstallment installment = installment();
        when(installment.getLoan()).thenReturn(loan);
        when(loan.getTransactionProcessingStrategyCode()).thenReturn("advanced-payment-allocation-strategy");
        when(processingService.getTransactionProcessor("advanced-payment-allocation-strategy"))
                .thenReturn(mock(AdvancedPaymentScheduleTransactionProcessor.class));
        ProgressiveLoanInterestScheduleModel model = mock(ProgressiveLoanInterestScheduleModel.class);
        when(repository.getSavedModel(loan, ON_DATE)).thenReturn(Optional.of(model));
        when(calculator.getPeriodInterestTillDate(model, LocalDate.of(2024, 1, 1), DUE_DATE, ON_DATE, false)).thenReturn(money("0.45"));

        assertEquals(new BigDecimal("0.45"), generator.getPeriodInterestTillDate(installment, ON_DATE).getAmount());
        when(installment.isDownPayment()).thenReturn(true);
        when(loan.getCurrency()).thenReturn(CURRENCY);
        assertEquals(new BigDecimal("0.00"), generator.getPeriodInterestTillDate(installment, ON_DATE).getAmount());
    }

    @Test
    void rejectsUnsupportedProcessorAndUnavailableSavedModel() {
        Loan loan = mock(Loan.class);
        LoanApplicationTerms terms = mock(LoanApplicationTerms.class);
        assertThrows(IllegalStateException.class, () -> generator.calculatePrepaymentAmount(CURRENCY, ON_DATE, terms, MC, loan, null,
                mock(LoanRepaymentScheduleTransactionProcessor.class)));
        LoanRepaymentScheduleInstallment installment = installment();
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(installment));
        when(terms.getPreClosureInterestCalculationStrategy()).thenReturn(LoanPreCloseInterestCalculationStrategy.TILL_PRE_CLOSURE_DATE);
        when(repository.getSavedModel(loan, ON_DATE)).thenReturn(Optional.empty());
        assertThrows(java.util.NoSuchElementException.class, () -> generator.calculatePrepaymentAmount(CURRENCY, ON_DATE, terms, MC, loan,
                null, mock(AdvancedPaymentScheduleTransactionProcessor.class)));
    }

    @ParameterizedTest
    @EnumSource(value = ChargeCalculationType.class, names = { "FLAT", "PERCENT_OF_AMOUNT", "PERCENT_OF_INTEREST",
            "PERCENT_OF_AMOUNT_AND_INTEREST" })
    void generatesFlatAndPercentageFeesAndPenalties(ChargeCalculationType calculation) {
        for (boolean installmentCharge : List.of(false, true)) {
            for (boolean penalty : List.of(false, true)) {
                LoanCharge charge = mock(LoanCharge.class);
                when(charge.isFeeCharge()).thenReturn(!penalty);
                when(charge.isPenaltyCharge()).thenReturn(penalty);
                when(charge.isInstalmentFee()).thenReturn(installmentCharge);
                when(charge.isSpecifiedDueDate()).thenReturn(!installmentCharge);
                when(charge.getChargeCalculation()).thenReturn(calculation);
                when(charge.getPercentage()).thenReturn(new BigDecimal("2.00"));
                when(charge.amount()).thenReturn(new BigDecimal("5.00"));
                when(charge.amountOrPercentage()).thenReturn(new BigDecimal("5.00"));
                when(charge.isDueInPeriod(Mockito.any(), Mockito.any(), Mockito.anyBoolean())).thenAnswer(invocation -> {
                    LocalDate from = invocation.getArgument(0);
                    LocalDate to = invocation.getArgument(1);
                    return DUE_DATE.isAfter(from) && !DUE_DATE.isAfter(to);
                });
                LoanRepaymentScheduleModelData data = new LoanRepaymentScheduleModelData(LocalDate.of(2024, 1, 1),
                        new CurrencyData("USD", "USD", 2, 1, "$", "USD"), new BigDecimal("100.00"), LocalDate.of(2024, 1, 1), 2, 1,
                        "MONTHS", new BigDecimal("12.00"), false, DaysInMonthType.DAYS_30, DaysInYearType.DAYS_360, null, null, null, false,
                        null, InterestMethod.DECLINING_BALANCE, true, false);
                ProgressiveLoanScheduleGenerator actualGenerator = new ProgressiveLoanScheduleGenerator(new DefaultScheduledDateGenerator(),
                        new ProgressiveEMICalculator(new DefaultScheduledDateGenerator()), repository);
                LoanScheduleModel schedule = actualGenerator.generate(MC, LoanApplicationTerms.assembleFrom(data, MC),
                        new HashSet<>(Set.of(charge)), null);
                BigDecimal expectedCharge = switch (calculation) {
                    case FLAT -> new BigDecimal(installmentCharge ? "10.00" : "5.00");
                    case PERCENT_OF_AMOUNT -> new BigDecimal("2.00");
                    case PERCENT_OF_INTEREST -> new BigDecimal("0.03");
                    case PERCENT_OF_AMOUNT_AND_INTEREST -> new BigDecimal(installmentCharge ? "2.04" : "2.03");
                    default -> throw new IllegalArgumentException(calculation.name());
                };
                assertEquals(new BigDecimal("100.00"), schedule.getTotalPrincipalExpected());
                assertEquals(new BigDecimal("1.50"), schedule.getTotalInterestCharged());
                assertEquals(expectedCharge, penalty ? schedule.getTotalPenaltyChargesCharged() : schedule.getTotalFeeChargesCharged());
                assertEquals(new BigDecimal("0.00"),
                        penalty ? schedule.getTotalFeeChargesCharged() : schedule.getTotalPenaltyChargesCharged());
            }
        }
    }

    private LoanRepaymentScheduleInstallment installment() {
        LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(installment.getFromDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(installment.getDueDate()).thenReturn(DUE_DATE);
        when(installment.getFeeChargesOutstanding(CURRENCY)).thenReturn(money("2.00"));
        when(installment.getPenaltyChargesOutstanding(CURRENCY)).thenReturn(money("0.50"));
        return installment;
    }

    private Money money(String value) {
        return Money.of(CURRENCY, new BigDecimal(value), MC);
    }
}
