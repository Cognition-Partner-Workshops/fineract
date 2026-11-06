/*
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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.collectionsheet.command.CollectionSheetBulkRepaymentCommand;
import org.apache.fineract.portfolio.collectionsheet.command.SingleRepaymentCommand;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CollectionSheetOfficeScopeSecurityTest {

    private static final Long LOAN_ID = 91L;

    @Mock
    private OfficeScopeValidator officeScopeValidator;
    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private AppUser appUser;
    @Spy
    @InjectMocks
    private LoanAssemblerImpl loanAssembler;
    @InjectMocks
    private LoanWritePlatformServiceJpaRepositoryImpl loanWritePlatformService;

    @BeforeEach
    void setUp() {
        lenient().when(context.authenticatedUser()).thenReturn(appUser);
        lenient().when(loanRepositoryWrapper.findOneWithNotFoundDetection(LOAN_ID, true))
                .thenThrow(new IllegalStateException("Reached loan repository"));
        ReflectionTestUtils.setField(loanWritePlatformService, "loanAssembler", loanAssembler);
    }

    @Test
    void rejectsCollectionSheetRepaymentForLoanOutsideUserOfficeBeforeLoadingLoan() {
        doThrow(new NoAuthorizationException("loan outside user office")).when(officeScopeValidator).validateLoan(LOAN_ID);

        assertThrows(NoAuthorizationException.class, () -> loanWritePlatformService.makeLoanBulkRepayment(collectionSheetRepayment()));

        verify(loanRepositoryWrapper, never()).findOneWithNotFoundDetection(LOAN_ID, true);
    }

    @Test
    void permitsCollectionSheetRepaymentForLoanWithinUserOffice() {
        assertThrows(IllegalStateException.class, () -> loanWritePlatformService.makeLoanBulkRepayment(collectionSheetRepayment()));

        verify(loanRepositoryWrapper).findOneWithNotFoundDetection(LOAN_ID, true);
    }

    private CollectionSheetBulkRepaymentCommand collectionSheetRepayment() {
        final var transactionDate = org.apache.fineract.infrastructure.core.service.DateUtils.parseLocalDate("2025-05-20");
        final SingleRepaymentCommand repayment = new SingleRepaymentCommand(LOAN_ID, ExternalId.empty(), BigDecimal.valueOf(100),
                transactionDate, null);
        return new CollectionSheetBulkRepaymentCommand(null, transactionDate, new SingleRepaymentCommand[] { repayment });
    }
}
