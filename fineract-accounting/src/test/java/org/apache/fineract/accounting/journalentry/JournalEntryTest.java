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
package org.apache.fineract.accounting.journalentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.apache.fineract.accounting.AccountingTestSupport;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountType;
import org.apache.fineract.accounting.journalentry.data.JournalEntryData;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.PortfolioProductType;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JournalEntryTest extends AccountingTestSupport {

    @ParameterizedTest
    @EnumSource(JournalEntryType.class)
    void creationPreservesAmountAssociationsAndInitialState(JournalEntryType type) {
        Office office = mock(Office.class);
        GLAccount account = mock(GLAccount.class);
        PaymentDetail payment = mock(PaymentDetail.class);
        BigDecimal amount = new BigDecimal("123456.123456");
        JournalEntry entry = JournalEntry.createNew(office, payment, account, "USD", "TX1", true, BUSINESS_DATE.minusDays(1), type, amount,
                "", PortfolioProductType.LOAN.getValue(), 10L, "ref", 11L, 12L, 13L, 14L);
        assertSame(office, entry.getOffice());
        assertSame(account, entry.getGlAccount());
        assertSame(payment, entry.getPaymentDetail());
        assertEquals(amount, entry.getAmount());
        assertEquals("USD", entry.getCurrencyCode());
        assertEquals("TX1", entry.getTransactionId());
        assertEquals("ref", entry.getReferenceNumber());
        assertEquals(11L, entry.getLoanTransactionId());
        assertEquals(12L, entry.getSavingsTransactionId());
        assertEquals(13L, entry.getClientTransactionId());
        assertEquals(14L, entry.getShareTransactionId());
        assertEquals(BUSINESS_DATE, entry.getSubmittedOnDate());
        assertEquals(BUSINESS_DATE.minusDays(1), entry.getTransactionDate());
        assertTrue(entry.isManualEntry());
        assertFalse(entry.isReversed());
        assertNull(entry.getReversalJournalEntry());
        assertNull(entry.getDescription());
        assertEquals(type == JournalEntryType.DEBIT, entry.isDebitEntry());
        assertEquals(type == JournalEntryType.CREDIT, entry.isCreditEntry());
    }

    @Test
    void mapperPreservesAccountingIdentityAndExactAmountWithOptionalPaymentAbsent() {
        Office office = mock(Office.class);
        when(office.getId()).thenReturn(1L);
        when(office.getName()).thenReturn("Branch");
        GLAccount account = mock(GLAccount.class);
        when(account.getId()).thenReturn(2L);
        when(account.getGlCode()).thenReturn("1000");
        when(account.getName()).thenReturn("Cash");
        when(account.getType()).thenReturn(GLAccountType.ASSET.getValue());
        BigDecimal amount = new BigDecimal("0.123456");
        JournalEntry entry = JournalEntry.createNew(office, null, account, "USD", "TX1", false, BUSINESS_DATE, JournalEntryType.DEBIT,
                amount, "description", PortfolioProductType.LOAN.getValue(), 10L, "reference", null, null, null, null);
        JournalEntryData data = new JournalEntryMapperImpl().map(entry);
        assertEquals(1L, data.getOfficeId());
        assertEquals("Branch", data.getOfficeName());
        assertEquals(2L, data.getGlAccountId());
        assertEquals("1000", data.getGlAccountCode());
        assertEquals("Cash", data.getGlAccountName());
        assertEquals(amount, data.getAmount());
        assertEquals("USD", data.getCurrency().getCode());
        assertEquals(JournalEntryType.DEBIT.getValue().longValue(), data.getEntryType().getId());
        assertEquals(GLAccountType.ASSET.getValue().longValue(), data.getGlAccountType().getId());
        assertEquals(PortfolioProductType.LOAN.getValue().longValue(), data.getEntityType().getId());
        assertEquals("reference", data.getReferenceNumber());
        assertNull(data.getPaymentTypeId());
        assertNull(new JournalEntryMapperImpl().map((JournalEntry) null));
    }
}
