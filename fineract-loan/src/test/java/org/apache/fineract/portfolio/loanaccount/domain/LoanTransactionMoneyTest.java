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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;

class LoanTransactionMoneyTest extends LoanMoneyTestSupport {

    private final ExternalId externalId = new ExternalId("loan-transaction-test");

    Stream<Arguments> factories() {
        return Stream.of(
                Arguments.of(LoanTransactionType.REPAYMENT, LoanTransaction.repayment(null, money("123.45"), null, dueDate, externalId)),
                Arguments.of(LoanTransactionType.RECOVERY_REPAYMENT,
                        LoanTransaction.recoveryRepayment(null, money("123.45"), null, dueDate, externalId)),
                Arguments.of(LoanTransactionType.REPAYMENT_AT_DISBURSEMENT,
                        LoanTransaction.repaymentAtDisbursement(null, money("123.45"), null, dueDate, externalId)),
                Arguments.of(LoanTransactionType.DOWN_PAYMENT,
                        LoanTransaction.downPayment(null, money("123.45"), null, dueDate, externalId)),
                Arguments.of(LoanTransactionType.REFUND, LoanTransaction.refund(null, money("123.45"), null, dueDate, externalId)),
                Arguments.of(LoanTransactionType.REFUND_FOR_ACTIVE_LOAN,
                        LoanTransaction.refundForActiveLoan(null, money("123.45"), null, dueDate, externalId)));
    }

    @Test
    void paymentFactoriesPreserveAmountDateAndExternalIdAndCanBeReversed() {
        factories().forEach(arguments -> {
            LoanTransactionType expectedType = (LoanTransactionType) arguments.get()[0];
            LoanTransaction transaction = (LoanTransaction) arguments.get()[1];
            assertEquals(expectedType, transaction.getTypeOf());
            assertAmount("123.45", transaction.getAmount(currency));
            assertEquals(dueDate, transaction.getTransactionDate());
            assertEquals(externalId, transaction.getExternalId());
            assertFalse(transaction.isReversed());
            transaction.reverse(externalId);
            assertTrue(transaction.isReversed());
            assertFalse(transaction.isRepaymentLikeType());
            assertEquals(dueDate, transaction.getReversedOnDate());
            assertEquals(externalId, transaction.getReversalExternalId());
            assertAmount("123.45", transaction.getAmount(currency));
        });
    }

    @Test
    void componentsAccumulateCopyIndependentlyAndResetWithoutChangingAmount() {
        var transaction = LoanTransaction.repayment(null, money("100"), null, dueDate, externalId);
        transaction.updateComponents(money("60"), money("20"), money("10"), money("5"));
        transaction.updateComponents(money("2"), money("1"), money("1"), money("1"));
        transaction.setOverPayments(money("3"));
        assertAmount("62", transaction.getPrincipalPortion(currency));
        assertAmount("21", transaction.getInterestPortion(currency));
        assertAmount("11", transaction.getFeeChargesPortion(currency));
        assertAmount("6", transaction.getPenaltyChargesPortion(currency));
        assertAmount("3", transaction.getOverPaymentPortion(currency));
        var copy = LoanTransaction.copyTransactionProperties(transaction);
        assertNotSame(transaction, copy);
        assertTrue(LoanTransaction.transactionAmountsMatch(currency, transaction, copy));
        copy.resetDerivedComponents();
        assertFalse(LoanTransaction.transactionAmountsMatch(currency, transaction, copy));
        assertAmount("0", copy.getPrincipalPortion(currency));
        assertAmount("0", copy.getInterestPortion(currency));
        assertAmount("0", copy.getFeeChargesPortion(currency));
        assertAmount("0", copy.getPenaltyChargesPortion(currency));
        assertAmount("0", copy.getOverPaymentPortion(currency));
        assertAmount("100", copy.getAmount(currency));
        assertAmount("62", transaction.getPrincipalPortion(currency));
    }

    @Test
    void updateComponentsAndTotalAndWaiversTrackUnrecognizedIncome() {
        var transaction = LoanTransaction.writeoff(null, null, dueDate, externalId);
        transaction.updateComponentsAndTotal(money("100"), money("20"), money("5"), money("2"));
        assertAmount("127", transaction.getAmount(currency));
        var waiver = LoanTransaction.waiver(null, null, money("20"), dueDate, money("20"), money("3"), externalId);
        assertTrue(waiver.isInterestWaiver());
        waiver.adjustInterestComponent();
        assertAmount("17", waiver.getInterestPortion(currency));
        var charges = LoanTransaction.waiveLoanCharge(null, null, money("7"), dueDate, money("5"), money("2"), money("1"), externalId);
        assertTrue(charges.isChargesWaiver());
        assertAmount("5", charges.getFeeChargesPortion(currency));
        assertAmount("2", charges.getPenaltyChargesPortion(currency));
        assertAmount("7", charges.getAmount(currency));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "50", "150" })
    void progressiveDisbursementOverpaymentIsCappedAtDisbursedAmount(String overpaid) {
        Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        when(loan.getLoanProductRelatedDetail().getLoanScheduleType()).thenReturn(LoanScheduleType.PROGRESSIVE);
        var transaction = LoanTransaction.disbursement(loan, money("100"), null, dueDate, externalId, money(overpaid));
        assertTrue(transaction.isDisbursement());
        assertAmount("100", transaction.getAmount(currency));
        assertDecimal(new BigDecimal(overpaid).min(new BigDecimal("100")).toPlainString(),
                transaction.getOverPaymentPortion(currency).getAmount());
    }

    @Test
    void terminalAndTransferFactoriesSnapshotOutstandingComponents() {
        Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        when(loan.getSummary().getTotalPrincipalOutstanding()).thenReturn(new BigDecimal("100"));
        when(loan.getSummary().getTotalInterestOutstanding()).thenReturn(new BigDecimal("20"));
        when(loan.getSummary().getTotalFeeChargesOutstanding()).thenReturn(new BigDecimal("5"));
        when(loan.getSummary().getTotalPenaltyChargesOutstanding()).thenReturn(new BigDecimal("2"));
        when(loan.getSummary().getTotalOutstanding()).thenReturn(new BigDecimal("127"));
        Stream.of(LoanTransaction.chargeOff(loan, dueDate, externalId), LoanTransaction.contractTermination(loan, dueDate, externalId),
                LoanTransaction.initiateTransfer(null, loan, dueDate, externalId),
                LoanTransaction.approveTransfer(null, loan, dueDate, externalId),
                LoanTransaction.withdrawTransfer(null, loan, dueDate, externalId)).forEach(transaction -> {
                    assertAmount("100", transaction.getPrincipalPortion(currency));
                    assertAmount("20", transaction.getInterestPortion(currency));
                    assertAmount("5", transaction.getFeeChargesPortion(currency));
                    assertAmount("2", transaction.getPenaltyChargesPortion(currency));
                    assertAmount("127", transaction.getAmount(currency));
                    assertEquals(dueDate, transaction.getTransactionDate());
                });
    }
}
