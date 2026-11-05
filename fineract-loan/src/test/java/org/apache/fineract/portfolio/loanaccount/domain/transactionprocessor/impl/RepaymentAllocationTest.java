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
package org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanMoneyTestSupport;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.AbstractLoanRepaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.MoneyHolder;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.TransactionCtx;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanBalanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RepaymentAllocationTest extends LoanMoneyTestSupport {

    private AbstractLoanRepaymentScheduleTransactionProcessor processor(String strategy) {
        ExternalIdFactory ids = mock(ExternalIdFactory.class);
        LoanChargeValidator validator = mock(LoanChargeValidator.class);
        LoanBalanceService balances = mock(LoanBalanceService.class);
        return switch (strategy) {
            case "principal-advance" ->
                new DuePenFeeIntPriInAdvancePriPenFeeIntLoanRepaymentScheduleTransactionProcessor(ids, validator, balances);
            case "interest-principal-fee" ->
                new DuePenIntPriFeeInAdvancePenIntPriFeeLoanRepaymentScheduleTransactionProcessor(ids, validator, balances);
            default -> new FineractStyleLoanRepaymentScheduleTransactionProcessor(ids, validator, balances);
        };
    }

    static Stream<Arguments> dueAllocations() {
        return Stream.of("standard", "principal-advance", "interest-principal-fee")
                .flatMap(strategy -> Stream.of(-1, 0, 1).flatMap(dayOffset -> Stream.of("0", "3", "12", "30", "135", "150").map(amount -> {
                    boolean advance = dayOffset < 0 && !strategy.equals("standard");
                    String order = advance && strategy.equals("principal-advance") ? "principal,penalty,fee,interest"
                            : strategy.equals("interest-principal-fee") ? "penalty,interest,principal,fee"
                                    : "penalty,fee,interest,principal";
                    return Arguments.of(strategy, dayOffset, amount, order);
                })));
    }

    @ParameterizedTest
    @MethodSource("dueAllocations")
    void allocationsRespectStrategyAndConserveMoney(String strategy, int dayOffset, String amount, String order) {
        var processor = processor(strategy);
        LoanRepaymentScheduleInstallment installment = installment(1, dueDate.minusMonths(1), dueDate);
        LoanTransaction payment = LoanTransaction.repayment(null, money(amount), null, dueDate.plusDays(dayOffset), ExternalId.empty());
        var remaining = processor.handleRepaymentSchedule(List.of(payment), currency, List.of(installment), Set.of());
        BigDecimal available = money(amount).getAmount();
        Map<String, BigDecimal> expected = new HashMap<>();
        Map<String, BigDecimal> charged = Map.of("principal", money("100").getAmount(), "interest", money("20").getAmount(), "fee",
                money("10").getAmount(), "penalty", money("5").getAmount());
        for (String component : order.split(",")) {
            BigDecimal allocated = available.min(charged.get(component));
            expected.put(component, allocated);
            available = available.subtract(allocated);
        }
        assertAmount(available.toPlainString(), remaining);
        assertAmount(expected.get("principal").toPlainString(), payment.getPrincipalPortion(currency));
        assertAmount(expected.get("interest").toPlainString(), payment.getInterestPortion(currency));
        assertAmount(expected.get("fee").toPlainString(), payment.getFeeChargesPortion(currency));
        assertAmount(expected.get("penalty").toPlainString(), payment.getPenaltyChargesPortion(currency));
        assertAmount(charged.get("principal").subtract(expected.get("principal")).toPlainString(),
                installment.getPrincipalOutstanding(currency));
        assertAmount(charged.get("interest").subtract(expected.get("interest")).toPlainString(),
                installment.getInterestOutstanding(currency));
        assertAmount(charged.get("fee").subtract(expected.get("fee")).toPlainString(), installment.getFeeChargesOutstanding(currency));
        assertAmount(charged.get("penalty").subtract(expected.get("penalty")).toPlainString(),
                installment.getPenaltyChargesOutstanding(currency));
        assertEquals(money(amount).isGreaterThanOrEqualTo(money("135")), !installment.isNotFullyPaidOff());
    }

    @Test
    void standardPaymentSpansInstallmentsAndLeavesExcessUnallocated() {
        var processor = processor("standard");
        var first = installment(1, dueDate.minusMonths(1), dueDate);
        var second = installment(2, dueDate, dueDate.plusMonths(1));
        var payment = LoanTransaction.repayment(null, money("200"), null, dueDate, ExternalId.empty());
        assertAmount("0", processor.handleRepaymentSchedule(List.of(payment), currency, List.of(first, second), Set.of()));
        assertAmount("130", payment.getPrincipalPortion(currency));
        assertAmount("40", payment.getInterestPortion(currency));
        assertAmount("20", payment.getFeeChargesPortion(currency));
        assertAmount("10", payment.getPenaltyChargesPortion(currency));
        assertAmount("0", first.getTotalOutstanding(currency));
        assertAmount("70", second.getTotalOutstanding(currency));
        var excess = LoanTransaction.repayment(null, money("80"), null, dueDate.plusDays(1), ExternalId.empty());
        assertAmount("10", processor.handleRepaymentSchedule(List.of(excess), currency, List.of(first, second), Set.of()));
        assertAmount("70", excess.getPrincipalPortion(currency));
        assertAmount("0", second.getTotalOutstanding(currency));
        assertFalse(second.isNotFullyPaidOff());
    }

    @Test
    void fractionalCentPaymentUsesHalfEvenCurrencyRounding() {
        var processor = processor("standard");
        var installment = installment(1, dueDate.minusMonths(1), dueDate);
        var payment = LoanTransaction.repayment(null, money("15.005"), null, dueDate, ExternalId.empty());
        assertAmount("0", processor.handleRepaymentSchedule(List.of(payment), currency, List.of(installment), Set.of()));
        assertAmount("5", payment.getPenaltyChargesPortion(currency));
        assertAmount("10", payment.getFeeChargesPortion(currency));
        assertAmount("0", payment.getInterestPortion(currency));
        assertAmount("120", installment.getTotalOutstanding(currency));
    }

    @Test
    void latestTransactionTracksOverpaymentAndAllocatesAllComponents() {
        var processor = processor("principal-advance");
        var installment = installment(1, dueDate.minusMonths(1), dueDate);
        MoneyHolder overpayment = new MoneyHolder(money("0"));
        TransactionCtx context = new TransactionCtx(currency, List.of(installment), Set.of(), overpayment, null);
        var payment = LoanTransaction.repayment(null, money("150"), null, dueDate, ExternalId.empty());
        processor.processLatestTransaction(payment, context);
        assertAmount("15", payment.getOverPaymentPortion(currency));
        assertAmount("15", overpayment.getMoneyObject());
        assertAmount("100", payment.getPrincipalPortion(currency));
        assertAmount("20", payment.getInterestPortion(currency));
        assertAmount("10", payment.getFeeChargesPortion(currency));
        assertAmount("5", payment.getPenaltyChargesPortion(currency));
        assertAmount("0", installment.getTotalOutstanding(currency));
    }

    @Test
    void latestRefundRestoresPaidComponentsAndOutstanding() {
        var processor = processor("principal-advance");
        var installment = installment(1, dueDate.minusMonths(1), dueDate);
        var payment = LoanTransaction.repayment(null, money("135"), null, dueDate, ExternalId.empty());
        processor.handleRepaymentSchedule(List.of(payment), currency, List.of(installment), Set.of());
        LoanCharge paidFee = new LoanCharge();
        paidFee.setChargeTime(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        paidFee.setDueDate(dueDate);
        paidFee.setAmount(new BigDecimal("10"));
        paidFee.setAmountPaid(new BigDecimal("10"));
        paidFee.setAmountOutstanding(BigDecimal.ZERO);
        paidFee.setAmountWaived(BigDecimal.ZERO);
        paidFee.setAmountWrittenOff(BigDecimal.ZERO);
        LoanTransaction refund = LoanTransaction.refundForActiveLoan(null, money("130"), null, dueDate, ExternalId.empty());
        processor.processLatestTransaction(refund,
                new TransactionCtx(currency, new ArrayList<>(List.of(installment)), Set.of(paidFee), new MoneyHolder(money("0")), null));
        assertAmount("100", refund.getPrincipalPortion(currency));
        assertAmount("20", refund.getInterestPortion(currency));
        assertAmount("10", refund.getFeeChargesPortion(currency));
        assertAmount("0", refund.getPenaltyChargesPortion(currency));
        assertAmount("130", installment.getTotalOutstanding(currency));
        assertAmount("10", paidFee.getAmountOutstanding(currency));
        assertAmount("0", paidFee.getAmountPaid(currency));
    }

    @Test
    void interestWaiverDoesNotPayPrincipalOrCharges() {
        var processor = processor("principal-advance");
        var installment = installment(1, dueDate.minusMonths(1), dueDate);
        LoanTransaction waiver = LoanTransaction.waiver(null, null, money("12"), dueDate, money("12"), money("0"), ExternalId.empty());
        processor.processLatestTransaction(waiver,
                new TransactionCtx(currency, List.of(installment), Set.of(), new MoneyHolder(money("0")), null));
        assertAmount("12", waiver.getInterestPortion(currency));
        assertAmount("0", waiver.getPrincipalPortion(currency));
        assertAmount("0", waiver.getFeeChargesPortion(currency));
        assertAmount("0", waiver.getPenaltyChargesPortion(currency));
        assertAmount("8", installment.getInterestOutstanding(currency));
        assertAmount("123", installment.getTotalOutstanding(currency));
    }

    @Test
    void emptyScheduleReturnsPaymentWithoutAllocatingAndStrategyMatchesOnlyItsCode() {
        var processor = processor("standard");
        var payment = LoanTransaction.repayment(null, money("10"), null, dueDate, ExternalId.empty());
        assertAmount("10", processor.handleRepaymentSchedule(List.of(payment), currency, List.of(), Set.of()));
        assertAmount("0", payment.getPrincipalPortion(currency));
        assertTrue(processor.accept(FineractStyleLoanRepaymentScheduleTransactionProcessor.STRATEGY_CODE));
        assertFalse(processor.accept("unknown"));
        assertAmount("0", processor.handleRepaymentSchedule(List.of(), currency, List.of(), Set.of()));
    }
}
