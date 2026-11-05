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
package org.apache.fineract.portfolio.savings.domain;

import static org.apache.fineract.portfolio.savings.DepositsApiConstants.onAccountClosureIdParamName;
import static org.apache.fineract.portfolio.savings.DepositsApiConstants.toSavingsAccountIdParamName;
import static org.apache.fineract.portfolio.savings.DepositsApiConstants.transferDescriptionParamName;
import static org.apache.fineract.portfolio.savings.SavingsApiConstants.closedOnDateParamName;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.savings.DepositAccountOnClosureType;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.service.SavingsAccountDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DepositAccountDomainServiceJpaSecurityTest {

    @Mock
    private SavingsAccountRepositoryWrapper savingsAccountRepository;
    @Mock
    private JournalEntryWritePlatformService journalEntryWritePlatformService;
    @Mock
    private AccountNumberGenerator accountNumberGenerator;
    @Mock
    private DepositAccountAssembler depositAccountAssembler;
    @Mock
    private SavingsAccountDomainService savingsAccountDomainService;
    @Mock
    private AccountTransfersWritePlatformService accountTransfersWritePlatformService;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private AccountNumberFormatRepositoryWrapper accountNumberFormatRepository;
    @Mock
    private CalendarInstanceRepository calendarInstanceRepository;
    @Mock
    private ExternalIdFactory externalIdFactory;
    @Mock
    private OfficeScopeValidator officeScopeValidator;
    @Mock
    private FixedDepositAccount fixedDepositAccount;
    @Mock
    private RecurringDepositAccount recurringDepositAccount;
    @Mock
    private SavingsAccount targetSavingsAccount;
    @Mock
    private SavingsAccountTransaction transaction;
    @Mock
    private MonetaryCurrency currency;
    @InjectMocks
    private DepositAccountDomainServiceJpa underTest;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(configurationDomainService.isSavingsInterestPostingAtCurrentPeriodEnd()).thenReturn(false);
        when(configurationDomainService.retrieveFinancialYearBeginningMonth()).thenReturn(1);
        prepareAccount(fixedDepositAccount);
        prepareAccount(recurringDepositAccount);
        when(depositAccountAssembler.assembleFrom(44L, DepositAccountType.SAVINGS_DEPOSIT)).thenReturn(targetSavingsAccount);
        when(currency.getCode()).thenReturn("USD");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void deniesDifferentOwnerBeforeAnyFdOrRdCloseTransfer() {
        doThrow(new NoAuthorizationException("wrong owner")).when(officeScopeValidator).validateSavingsHasSameOwner(44L, 7L);
        final JsonCommand command = transferCommand();

        assertAll(
                () -> assertThrows(NoAuthorizationException.class,
                        () -> underTest.handleFDAccountClosure(fixedDepositAccount, null, null, command, new HashMap<>())),
                () -> assertThrows(NoAuthorizationException.class,
                        () -> underTest.handleFDAccountMaturityClosure(fixedDepositAccount, null, null, DateTimeFormatter.ISO_LOCAL_DATE,
                                LocalDate.now(), DepositAccountOnClosureType.TRANSFER_TO_SAVINGS.getValue(), 44L, "", new HashMap<>())),
                () -> assertThrows(NoAuthorizationException.class,
                        () -> underTest.handleRDAccountClosure(recurringDepositAccount, null, null, command, new HashMap<>())),
                () -> assertThrows(NoAuthorizationException.class,
                        () -> underTest.handleFDAccountPreMatureClosure(fixedDepositAccount, null, null, command, new HashMap<>())),
                () -> assertThrows(NoAuthorizationException.class,
                        () -> underTest.handleRDAccountPreMatureClosure(recurringDepositAccount, null, null, command, new HashMap<>())),
                () -> verify(accountTransfersWritePlatformService, never()).transferFunds(any(AccountTransferDTO.class)));

    }

    @Test
    void transfersToSameOwnerWhenValidationPasses() {
        assertDoesNotThrow(() -> underTest.handleFDAccountClosure(fixedDepositAccount, null, null, transferCommand(), new HashMap<>()));
        assertDoesNotThrow(() -> underTest.handleFDAccountMaturityClosure(fixedDepositAccount, null, null, DateTimeFormatter.ISO_LOCAL_DATE,
                LocalDate.now(), DepositAccountOnClosureType.TRANSFER_TO_SAVINGS.getValue(), 44L, "", new HashMap<>()));
        assertDoesNotThrow(() -> underTest.handleRDAccountClosure(recurringDepositAccount, null, null, transferCommand(), new HashMap<>()));
        assertDoesNotThrow(
                () -> underTest.handleFDAccountPreMatureClosure(fixedDepositAccount, null, null, transferCommand(), new HashMap<>()));
        assertDoesNotThrow(
                () -> underTest.handleRDAccountPreMatureClosure(recurringDepositAccount, null, null, transferCommand(), new HashMap<>()));

        verify(accountTransfersWritePlatformService, times(5)).transferFunds(any(AccountTransferDTO.class));
    }

    private void prepareAccount(final SavingsAccount account) {
        when(account.getId()).thenReturn(7L);
        when(account.getAccountBalance()).thenReturn(BigDecimal.TEN);
        when(account.findExistingTransactionIds()).thenReturn(List.of());
        when(account.findExistingReversedTransactionIds()).thenReturn(List.of());
        when(account.getTransactions()).thenReturn(List.of(transaction));
        when(account.getCurrency()).thenReturn(currency);
        when(account.deriveAccountingBridgeData(anyString(), anySet(), anySet(), anyBoolean(), anyBoolean())).thenReturn(Map.of());
    }

    private JsonCommand transferCommand() {
        final JsonCommand command = org.mockito.Mockito.mock(JsonCommand.class);
        when(command.localDateValueOfParameterNamed(closedOnDateParamName)).thenReturn(LocalDate.now());
        when(command.extractLocale()).thenReturn(Locale.US);
        when(command.dateFormat()).thenReturn("yyyy-MM-dd");
        when(command.integerValueOfParameterNamed(onAccountClosureIdParamName))
                .thenReturn(DepositAccountOnClosureType.TRANSFER_TO_SAVINGS.getValue());
        when(command.longValueOfParameterNamed(toSavingsAccountIdParamName)).thenReturn(44L);
        when(command.stringValueOfParameterNamed(transferDescriptionParamName)).thenReturn("");
        return command;
    }
}
