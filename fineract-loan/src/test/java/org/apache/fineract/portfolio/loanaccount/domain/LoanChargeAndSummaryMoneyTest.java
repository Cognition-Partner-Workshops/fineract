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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.Test;

class LoanChargeAndSummaryMoneyTest extends LoanMoneyTestSupport {

    private LoanCharge charge(String amount) {
        LoanCharge charge = new LoanCharge();
        charge.setAmount(new BigDecimal(amount));
        charge.setAmountOrPercentage(new BigDecimal(amount));
        charge.setAmountOutstanding(new BigDecimal(amount));
        charge.setAmountPaid(BigDecimal.ZERO);
        charge.setAmountWaived(BigDecimal.ZERO);
        charge.setAmountWrittenOff(BigDecimal.ZERO);
        charge.setChargeCalculation(ChargeCalculationType.FLAT.getValue());
        charge.setChargeTime(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        charge.setActive(true);
        return charge;
    }

    @Test
    void percentageAndCapsUseMoneyPrecisionAndBounds() {
        LoanCharge charge = charge("20");
        charge.setPercentage(new BigDecimal("2.5"));
        assertDecimal("25", charge.percentageOf(new BigDecimal("1000")));
        assertDecimal("0", charge.percentageOf(BigDecimal.ZERO));
        assertDecimal("0", LoanCharge.percentageOf(new BigDecimal("-10"), new BigDecimal("25")));
        charge.setMinCap(new BigDecimal("30"));
        charge.setMaxCap(new BigDecimal("100"));
        assertDecimal("30", charge.minimumAndMaximumCap(new BigDecimal("25")));
        assertDecimal("100", charge.minimumAndMaximumCap(new BigDecimal("101")));
        assertDecimal("50", charge.minimumAndMaximumCap(new BigDecimal("50")));
    }

    @Test
    void flatChargePartialPaymentWaiverUndoAndRefundPreserveOutstanding() {
        LoanCharge charge = charge("100");
        assertAmount("40", charge.updatePaidAmountBy(money("40"), null, money("0")));
        assertAmount("40", charge.getAmountPaid(currency));
        assertAmount("60", charge.getAmountOutstanding(currency));
        assertFalse(charge.isFullyPaid());
        assertAmount("60", charge.waive(currency, null));
        assertAmount("0", charge.getAmountOutstanding(currency));
        assertTrue(charge.isWaived());
        charge.undoWaive(currency, null);
        assertAmount("60", charge.getAmountOutstanding(currency));
        assertAmount("40", charge.undoPaidOrPartiallyAmountBy(money("40"), null, money("0")));
        assertAmount("100", charge.getAmountOutstanding(currency));
        charge.markAsFullyPaid();
        assertTrue(charge.isFullyPaid());
        charge.resetPaidAmount(currency);
        assertAmount("100", charge.getAmountOutstanding(currency));
        assertFalse(charge.isFullyPaid());
    }

    @Test
    void installmentChargePartialPaymentAndWaive() {
        LoanCharge charge = charge("100");
        charge.setChargeTime(ChargeTimeType.INSTALMENT_FEE.getValue());
        LoanRepaymentScheduleInstallment installment = installment(1, dueDate.minusMonths(1), dueDate);
        LoanInstallmentCharge perInstallment = new LoanInstallmentCharge(new BigDecimal("60"), charge, installment);
        charge.setInstallmentLoanCharge(perInstallment, 1);
        assertAmount("25", charge.updatePaidAmountBy(money("25"), 1, money("0")));
        assertAmount("35", perInstallment.getAmountOutstanding(currency));
        assertAmount("25", charge.getAmountPaid(currency));
        assertAmount("75", charge.getAmountOutstanding(currency));
        assertAmount("35", charge.waive(currency, 1));
        assertTrue(perInstallment.isWaived());
        charge.undoWaive(currency, 1);
        assertFalse(perInstallment.isWaived());
        assertAmount("35", perInstallment.getAmountOutstanding(currency));
        assertAmount("35", charge.updatePaidAmountBy(money("100"), 1, money("0")));
        assertAmount("40", charge.getAmountOutstanding(currency));
        assertFalse(charge.isFullyPaid());
    }

    @Test
    void summarySumsPrincipalInterestAndChargeComponents() {
        LoanRepaymentScheduleInstallment first = installment(1, dueDate.minusMonths(1), dueDate);
        LoanRepaymentScheduleInstallment second = installment(2, dueDate, dueDate.plusMonths(1));
        first.payPrincipalComponent(dueDate, money("30"));
        first.payInterestComponent(dueDate, money("5"));
        first.payFeeChargesComponent(dueDate, money("3"));
        first.payPenaltyChargesComponent(dueDate, money("2"));
        LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);
        summary.updateSummary(currency, money("200"), List.of(first, second), Set.of(), Money.zero(currency), Money.zero(currency));
        assertDecimal("200", summary.getTotalPrincipalDisbursed());
        assertDecimal("200", summary.getTotalPrincipal());
        assertDecimal("170", summary.getTotalPrincipalOutstanding());
        assertDecimal("40", summary.getTotalInterestCharged());
        assertDecimal("5", summary.getTotalInterestRepaid());
        assertDecimal("20", summary.getTotalFeeChargesCharged());
        assertDecimal("3", summary.getTotalFeeChargesRepaid());
        assertDecimal("10", summary.getTotalPenaltyChargesCharged());
        assertDecimal("2", summary.getTotalPenaltyChargesRepaid());
        assertDecimal("270", summary.getTotalExpectedRepayment());
        assertDecimal("40", summary.getTotalRepayment());
        assertDecimal("230", summary.getTotalOutstanding());
        assertFalse(summary.isRepaidInFull(currency));
        summary.updateTotalOutstanding(BigDecimal.ZERO);
        assertTrue(summary.isRepaidInFull(currency));
    }

    @Test
    void summaryZeroFieldsKeepsDisbursementFeeAndClearsDerivedAmounts() {
        LoanSummary summary = LoanSummary.create(new BigDecimal("7"));
        summary.updateTotalOutstanding(new BigDecimal("10"));
        summary.updateTotalWaived(new BigDecimal("3"));
        summary.zeroFields();
        assertDecimal("0", summary.getTotalOutstanding());
        assertDecimal("0", summary.getTotalWaived());
        assertDecimal("7", summary.getTotalFeeChargesDueAtDisbursement());
    }
}
