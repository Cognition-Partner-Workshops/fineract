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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.account.api.AccountTransfersApiResource;
import org.apache.fineract.portfolio.account.data.request.AccountTransferRequest;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.self.account.data.SelfAccountTemplateData;
import org.apache.fineract.portfolio.self.account.data.SelfAccountTransferData;
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
    private static final LocalDate BACKDATED_DATE = BUSINESS_DATE.minusDays(1);

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final DefaultToApiJsonSerializer<SelfAccountTransferData> serializer = mock(DefaultToApiJsonSerializer.class);
    private final AccountTransfersApiResource accountTransfersApiResource = mock(AccountTransfersApiResource.class);
    private final SelfAccountTransferDataValidator dataValidator = mock(SelfAccountTransferDataValidator.class);
    private final SelfBeneficiariesTPTReadPlatformService beneficiaryReadService = mock(SelfBeneficiariesTPTReadPlatformService.class);
    private final ConfigurationDomainService configurationDomainService = mock(ConfigurationDomainService.class);
    private final AccountTransfersReadPlatformService accountTransfersReadPlatformService = mock(AccountTransfersReadPlatformService.class);
    private final SelfAccountTransferApiResource underTest = new SelfAccountTransferApiResource(context, serializer,
            accountTransfersApiResource, mock(SelfAccountTransferReadService.class),
            mock(org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper.class), dataValidator, beneficiaryReadService,
            configurationDomainService, accountTransfersReadPlatformService);

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
        AccountTransferRequest request = mock(AccountTransferRequest.class);
        CommandProcessingResult result = mock(CommandProcessingResult.class);
        when(serializer.serialize(request)).thenReturn("synthetic-request");
        when(dataValidator.validateCreate("tpt", "synthetic-request")).thenReturn(params(new BigDecimal("40"), BUSINESS_DATE));
        when(accountTransfersApiResource.create(request)).thenReturn(result);

        assertSame(result, underTest.create("tpt", request));
        verify(accountTransfersReadPlatformService).getTotalTransactionAmount(101L, 2, BUSINESS_DATE);
        verify(accountTransfersApiResource).create(request);
    }

    @Test
    void backdatedTransferIsRejectedWithBusinessDateErrorBeforeLimitCheck() {
        when(accountTransfersReadPlatformService.getTotalTransactionAmount(101L, 2, BACKDATED_DATE)).thenReturn(BigDecimal.ZERO);
        when(accountTransfersReadPlatformService.getTotalTransactionAmount(101L, 2, BUSINESS_DATE)).thenReturn(new BigDecimal("90"));
        AccountTransferRequest request = mock(AccountTransferRequest.class);
        when(serializer.serialize(request)).thenReturn("synthetic-request");
        when(dataValidator.validateCreate("tpt", "synthetic-request")).thenReturn(params(new BigDecimal("20"), BACKDATED_DATE));

        AbstractPlatformDomainRuleException exception = assertThrows(AbstractPlatformDomainRuleException.class,
                () -> underTest.create("tpt", request));

        assertEquals("error.msg.selfservice.tpt.transaction.date.must.be.business.date", exception.getGlobalisationMessageCode());
    }

    private static Map<String, Object> params(final BigDecimal amount) {
        return params(amount, BACKDATED_DATE);
    }

    private static Map<String, Object> params(final BigDecimal amount, final LocalDate transactionDate) {
        return Map.of("fromAccount", new SelfAccountTemplateData(101L, 2, 1L, 1L), "toAccount",
                new SelfAccountTemplateData(202L, 2, 1L, 1L), "transactionDate", transactionDate, "transactionAmount", amount);
    }
}
