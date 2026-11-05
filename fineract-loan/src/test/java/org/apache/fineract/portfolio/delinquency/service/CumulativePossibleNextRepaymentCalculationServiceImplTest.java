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
package org.apache.fineract.portfolio.delinquency.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.junit.jupiter.api.Test;

class CumulativePossibleNextRepaymentCalculationServiceImplTest {

    private final CumulativePossibleNextRepaymentCalculationServiceImpl service = new CumulativePossibleNextRepaymentCalculationServiceImpl();
    private final Loan loan = mock(Loan.class);
    private final LocalDate dueDate = LocalDate.of(2023, 3, 1);

    @Test
    void acceptsOnlyCumulativeLoans() {
        assertTrue(service.canAccept(loan));
        when(loan.isProgressiveSchedule()).thenReturn(true);
        assertFalse(service.canAccept(loan));
    }

    @Test
    void returnsNextInstallmentOutstandingIncludingFutureRecalculationFallback() {
        LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        MonetaryCurrency currency = new MonetaryCurrency("USD", 2, 1);
        Money outstanding = mock(Money.class);
        when(loan.getRelatedRepaymentScheduleInstallment(dueDate)).thenReturn(installment);
        when(loan.getCurrency()).thenReturn(currency);
        when(installment.getTotalOutstanding(currency)).thenReturn(outstanding);
        when(outstanding.getAmount()).thenReturn(new BigDecimal("620.00"));
        assertEquals(new BigDecimal("620.00"), service.possibleNextRepaymentAmount(loan, dueDate));
        assertEquals(new BigDecimal("620.00"), service.calculateInterestRecalculationFutureOutstandingValue(loan, dueDate, installment));
    }

    @Test
    void returnsZeroForMissingOrPaidInstallment() {
        assertEquals(BigDecimal.ZERO, service.possibleNextRepaymentAmount(loan, dueDate));
        LoanRepaymentScheduleInstallment installment = mock(LoanRepaymentScheduleInstallment.class);
        when(loan.getRelatedRepaymentScheduleInstallment(dueDate)).thenReturn(installment);
        when(installment.isObligationsMet()).thenReturn(true);
        assertEquals(BigDecimal.ZERO, service.possibleNextRepaymentAmount(loan, dueDate));
    }
}
