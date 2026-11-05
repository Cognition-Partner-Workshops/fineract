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
package org.apache.fineract.organisation.teller.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.junit.jupiter.api.Test;

class CashierTransactionTest {

    private final JsonCommand command = mock(JsonCommand.class);

    @Test
    void createsTransactionWithoutRoundingMoney() {
        Cashier cashier = new Cashier();
        LocalDate date = LocalDate.of(2025, 1, 2);
        BigDecimal amount = new BigDecimal("1234.567891");
        when(command.integerValueOfParameterNamed("txnType")).thenReturn(101);
        when(command.bigDecimalValueOfParameterNamed("txnAmount")).thenReturn(amount);
        when(command.localDateValueOfParameterNamed("txnDate")).thenReturn(date);
        when(command.stringValueOfParameterNamed("entityType")).thenReturn("loan");
        when(command.longValueOfParameterNamed("entityId")).thenReturn(7L);
        when(command.stringValueOfParameterNamed("txnNote")).thenReturn("Allocation");
        when(command.stringValueOfParameterNamed("currencyCode")).thenReturn("USD");
        CashierTransaction transaction = CashierTransaction.fromJson(cashier, command);
        assertSame(cashier, transaction.getCashier());
        assertEquals(101, transaction.getTxnType());
        assertEquals(amount, transaction.getTxnAmount());
        assertEquals(date, transaction.getTxnDate());
        assertEquals("loan", transaction.getEntityType());
        assertEquals(7L, transaction.getEntityId());
        assertEquals("Allocation", transaction.getTxnNote());
        assertEquals("USD", transaction.getCurrencyCode());
        assertNotNull(transaction.getCreatedDate());
    }

    @Test
    void omittedDateRemainsNullAndEmptyUpdateHasNoChanges() {
        CashierTransaction transaction = CashierTransaction.fromJson(new Cashier(), command);
        assertNull(transaction.getTxnDate());
        assertTrue(transaction.update(command).isEmpty());
    }

    @Test
    void updatesTransactionAndAuditValues() {
        CashierTransaction transaction = new CashierTransaction();
        LocalDate date = LocalDate.of(2025, 1, 3);
        BigDecimal amount = new BigDecimal("0.000001");
        when(command.isChangeInIntegerParameterNamed("txnType", null)).thenReturn(true);
        when(command.integerValueOfParameterNamed("txnType")).thenReturn(102);
        when(command.isChangeInBigDecimalParameterNamed("txnAmount", null)).thenReturn(true);
        when(command.bigDecimalValueOfParameterNamed("txnAmount")).thenReturn(amount);
        when(command.isChangeInLocalDateParameterNamed("txnDate", null)).thenReturn(true);
        when(command.localDateValueOfParameterNamed("txnDate")).thenReturn(date);
        when(command.stringValueOfParameterNamed("txnDate")).thenReturn("2025-01-03");
        when(command.dateFormat()).thenReturn("yyyy-MM-dd");
        when(command.locale()).thenReturn("en");
        when(command.isChangeInStringParameterNamed("txnNote", null)).thenReturn(true);
        when(command.stringValueOfParameterNamed("txnNote")).thenReturn("Settlement");
        when(command.isChangeInStringParameterNamed("currencyCode", null)).thenReturn(true);
        when(command.stringValueOfParameterNamed("currencyCode")).thenReturn("EUR");
        assertEquals(Map.of("txnType", 102, "txnAmount", amount, "txnDate", "2025-01-03", "dateFormat", "yyyy-MM-dd", "locale", "en",
                "txnNote", "Settlement", "currencyCode", "EUR"), transaction.update(command));
        assertEquals(102, transaction.getTxnType());
        assertEquals(amount, transaction.getTxnAmount());
        assertEquals(date, transaction.getTxnDate());
        assertEquals("Settlement", transaction.getTxnNote());
        assertEquals("EUR", transaction.getCurrencyCode());
    }

    @Test
    void mapsAllCashFlowDirections() {
        assertSame(CashierTxnType.ALLOCATE, CashierTxnType.getCashierTxnType(101));
        assertSame(CashierTxnType.SETTLE, CashierTxnType.getCashierTxnType(102));
        assertSame(CashierTxnType.INWARD_CASH_TXN, CashierTxnType.getCashierTxnType(103));
        assertSame(CashierTxnType.OUTWARD_CASH_TXN, CashierTxnType.getCashierTxnType(104));
        assertNull(CashierTxnType.getCashierTxnType(-1));
        assertEquals("Allocate Cash", CashierTxnType.ALLOCATE.toString());
    }
}
