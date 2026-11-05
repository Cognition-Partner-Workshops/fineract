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
package org.apache.fineract.organisation.teller.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccount;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.teller.domain.Cashier;
import org.apache.fineract.organisation.teller.domain.CashierRepository;
import org.apache.fineract.organisation.teller.domain.CashierTransaction;
import org.apache.fineract.organisation.teller.domain.CashierTransactionRepository;
import org.apache.fineract.organisation.teller.domain.Teller;
import org.apache.fineract.organisation.teller.serialization.TellerCommandFromApiJsonDeserializer;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TellerWritePlatformServiceJpaImplTest {

    private static final Long CASHIER_ID = 3L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private TellerCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    @Mock
    private CashierRepository cashierRepository;
    @Mock
    private CashierTransactionRepository cashierTxnRepository;
    @Mock
    private JournalEntryRepository glJournalEntryRepository;
    @Mock
    private FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper;

    @InjectMocks
    private TellerWritePlatformServiceJpaImpl underTest;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2025, 1, 15))));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void allocateCashReadsEntityTypeOnlyToRecordItOnTheCashierTransaction() {
        final AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(1L);
        when(context.authenticatedUser()).thenReturn(user);
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(2L);
        final Teller teller = mock(Teller.class);
        when(teller.getOffice()).thenReturn(office);
        final Cashier cashier = mock(Cashier.class);
        when(cashier.getTeller()).thenReturn(teller);
        when(cashierRepository.findById(CASHIER_ID)).thenReturn(Optional.of(cashier));
        final FinancialActivityAccount financialActivityAccount = mock(FinancialActivityAccount.class);
        when(financialActivityAccount.getGlAccount()).thenReturn(mock(GLAccount.class));
        when(financialActivityAccountRepositoryWrapper.findByFinancialActivityTypeWithNotFoundDetection(anyInt()))
                .thenReturn(financialActivityAccount);
        final JsonCommand command = mock(JsonCommand.class);
        when(command.stringValueOfParameterNamed("entityType")).thenReturn("client");

        underTest.allocateCashToCashier(CASHIER_ID, command);

        verify(command, times(1)).stringValueOfParameterNamed("entityType");
        verify(cashierTxnRepository).save(any(CashierTransaction.class));
        verify(glJournalEntryRepository, times(2)).saveAndFlush(any(JournalEntry.class));
    }
}
