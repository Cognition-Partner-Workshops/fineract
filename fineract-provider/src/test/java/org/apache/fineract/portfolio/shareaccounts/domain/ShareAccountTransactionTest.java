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
package org.apache.fineract.portfolio.shareaccounts.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ShareAccountTransactionTest {

    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 11, 5);

    @Test
    void purchaseTransactionAmountPaidKeepsExactDecimalAmount() {
        ShareAccountTransaction transaction = new ShareAccountTransaction(TRANSACTION_DATE, 3L, new BigDecimal("0.1"));

        assertEquals(new BigDecimal("0.3"), transaction.amount());
        assertEquals(new BigDecimal("0.3"), transaction.amountPaid());
    }

    @Test
    void redeemTransactionAmountPaidKeepsExactDecimalAmount() {
        ShareAccountTransaction transaction = ShareAccountTransaction.createRedeemTransaction(TRANSACTION_DATE, 7L, new BigDecimal("1.15"));

        assertEquals(new BigDecimal("8.05"), transaction.amount());
        assertEquals(new BigDecimal("8.05"), transaction.amountPaid());
    }

    @Test
    void purchaseTransactionAmountPaidKeepsExactLargeAmount() {
        ShareAccountTransaction transaction = new ShareAccountTransaction(TRANSACTION_DATE, 1_000_000L, new BigDecimal("12345678901.23"));

        assertEquals(new BigDecimal("12345678901230000.00"), transaction.amountPaid());
    }
}
