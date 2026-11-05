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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanAssemblerImplGlimAccountNumberTest {

    private static final BigDecimal APPLICATION_ID = BigDecimal.valueOf(7);
    private static final BigDecimal TOTAL_LOAN = BigDecimal.valueOf(1000);

    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @Mock
    private AccountNumberFormatRepositoryWrapper accountNumberFormatRepository;
    @Mock
    private AccountNumberGenerator accountNumberGenerator;
    @Mock
    private GLIMAccountInfoWritePlatformService glimAccountInfoWritePlatformService;
    @Mock
    private GLIMAccountInfoRepository glimRepository;

    @InjectMocks
    private LoanAssemblerImpl underTest;

    @Test
    void parentGlimApplicationOpensNewGlimAccountWithoutConsultingExistingGlimAccounts() {
        final JsonObject element = new JsonObject();
        final JsonCommand command = mock(JsonCommand.class);
        when(command.parsedJson()).thenReturn(element);
        final AccountNumberFormat accountNumberFormat = mock(AccountNumberFormat.class);
        when(accountNumberFormatRepository.findByAccountType(EntityAccountType.LOAN)).thenReturn(accountNumberFormat);
        final Group group = mock(Group.class);
        final Loan loan = mock(Loan.class);
        when(loan.getLoanType()).thenReturn(AccountType.GLIM);
        when(loan.getGroup()).thenReturn(group);
        when(fromApiJsonHelper.extractBigDecimalNamed("applicationId", element, (Locale) null)).thenReturn(APPLICATION_ID);
        when(fromApiJsonHelper.extractBigDecimalNamed("totalLoan", element, (Locale) null)).thenReturn(TOTAL_LOAN);
        when(fromApiJsonHelper.extractBooleanNamed("isParentAccount", element)).thenReturn(true);
        when(accountNumberGenerator.generate(any(Loan.class), any(AccountNumberFormat.class))).thenReturn("000000042");
        final GroupLoanIndividualMonitoringAccount glimAccount = mock(GroupLoanIndividualMonitoringAccount.class);
        when(glimAccountInfoWritePlatformService.createGLIMAccount("000000042", group, TOTAL_LOAN, 1L, true,
                LoanStatus.SUBMITTED_AND_PENDING_APPROVAL.getValue(), APPLICATION_ID)).thenReturn(glimAccount);

        underTest.accountNumberGeneration(command, loan);

        verify(loan).setAccountNumber("0000000421");
        verify(loan).setGlim(glimAccount);
        verifyNoInteractions(glimRepository);
    }
}
