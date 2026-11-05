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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.service.LoanBalanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultLoanLifecycleStateMachineTest {

    private final BusinessEventNotifierService notifier = mock(BusinessEventNotifierService.class);
    private final LoanBalanceService balances = mock(LoanBalanceService.class);
    private final DefaultLoanLifecycleStateMachine machine = new DefaultLoanLifecycleStateMachine(notifier, balances);

    static Stream<Arguments> transitions() {
        return Stream.of(LoanStatus.values())
                .flatMap(status -> Stream.of(LoanEvent.values()).map(event -> Arguments.of(status, event, expected(status, event))));
    }

    private static LoanStatus expected(LoanStatus from, LoanEvent event) {
        return switch (event) {
            case LOAN_REJECTED -> from == LoanStatus.SUBMITTED_AND_PENDING_APPROVAL ? LoanStatus.REJECTED : from;
            case LOAN_APPROVED -> from == LoanStatus.SUBMITTED_AND_PENDING_APPROVAL ? LoanStatus.APPROVED : from;
            case LOAN_WITHDRAWN -> from == LoanStatus.SUBMITTED_AND_PENDING_APPROVAL ? LoanStatus.WITHDRAWN_BY_CLIENT : from;
            case LOAN_DISBURSED ->
                Set.of(LoanStatus.APPROVED, LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.OVERPAID).contains(from) ? LoanStatus.ACTIVE
                        : from;
            case LOAN_APPROVAL_UNDO -> from == LoanStatus.APPROVED ? LoanStatus.SUBMITTED_AND_PENDING_APPROVAL : from;
            case LOAN_DISBURSAL_UNDO -> from == LoanStatus.ACTIVE ? LoanStatus.APPROVED : from;
            case LOAN_CHARGE_PAYMENT, LOAN_REPAYMENT_OR_WAIVER, LOAN_CHARGEBACK ->
                Set.of(LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.OVERPAID).contains(from) ? LoanStatus.ACTIVE : from;
            case REPAID_IN_FULL -> Set.of(LoanStatus.ACTIVE, LoanStatus.OVERPAID).contains(from) ? LoanStatus.CLOSED_OBLIGATIONS_MET : from;
            case WRITE_OFF_OUTSTANDING -> from == LoanStatus.ACTIVE ? LoanStatus.CLOSED_WRITTEN_OFF : from;
            case LOAN_RESCHEDULE -> from == LoanStatus.ACTIVE ? LoanStatus.CLOSED_RESCHEDULE_OUTSTANDING_AMOUNT : from;
            case LOAN_OVERPAYMENT ->
                Set.of(LoanStatus.ACTIVE, LoanStatus.CLOSED_OBLIGATIONS_MET).contains(from) ? LoanStatus.OVERPAID : from;
            case LOAN_ADJUST_TRANSACTION ->
                Set.of(LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.CLOSED_WRITTEN_OFF, LoanStatus.CLOSED_RESCHEDULE_OUTSTANDING_AMOUNT)
                        .contains(from) ? LoanStatus.ACTIVE : from;
            case LOAN_INITIATE_TRANSFER -> LoanStatus.TRANSFER_IN_PROGRESS;
            case LOAN_REJECT_TRANSFER -> from == LoanStatus.TRANSFER_IN_PROGRESS ? LoanStatus.TRANSFER_ON_HOLD : from;
            case LOAN_WITHDRAW_TRANSFER -> from == LoanStatus.TRANSFER_IN_PROGRESS ? LoanStatus.ACTIVE : from;
            case WRITE_OFF_OUTSTANDING_UNDO -> from == LoanStatus.CLOSED_WRITTEN_OFF ? LoanStatus.ACTIVE : from;
            case LOAN_CREDIT_BALANCE_REFUND -> from == LoanStatus.OVERPAID ? LoanStatus.CLOSED_OBLIGATIONS_MET : from;
            case LOAN_CHARGE_ADDED -> from == LoanStatus.CLOSED_OBLIGATIONS_MET ? LoanStatus.ACTIVE : from;
            case LOAN_CHARGE_ADJUSTMENT -> from == LoanStatus.CLOSED_OBLIGATIONS_MET ? LoanStatus.OVERPAID : from;
            default -> from;
        };
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void eventStatusMatrix(LoanStatus from, LoanEvent event, LoanStatus expected) {
        Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        when(loan.getStatus()).thenReturn(from);
        when(loan.getTotalOverpaid()).thenReturn(BigDecimal.ZERO);
        when(loan.getCurrency()).thenReturn(mock(MonetaryCurrency.class));
        when(loan.getTotalOverpaidAsMoney().isZero()).thenReturn(true);
        when(loan.getSummary().getTotalOutstanding(loan.getCurrency()).isZero()).thenReturn(false);
        assertEquals(expected, machine.dryTransition(event, loan));
        verify(loan, never()).setLoanStatus(any());
        machine.transition(event, loan);
        verify(balances).updateLoanSummaryDerivedFields(loan);
        if (expected != from || event == LoanEvent.LOAN_INITIATE_TRANSFER) {
            verify(loan).setLoanStatus(expected);
            verify(notifier).notifyPostBusinessEvent(any());
            if (expected == LoanStatus.ACTIVE) {
                verify(loan).setClosedBy(null);
                verify(loan).setClosedOnDate(null);
                verify(loan).setOverpaidOnDate(null);
            } else if (expected == LoanStatus.APPROVED) {
                verify(loan).setDisbursedBy(null);
                verify(loan).setActualDisbursementDate(null);
            } else if (expected == LoanStatus.SUBMITTED_AND_PENDING_APPROVAL) {
                verify(loan).setApprovedBy(null);
                verify(loan).setApprovedOnDate(null);
            }
        } else {
            verify(loan, never()).setLoanStatus(any());
            verifyNoInteractions(notifier);
        }
    }

    @Test
    void creationDoesNotPublishDuplicateStatusEvent() {
        Loan loan = mock(Loan.class);
        assertEquals(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL, machine.dryTransition(LoanEvent.LOAN_CREATED, loan));
        machine.transition(LoanEvent.LOAN_CREATED, loan);
        verify(loan).setLoanStatus(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        verifyNoInteractions(notifier);
    }

    static Stream<Arguments> balances() {
        return Stream.of(Arguments.of(LoanStatus.ACTIVE, true, false, false, LoanStatus.OVERPAID),
                Arguments.of(LoanStatus.ACTIVE, false, false, true, LoanStatus.CLOSED_OBLIGATIONS_MET),
                Arguments.of(LoanStatus.ACTIVE, false, true, false, LoanStatus.ACTIVE),
                Arguments.of(LoanStatus.OVERPAID, false, false, true, LoanStatus.CLOSED_OBLIGATIONS_MET),
                Arguments.of(LoanStatus.OVERPAID, false, true, false, LoanStatus.ACTIVE),
                Arguments.of(LoanStatus.OVERPAID, true, false, true, LoanStatus.OVERPAID),
                Arguments.of(LoanStatus.CLOSED_OBLIGATIONS_MET, true, false, true, LoanStatus.OVERPAID),
                Arguments.of(LoanStatus.CLOSED_OBLIGATIONS_MET, false, true, false, LoanStatus.ACTIVE),
                Arguments.of(LoanStatus.CLOSED_OBLIGATIONS_MET, false, false, true, LoanStatus.CLOSED_OBLIGATIONS_MET),
                Arguments.of(LoanStatus.CLOSED_WRITTEN_OFF, false, true, false, LoanStatus.ACTIVE),
                Arguments.of(LoanStatus.CLOSED_RESCHEDULE_OUTSTANDING_AMOUNT, false, true, false, LoanStatus.ACTIVE),
                Arguments.of(LoanStatus.CLOSED_WRITTEN_OFF, false, false, false, LoanStatus.CLOSED_WRITTEN_OFF),
                Arguments.of(LoanStatus.APPROVED, false, true, false, LoanStatus.APPROVED));
    }

    @ParameterizedTest
    @MethodSource("balances")
    void determineFromBalances(LoanStatus from, boolean overpaid, boolean outstanding, boolean repaid, LoanStatus expected) {
        Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        when(loan.getStatus()).thenReturn(from);
        when(loan.getCurrency()).thenReturn(mock(MonetaryCurrency.class));
        when(loan.getTotalOverpaid()).thenReturn(overpaid ? BigDecimal.ONE : BigDecimal.ZERO);
        when(loan.getLoanCharges()).thenReturn(Set.of());
        Money balance = mock(Money.class);
        when(balance.isGreaterThanZero()).thenReturn(outstanding);
        when(loan.getSummary().getTotalOutstanding(loan.getCurrency())).thenReturn(balance);
        when(loan.getSummary().isRepaidInFull(loan.getCurrency())).thenReturn(repaid);
        LocalDate date = LocalDate.of(2024, 2, 29);
        machine.determineAndTransition(loan, date);
        if (expected != from) {
            verify(loan).setLoanStatus(expected);
            verify(notifier).notifyPostBusinessEvent(any());
            if (expected == LoanStatus.CLOSED_OBLIGATIONS_MET) {
                verify(loan).setClosedOnDate(date);
                verify(loan).setActualMaturityDate(date);
            } else if (expected == LoanStatus.OVERPAID) {
                verify(loan).setOverpaidOnDate(date);
            }
        } else {
            verify(loan, never()).setLoanStatus(any());
        }
    }

    @Test
    void adjustmentOfClosedLoanWithExcessPaymentBecomesOverpaid() {
        Loan loan = mock(Loan.class);
        when(loan.getStatus()).thenReturn(LoanStatus.CLOSED_WRITTEN_OFF);
        when(loan.getTotalOverpaid()).thenReturn(new BigDecimal("0.01"));
        assertEquals(LoanStatus.OVERPAID, machine.dryTransition(LoanEvent.LOAN_ADJUST_TRANSACTION, loan));
        machine.transition(LoanEvent.LOAN_ADJUST_TRANSACTION, loan);
        verify(loan).setLoanStatus(LoanStatus.OVERPAID);
    }

    @Test
    void disbursementClosesOverpaidLoanOnlyWhenBothBalancesAreZero() {
        Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        when(loan.getStatus()).thenReturn(LoanStatus.OVERPAID);
        when(loan.getCurrency()).thenReturn(mock(MonetaryCurrency.class));
        when(loan.getTotalOverpaidAsMoney().isZero()).thenReturn(true);
        when(loan.getSummary().getTotalOutstanding(loan.getCurrency()).isZero()).thenReturn(true);
        assertEquals(LoanStatus.CLOSED_OBLIGATIONS_MET, machine.dryTransition(LoanEvent.LOAN_DISBURSED, loan));
        when(loan.getTotalOverpaidAsMoney().isZero()).thenReturn(false);
        assertEquals(LoanStatus.OVERPAID, machine.dryTransition(LoanEvent.LOAN_DISBURSED, loan));
    }

    @Test
    void outstandingActiveChargePreventsClosureEvenWithRepaidSchedule() {
        Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getTotalOverpaid()).thenReturn(BigDecimal.ZERO);
        when(loan.getSummary().isRepaidInFull(loan.getCurrency())).thenReturn(true);
        LoanCharge charge = mock(LoanCharge.class);
        when(charge.isActive()).thenReturn(true);
        when(charge.amount()).thenReturn(BigDecimal.ONE);
        when(loan.getLoanCharges()).thenReturn(Set.of(charge));
        machine.determineAndTransition(loan, LocalDate.of(2024, 2, 29));
        verify(loan, never()).setLoanStatus(any());
        when(charge.isPaid()).thenReturn(true);
        machine.determineAndTransition(loan, LocalDate.of(2024, 2, 29));
        verify(loan).setLoanStatus(LoanStatus.CLOSED_OBLIGATIONS_MET);
    }

    @Test
    void uninitializedLoanDoesNotRecalculateBalances() {
        machine.determineAndTransition(mock(Loan.class), LocalDate.of(2024, 1, 1));
        verifyNoInteractions(balances, notifier);
    }
}
