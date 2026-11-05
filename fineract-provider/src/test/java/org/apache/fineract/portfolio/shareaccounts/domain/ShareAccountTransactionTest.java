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

    private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

    @Test
    void purchaseTransactionAmountPaidEqualsExactAmount() {
        ShareAccountTransaction transaction = new ShareAccountTransaction(DATE, 3L, new BigDecimal("0.10"));

        assertEquals(new BigDecimal("0.30"), transaction.amount());
        assertEquals(transaction.amount(), transaction.amountPaid());
    }

    @Test
    void redeemTransactionAmountPaidEqualsExactAmount() {
        ShareAccountTransaction transaction = ShareAccountTransaction.createRedeemTransaction(DATE, 7L, new BigDecimal("1.13"));

        assertEquals(new BigDecimal("7.91"), transaction.amount());
        assertEquals(transaction.amount(), transaction.amountPaid());
    }
}
