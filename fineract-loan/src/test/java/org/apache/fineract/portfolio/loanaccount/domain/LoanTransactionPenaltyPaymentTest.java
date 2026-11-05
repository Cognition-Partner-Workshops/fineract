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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class LoanTransactionPenaltyPaymentTest {

    private static LoanTransaction transaction(final LoanTransactionType type, final Boolean... penaltyFlags) {
        final LoanTransaction transaction = spy(new LoanTransaction());
        doReturn(type).when(transaction).getTypeOf();
        for (final Boolean penalty : penaltyFlags) {
            final LoanCharge loanCharge = mock(LoanCharge.class);
            when(loanCharge.isPenaltyCharge()).thenReturn(penalty);
            final LoanChargePaidBy chargePaidBy = mock(LoanChargePaidBy.class);
            when(chargePaidBy.getLoanCharge()).thenReturn(loanCharge);
            transaction.getLoanChargesPaid().add(chargePaidBy);
        }
        return transaction;
    }

    @Test
    void chargePaymentOfPenaltyIsPenaltyPayment() {
        assertTrue(transaction(LoanTransactionType.CHARGE_PAYMENT, true).isPenaltyPayment());
    }

    @Test
    void chargePaymentOfFeeIsNotPenaltyPayment() {
        assertFalse(transaction(LoanTransactionType.CHARGE_PAYMENT, false).isPenaltyPayment());
    }

    @Test
    void chargePaymentWithoutPaidChargesIsNotPenaltyPayment() {
        assertFalse(transaction(LoanTransactionType.CHARGE_PAYMENT).isPenaltyPayment());
    }

    @Test
    void repaymentIsNeverPenaltyPayment() {
        assertFalse(transaction(LoanTransactionType.REPAYMENT, true).isPenaltyPayment());
    }
}
