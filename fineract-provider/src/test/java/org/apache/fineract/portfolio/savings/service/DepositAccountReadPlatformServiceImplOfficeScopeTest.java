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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.PaginationParameters;
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
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Regression tests: recurring deposit account and transaction reads must be restricted to the caller's office
 * hierarchy.
 */
class DepositAccountReadPlatformServiceImplOfficeScopeTest {

    static final String USER_HIERARCHY = ".1.2.";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DepositAccountReadPlatformServiceImpl depositAccountReadService;
    @InjectMocks
    private SavingsAccountReadPlatformServiceImpl savingsAccountReadService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        loginWithHierarchy(context, USER_HIERARCHY);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void retrieveOneRecurringDepositAccountIsOfficeScoped() {
        depositAccountReadService.retrieveOne(DepositAccountType.RECURRING_DEPOSIT, 7L);

        assertLastQueryIsOfficeScoped(jdbcTemplate);
    }

    @Test
    void retrieveAllRecurringDepositAccountsIsOfficeScoped() {
        depositAccountReadService.retrieveAll(DepositAccountType.RECURRING_DEPOSIT, mock(PaginationParameters.class));

        assertLastQueryIsOfficeScoped(jdbcTemplate);
    }

    @Test
    void retrieveAllRecurringDepositTransactionsIsOfficeScoped() {
        depositAccountReadService.retrieveAllTransactions(DepositAccountType.RECURRING_DEPOSIT, 7L);

        assertLastQueryIsOfficeScoped(jdbcTemplate);
    }

    @Test
    void retrieveRecurringDepositTransactionTemplateIsOfficeScoped() {
        depositAccountReadService.retrieveRecurringAccountDepositTransactionTemplate(7L);

        assertLastQueryIsOfficeScoped(jdbcTemplate);
    }

    @Test
    void retrieveOneRecurringDepositTransactionIsOfficeScoped() {
        savingsAccountReadService.retrieveSavingsTransaction(7L, 8L, DepositAccountType.RECURRING_DEPOSIT);

        assertLastQueryIsOfficeScoped(jdbcTemplate);
    }

    static void loginWithHierarchy(final PlatformSecurityContext context, final String hierarchy) {
        final Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(hierarchy);
        final AppUser user = mock(AppUser.class);
        when(user.getOffice()).thenReturn(office);
        when(context.authenticatedUser()).thenReturn(user);
        when(context.getAuthenticatedUserIfPresent()).thenReturn(user);
    }

    static void assertLastQueryIsOfficeScoped(final JdbcTemplate jdbcTemplate) {
        final List<Invocation> invocations = new ArrayList<>(mockingDetails(jdbcTemplate).getInvocations());
        assertTrue(!invocations.isEmpty(), "expected a SQL query to be executed");
        final Invocation query = invocations.get(invocations.size() - 1);
        final String sql = (String) query.getArgument(0);
        final List<Object> parameters = new ArrayList<>();
        for (final Object argument : query.getArguments()) {
            if (argument instanceof Object[] array) {
                parameters.addAll(Arrays.asList(array));
            } else {
                parameters.add(argument);
            }
        }
        assertTrue(sql.contains("hierarchy like ?"), "query is not restricted by office hierarchy: " + sql);
        assertTrue(parameters.contains(USER_HIERARCHY + "%"), "query is not bound to the user's office hierarchy: " + parameters);
    }
}
