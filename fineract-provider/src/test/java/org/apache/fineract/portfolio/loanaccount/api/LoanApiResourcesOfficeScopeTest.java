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
package org.apache.fineract.portfolio.loanaccount.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargePaidByReadService;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Regression tests: loan transaction, charge and disbursement endpoints must only operate on loans visible to the
 * caller's office hierarchy (LoanReadPlatformService#retrieveOne applies the hierarchy filter and throws
 * LoanNotFoundException for loans of sibling offices).
 */
class LoanApiResourcesOfficeScopeTest {

    private static final Long SIBLING_LOAN_ID = 1L;
    private static final Long OWN_LOAN_ID = 2L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private LoanReadPlatformService loanReadPlatformService;
    @Mock
    private LoanChargeReadPlatformService loanChargeReadPlatformService;
    @Mock
    private DefaultToApiJsonSerializer<LoanTransactionData> toApiJsonSerializer;
    @Mock
    private LoanChargePaidByReadService loanChargePaidByReadService;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @InjectMocks
    private LoanTransactionsApiResource transactionsResource;
    @InjectMocks
    private LoanChargesApiResource chargesResource;
    @InjectMocks
    private LoanDisbursementDetailApiResource disbursementResource;

    private AutoCloseable mocks;
    private final UriInfo uriInfo = mock(UriInfo.class);

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.authenticatedUser()).thenReturn(mock(AppUser.class));
        when(loanReadPlatformService.retrieveOne(SIBLING_LOAN_ID)).thenThrow(new LoanNotFoundException(SIBLING_LOAN_ID));
        when(loanReadPlatformService.retrieveOne(OWN_LOAN_ID)).thenReturn(mock(LoanAccountData.class));
        when(loanReadPlatformService.retrieveLoanTransaction(anyLong(), anyLong())).thenReturn(mock(LoanTransactionData.class));
        when(apiRequestParameterHelper.process(any())).thenReturn(mock(ApiRequestJsonSerializationSettings.class));
        when(commandsSourceWritePlatformService.logCommandSource(any())).thenReturn(CommandProcessingResult.empty());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void siblingBranchLoanTransactionCannotBeRead() {
        assertThrows(LoanNotFoundException.class, () -> transactionsResource.retrieveTransaction(SIBLING_LOAN_ID, 5L, null, uriInfo));
        verify(loanReadPlatformService, never()).retrieveLoanTransaction(anyLong(), anyLong());
    }

    @Test
    void ownBranchLoanTransactionCanBeRead() {
        transactionsResource.retrieveTransaction(OWN_LOAN_ID, 5L, null, uriInfo);
        verify(loanReadPlatformService).retrieveLoanTransaction(OWN_LOAN_ID, 5L);
    }

    @Test
    void repaymentCannotBePostedOnSiblingBranchLoan() {
        assertThrows(LoanNotFoundException.class,
                () -> transactionsResource.executeLoanTransaction(SIBLING_LOAN_ID, "repayment", "{\"transactionAmount\": 10}"));
        verify(commandsSourceWritePlatformService, never()).logCommandSource(any());
    }

    @Test
    void siblingBranchLoanChargesCannotBeListed() {
        assertThrows(LoanNotFoundException.class, () -> chargesResource.retrieveAllLoanCharges(SIBLING_LOAN_ID, uriInfo));
        verify(loanChargeReadPlatformService, never()).retrieveLoanCharges(anyLong());
    }

    @Test
    void siblingBranchLoanChargeCannotBeWaived() {
        assertThrows(LoanNotFoundException.class, () -> chargesResource.executeLoanCharge(SIBLING_LOAN_ID, 3L, "waive", "{}"));
        verify(commandsSourceWritePlatformService, never()).logCommandSource(any());
    }

    @Test
    void siblingBranchDisbursementDetailCannotBeReadOrChanged() {
        assertThrows(LoanNotFoundException.class, () -> disbursementResource.retriveDetail(SIBLING_LOAN_ID, 4L, uriInfo));
        verify(loanReadPlatformService, never()).retrieveLoanDisbursementDetail(anyLong(), anyLong());
        assertThrows(LoanNotFoundException.class,
                () -> disbursementResource.updateDisbursementDate(SIBLING_LOAN_ID, 4L, "{\"expectedDisbursementDate\": \"x\"}"));
        verify(commandsSourceWritePlatformService, never()).logCommandSource(any());
    }
}
