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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Regression tests: recurring deposit commands (deposit, withdrawal, close, ...) assemble the account through
 * {@link DepositAccountAssembler#assembleFrom(Long, DepositAccountType)}, which must enforce the office hierarchy.
 */
class DepositAccountAssemblerOfficeScopeTest {

    private static final Long ACCOUNT_ID = 7L;

    @Mock
    private SavingsAccountRepositoryWrapper savingsAccountRepository;
    @Mock
    private PlatformSecurityContext context;

    @InjectMocks
    private DepositAccountAssembler underTest;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        final Office siblingBranch = mock(Office.class);
        when(siblingBranch.getHierarchy()).thenReturn(".1.3.");
        final SavingsAccount account = mock(SavingsAccount.class);
        when(account.office()).thenReturn(siblingBranch);
        when(savingsAccountRepository.findOneWithNotFoundDetection(ACCOUNT_ID, DepositAccountType.RECURRING_DEPOSIT)).thenReturn(account);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void siblingBranchUserCannotAssembleRecurringDepositAccount() {
        loginWithHierarchy(".1.2.");

        assertThrows(NoAuthorizationException.class, () -> underTest.assembleFrom(ACCOUNT_ID, DepositAccountType.RECURRING_DEPOSIT));
    }

    @Test
    void headOfficeUserCanAssembleBranchRecurringDepositAccount() {
        loginWithHierarchy(".");

        assertDoesNotThrow(() -> underTest.assembleFrom(ACCOUNT_ID, DepositAccountType.RECURRING_DEPOSIT));
    }

    @Test
    void backgroundJobWithoutUserCanAssembleAccount() {
        when(context.getAuthenticatedUserIfPresent()).thenReturn(null);
        doAnswer(invocation -> {
            throw new IllegalStateException("no authenticated user");
        }).when(context).validateAccessRights(anyString());

        assertDoesNotThrow(() -> underTest.assembleFrom(ACCOUNT_ID, DepositAccountType.RECURRING_DEPOSIT));
    }

    private void loginWithHierarchy(final String hierarchy) {
        final Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(hierarchy);
        final AppUser user = mock(AppUser.class);
        when(user.getOffice()).thenReturn(office);
        when(context.authenticatedUser()).thenReturn(user);
        when(context.getAuthenticatedUserIfPresent()).thenReturn(user);
        doAnswer(invocation -> {
            if (!invocation.<String>getArgument(0).startsWith(hierarchy)) {
                throw new NoAuthorizationException("The user doesn't have enough permissions to access the resource.");
            }
            return null;
        }).when(context).validateAccessRights(anyString());
    }
}
