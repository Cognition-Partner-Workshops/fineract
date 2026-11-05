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
package org.apache.fineract.portfolio.self.account.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.account.api.AccountTransfersApiResource;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.self.account.data.SelfAccountTemplateData;
import org.apache.fineract.portfolio.self.account.data.SelfAccountTransferDataValidator;
import org.apache.fineract.portfolio.self.account.exception.DailyTPTTransactionAmountLimitExceededException;
import org.apache.fineract.portfolio.self.account.service.SelfAccountTransferReadService;
import org.apache.fineract.portfolio.self.account.service.SelfBeneficiariesTPTReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SelfAccountTransferApiResourceSecurityTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2025, 2, 1);
    private static final LocalDate CALLER_DATE = LocalDate.of(2000, 1, 1);

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final SelfBeneficiariesTPTReadPlatformService beneficiaryReadService = mock(SelfBeneficiariesTPTReadPlatformService.class);
    private final ConfigurationDomainService configurationDomainService = mock(ConfigurationDomainService.class);
    private final AccountTransfersReadPlatformService accountTransfersReadPlatformService = mock(AccountTransfersReadPlatformService.class);
    private final SelfAccountTransferApiResource underTest = new SelfAccountTransferApiResource(context,
            mock(DefaultToApiJsonSerializer.class), mock(AccountTransfersApiResource.class), mock(SelfAccountTransferReadService.class),
            mock(org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper.class), mock(SelfAccountTransferDataValidator.class),
            beneficiaryReadService, configurationDomainService, accountTransfersReadPlatformService);

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
        final AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(17L);
        when(context.authenticatedUser()).thenReturn(user);
        when(beneficiaryReadService.getTransferLimit(17L, 202L, 2)).thenReturn(null);
        when(configurationDomainService.isDailyTPTLimitEnabled()).thenReturn(true);
        when(configurationDomainService.getDailyTPTLimit()).thenReturn(100L);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void nullDailyTotalStillEnforcesLimit() {
        when(accountTransfersReadPlatformService.getTotalTransactionAmount(101L, 2, BUSINESS_DATE)).thenReturn(null);

        assertThrows(DailyTPTTransactionAmountLimitExceededException.class,
                () -> ReflectionTestUtils.invokeMethod(underTest, "checkForLimits", params(new BigDecimal("101"))));
    }

    @Test
    void dailyLimitUsesBusinessDateAndAllowsAmountWithinLimit() {
        when(accountTransfersReadPlatformService.getTotalTransactionAmount(101L, 2, BUSINESS_DATE)).thenReturn(new BigDecimal("60"));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(underTest, "checkForLimits", params(new BigDecimal("40"))));
        verify(accountTransfersReadPlatformService).getTotalTransactionAmount(101L, 2, BUSINESS_DATE);
    }

    private static Map<String, Object> params(final BigDecimal amount) {
        return Map.of("fromAccount", new SelfAccountTemplateData(101L, 2, 1L, 1L), "toAccount",
                new SelfAccountTemplateData(202L, 2, 1L, 1L), "transactionDate", CALLER_DATE, "transactionAmount", amount);
    }
}
