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
package org.apache.fineract.portfolio.account.domain;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.account.AccountDetailConstants;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class AccountTransferDetailAssemblerSecurityTest {

    @Mock
    private ClientRepositoryWrapper clientRepository;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private SavingsAccountAssembler savingsAccountAssembler;
    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @Mock
    private LoanAssembler loanAccountAssembler;
    @InjectMocks
    private AccountTransferDetailAssembler underTest;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void rejectsSavingsTransferWhenSourceOfficeDoesNotMatchLoadedAccount() {
        final SavingsAccount fromSavingsAccount = savingsAccount(1L, 2L);
        final SavingsAccount toSavingsAccount = Mockito.mock(SavingsAccount.class);
        stubSourceMetadata(99L, 2L);

        assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.assembleSavingsToSavingsTransfer(command(), fromSavingsAccount, toSavingsAccount));
    }

    @Test
    void rejectsSavingsTransferWhenSourceClientDoesNotMatchLoadedAccount() {
        final SavingsAccount fromSavingsAccount = savingsAccount(1L, 2L);
        final SavingsAccount toSavingsAccount = Mockito.mock(SavingsAccount.class);
        stubSourceMetadata(1L, 99L);

        assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.assembleSavingsToSavingsTransfer(command(), fromSavingsAccount, toSavingsAccount));
    }

    @Test
    void permitsSavingsTransferWhenSourceMetadataMatchesLoadedAccount() {
        final SavingsAccount fromSavingsAccount = savingsAccount(1L, 2L);
        final SavingsAccount toSavingsAccount = Mockito.mock(SavingsAccount.class);
        stubSourceMetadata(1L, 2L);

        final AccountTransferDetails result = underTest.assembleSavingsToSavingsTransfer(command(), fromSavingsAccount, toSavingsAccount);

        assertNotNull(result);
        assertSame(fromSavingsAccount, result.fromSavingsAccount());
    }

    @Test
    void rejectsLoanRefundWhenSourceClientDoesNotMatchLoadedAccount() {
        final Loan fromLoanAccount = loanAccount(1L, 2L);
        final SavingsAccount toSavingsAccount = Mockito.mock(SavingsAccount.class);
        stubSourceMetadata(1L, 99L);

        assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.assembleLoanToSavingsTransfer(command(), fromLoanAccount, toSavingsAccount));
    }

    @Test
    void permitsLoanRefundWhenSourceMetadataMatchesLoadedAccount() {
        final Loan fromLoanAccount = loanAccount(1L, 2L);
        final SavingsAccount toSavingsAccount = Mockito.mock(SavingsAccount.class);
        stubSourceMetadata(1L, 2L);

        final AccountTransferDetails result = underTest.assembleLoanToSavingsTransfer(command(), fromLoanAccount, toSavingsAccount);

        assertNotNull(result);
        assertSame(fromLoanAccount, result.fromLoanAccount());
    }

    private void stubSourceMetadata(final Long fromOfficeId, final Long fromClientId) {
        when(fromApiJsonHelper.extractLongNamed(eq(AccountDetailConstants.fromOfficeIdParamName), any(JsonElement.class)))
                .thenReturn(fromOfficeId);
        when(fromApiJsonHelper.extractLongNamed(eq(AccountDetailConstants.fromClientIdParamName), any(JsonElement.class)))
                .thenReturn(fromClientId);
    }

    private JsonCommand command() {
        return JsonCommand.fromJsonElement(null, JsonParser.parseString("{}"));
    }

    private SavingsAccount savingsAccount(final Long officeId, final Long clientId) {
        final Office office = Mockito.mock(Office.class);
        when(office.getId()).thenReturn(officeId);
        final Client client = Mockito.mock(Client.class);
        when(client.getId()).thenReturn(clientId);
        final SavingsAccount savingsAccount = Mockito.mock(SavingsAccount.class);
        when(savingsAccount.office()).thenReturn(office);
        when(savingsAccount.getClient()).thenReturn(client);
        return savingsAccount;
    }

    private Loan loanAccount(final Long officeId, final Long clientId) {
        final Office office = Mockito.mock(Office.class);
        when(office.getId()).thenReturn(officeId);
        final Client client = Mockito.mock(Client.class);
        when(client.getId()).thenReturn(clientId);
        final Loan loanAccount = Mockito.mock(Loan.class);
        when(loanAccount.getOffice()).thenReturn(office);
        when(loanAccount.client()).thenReturn(client);
        return loanAccount;
    }
}
