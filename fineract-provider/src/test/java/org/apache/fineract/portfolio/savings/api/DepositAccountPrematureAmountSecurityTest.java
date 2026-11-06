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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.savings.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.google.gson.JsonParser;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.bulkimport.service.BulkImportWorkbookPopulatorService;
import org.apache.fineract.infrastructure.bulkimport.service.BulkImportWorkbookService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.api.JsonQuery;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.DepositsApiConstants;
import org.apache.fineract.portfolio.savings.data.DepositAccountData;
import org.apache.fineract.portfolio.savings.service.DepositAccountPreMatureCalculationPlatformService;
import org.apache.fineract.portfolio.savings.service.DepositAccountReadPlatformService;
import org.apache.fineract.portfolio.savings.service.FixedDepositAccountInterestCalculationService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountChargeReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepositAccountPrematureAmountSecurityTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private DepositAccountReadPlatformService depositAccountReadPlatformService;
    @Mock
    private DefaultToApiJsonSerializer<DepositAccountData> toApiJsonSerializer;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private SavingsAccountChargeReadPlatformService savingsAccountChargeReadPlatformService;
    @Mock
    private FromJsonHelper fromJsonHelper;
    @Mock
    private DepositAccountPreMatureCalculationPlatformService accountPreMatureCalculationPlatformService;
    @Mock
    private AccountAssociationsReadPlatformService accountAssociationsReadPlatformService;
    @Mock
    private BulkImportWorkbookService bulkImportWorkbookService;
    @Mock
    private BulkImportWorkbookPopulatorService bulkImportWorkbookPopulatorService;
    @Mock
    private FixedDepositAccountInterestCalculationService fixedDepositAccountInterestCalculationService;
    @Mock
    private SqlValidator sqlValidator;
    @Mock
    private UriInfo uriInfo;
    @InjectMocks
    private FixedDepositAccountsApiResource fixedDepositUnderTest;
    @InjectMocks
    private RecurringDepositAccountsApiResource recurringDepositUnderTest;

    @BeforeEach
    void setUp() {
        lenient().when(context.authenticatedUser()).thenReturn(user);
        lenient().when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        lenient().when(fromJsonHelper.parse("{}")).thenReturn(JsonParser.parseString("{}"));
    }

    @Test
    void rejectsFixedDepositPrematureAmountWithoutReadPermission() {
        deny(DepositsApiConstants.FIXED_DEPOSIT_ACCOUNT_RESOURCE_NAME);

        assertThrows(NoAuthorizationException.class,
                () -> fixedDepositUnderTest.handleCommands(18L, "calculatePrematureAmount", uriInfo, "{}"));
    }

    @Test
    void permitsFixedDepositPrematureAmountWithReadPermission() {
        assertDoesNotThrow(() -> fixedDepositUnderTest.handleCommands(18L, "calculatePrematureAmount", uriInfo, "{}"));

        verify(user).validateHasReadPermission(DepositsApiConstants.FIXED_DEPOSIT_ACCOUNT_RESOURCE_NAME);
        verify(accountPreMatureCalculationPlatformService).calculatePreMatureAmount(eq(18L), any(JsonQuery.class),
                eq(DepositAccountType.FIXED_DEPOSIT));
    }

    @Test
    void rejectsRecurringDepositPrematureAmountWithoutReadPermission() {
        deny(DepositsApiConstants.RECURRING_DEPOSIT_ACCOUNT_RESOURCE_NAME);

        assertThrows(NoAuthorizationException.class,
                () -> recurringDepositUnderTest.handleCommands(18L, "calculatePrematureAmount", uriInfo, "{}"));
    }

    @Test
    void permitsRecurringDepositPrematureAmountWithReadPermission() {
        assertDoesNotThrow(() -> recurringDepositUnderTest.handleCommands(18L, "calculatePrematureAmount", uriInfo, "{}"));

        verify(user).validateHasReadPermission(DepositsApiConstants.RECURRING_DEPOSIT_ACCOUNT_RESOURCE_NAME);
        verify(accountPreMatureCalculationPlatformService).calculatePreMatureAmount(eq(18L), any(JsonQuery.class),
                eq(DepositAccountType.RECURRING_DEPOSIT));
    }

    private void deny(final String resourceName) {
        lenient().doThrow(new NoAuthorizationException("read permission required")).when(user).validateHasReadPermission(eq(resourceName));
    }
}
