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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanAssemblerImplSecurityTest {

    @Mock
    private LoanRepositoryWrapper loanRepository;
    @Mock
    private OfficeScopeValidator officeScopeValidator;
    @InjectMocks
    private LoanAssemblerImpl underTest;

    @Test
    void rejectsOutOfScopeLoanBeforeRepositoryLoad() {
        doThrow(new NoAuthorizationException("loan outside user office")).when(officeScopeValidator).validateLoan(91L);
        lenient().when(loanRepository.findOneWithNotFoundDetection(91L, true))
                .thenThrow(new IllegalStateException("loan repository reached"));

        assertAll(() -> assertThrows(NoAuthorizationException.class, () -> underTest.assembleFrom(91L)),
                () -> verify(loanRepository, never()).findOneWithNotFoundDetection(91L, true));
    }

    @Test
    void loadsLoanAfterScopeValidationPasses() {
        Loan loan = org.mockito.Mockito.mock(Loan.class);
        when(loanRepository.findOneWithNotFoundDetection(91L, true)).thenReturn(loan);

        assertSame(loan, underTest.assembleFrom(91L));

        verify(loanRepository).findOneWithNotFoundDetection(91L, true);
    }
}
