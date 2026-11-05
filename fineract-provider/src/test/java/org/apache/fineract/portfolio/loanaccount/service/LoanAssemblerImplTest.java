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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.Locale;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormat;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.accountnumberformat.domain.EntityAccountType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.accountdetails.domain.AccountType;
import org.apache.fineract.portfolio.group.domain.Group;
import org.apache.fineract.portfolio.loanaccount.domain.GLIMAccountInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.GroupLoanIndividualMonitoringAccount;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanAssemblerImplTest {

    private final GLIMAccountInfoRepository glimRepository = mock(GLIMAccountInfoRepository.class);

    @Spy
    private GLIMAccountInfoWritePlatformServiceImpl glimAccountInfoWritePlatformService = new GLIMAccountInfoWritePlatformServiceImpl(
            glimRepository);

    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @Mock
    private AccountNumberFormatRepositoryWrapper accountNumberFormatRepository;
    @Mock
    private AccountNumberGenerator accountNumberGenerator;
    @Mock
    private JsonCommand command;
    @Mock
    private Loan loan;
    @Mock
    private Group group;
    @Mock
    private AccountNumberFormat accountNumberFormat;

    @InjectMocks
    private LoanAssemblerImpl assembler;

    @Test
    void createsAndSavesParentGlimAccountWithoutCountingExistingAccounts() {
        JsonObject element = new JsonObject();
        BigDecimal applicationId = BigDecimal.TEN;
        BigDecimal totalLoan = new BigDecimal("1000");
        when(command.parsedJson()).thenReturn(element);
        when(loan.getLoanType()).thenReturn(AccountType.GLIM);
        when(loan.getGroup()).thenReturn(group);
        when(accountNumberFormatRepository.findByAccountType(EntityAccountType.LOAN)).thenReturn(accountNumberFormat);
        when(fromApiJsonHelper.extractLocaleParameter(element)).thenReturn(Locale.ENGLISH);
        when(fromApiJsonHelper.extractBigDecimalNamed("applicationId", element, Locale.ENGLISH)).thenReturn(applicationId);
        when(fromApiJsonHelper.extractBigDecimalNamed("totalLoan", element, Locale.ENGLISH)).thenReturn(totalLoan);
        when(fromApiJsonHelper.extractBooleanNamed("isParentAccount", element)).thenReturn(true);
        when(fromApiJsonHelper.extractBooleanNamed("lastApplication", element)).thenReturn(false);
        when(accountNumberGenerator.generate(loan, accountNumberFormat)).thenReturn("GLIM123");
        when(glimRepository.save(any(GroupLoanIndividualMonitoringAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assembler.accountNumberGeneration(command, loan);

        ArgumentCaptor<GroupLoanIndividualMonitoringAccount> savedAccount = ArgumentCaptor
                .forClass(GroupLoanIndividualMonitoringAccount.class);
        verify(glimRepository).save(savedAccount.capture());
        GroupLoanIndividualMonitoringAccount glimAccount = savedAccount.getValue();
        assertEquals("GLIM123", glimAccount.getAccountNumber());
        assertSame(group, glimAccount.getGroup());
        assertEquals(totalLoan, glimAccount.getPrincipalAmount());
        assertEquals(applicationId, glimAccount.getApplicationId());
        assertEquals(1L, glimAccount.getChildAccountsCount());
        assertEquals(true, glimAccount.getIsAcceptingChild());
        assertEquals(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL.getValue(), glimAccount.getLoanStatus());
        verify(loan).setAccountNumber("GLIM1231");
        verify(loan).setGlim(glimAccount);
        verify(glimRepository, never()).count();
    }
}
