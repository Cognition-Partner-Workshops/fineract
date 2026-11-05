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
package org.apache.fineract.portfolio.savings.service;

import static org.apache.fineract.portfolio.savings.service.DepositAccountReadPlatformServiceImplOfficeScopeTest.assertLastQueryIsOfficeScoped;
import static org.apache.fineract.portfolio.savings.service.DepositAccountReadPlatformServiceImplOfficeScopeTest.loginWithHierarchy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Regression tests: savings account charges can only be read or modified for accounts within the caller's office
 * hierarchy.
 */
class SavingsAccountChargeOfficeScopeTest {

    private static final Long ACCOUNT_ID = 7L;
    private static final Long CHARGE_ID = 8L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SavingsAccountAssembler savingAccountAssembler;
    @Mock
    private SavingsAccountChargeRepositoryWrapper savingsAccountChargeRepository;
    @Mock
    private SavingsAccountRepositoryWrapper savingAccountRepositoryWrapper;

    @InjectMocks
    private SavingsAccountWritePlatformServiceJpaRepositoryImpl writeService;
    @InjectMocks
    private SavingsAccountChargeReadPlatformServiceImpl chargeReadService;

    private AutoCloseable mocks;
    private SavingsAccount account;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        account = mock(SavingsAccount.class);
        final SavingsAccountCharge charge = mock(SavingsAccountCharge.class);
        when(charge.savingsAccount()).thenReturn(account);
        when(savingAccountAssembler.assembleFrom(ACCOUNT_ID, false)).thenReturn(account);
        when(savingsAccountChargeRepository.findOneWithNotFoundDetection(CHARGE_ID, ACCOUNT_ID)).thenReturn(charge);
        accountBelongsTo(".1.3.");
        login(".1.2.");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void siblingBranchUserCannotDeleteSavingsCharge() {
        assertThrows(NoAuthorizationException.class,
                () -> writeService.deleteSavingsAccountCharge(ACCOUNT_ID, CHARGE_ID, JsonCommand.from("{}")));
        verify(savingAccountRepositoryWrapper, never()).saveAndFlush(any());
    }

    @Test
    void ownBranchUserCanDeleteSavingsCharge() {
        accountBelongsTo(".1.2.5.");

        assertDoesNotThrow(() -> writeService.deleteSavingsAccountCharge(ACCOUNT_ID, CHARGE_ID, JsonCommand.from("{}")));
        verify(savingAccountRepositoryWrapper).saveAndFlush(account);
    }

    @Test
    void savingsChargeListIsOfficeScoped() {
        chargeReadService.retrieveSavingsAccountCharges(ACCOUNT_ID, "all");

        assertLastQueryIsOfficeScoped(jdbcTemplate);
    }

    @Test
    void savingsChargeDetailIsOfficeScoped() {
        chargeReadService.retrieveSavingsAccountChargeDetails(CHARGE_ID, ACCOUNT_ID);

        assertLastQueryIsOfficeScoped(jdbcTemplate);
    }

    private void accountBelongsTo(final String hierarchy) {
        final Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(hierarchy);
        when(account.office()).thenReturn(office);
    }

    private void login(final String hierarchy) {
        loginWithHierarchy(context, hierarchy);
        doAnswer(invocation -> {
            if (!invocation.<String>getArgument(0).startsWith(hierarchy)) {
                throw new NoAuthorizationException("The user doesn't have enough permissions to access the resource.");
            }
            return null;
        }).when(context).validateAccessRights(anyString());
    }
}
