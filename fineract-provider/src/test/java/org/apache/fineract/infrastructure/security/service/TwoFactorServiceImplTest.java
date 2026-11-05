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
package org.apache.fineract.infrastructure.security.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.data.OTPDeliveryMethod;
import org.apache.fineract.infrastructure.security.data.OTPRequest;
import org.apache.fineract.infrastructure.security.domain.OTPRequestRepository;
import org.apache.fineract.infrastructure.security.domain.TFAccessTokenRepository;
import org.apache.fineract.infrastructure.security.exception.OTPTokenInvalidException;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageRepository;
import org.apache.fineract.infrastructure.sms.scheduler.SmsMessageScheduledJobService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TwoFactorServiceImplTest {

    private static final long USER_ID = 99001L;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String VALID_TOKEN = "SYNTHETIC-OTP-CODE";

    private final AccessTokenGenerationService accessTokenGenerationService = mock(AccessTokenGenerationService.class);
    private final PlatformEmailService emailService = mock(PlatformEmailService.class);
    private final SmsMessageScheduledJobService smsMessageScheduledJobService = mock(SmsMessageScheduledJobService.class);
    private final OTPRequestRepository otpRequestRepository = new OTPRequestRepository();
    private final TFAccessTokenRepository tfAccessTokenRepository = mock(TFAccessTokenRepository.class);
    private final SmsMessageRepository smsMessageRepository = mock(SmsMessageRepository.class);
    private final TwoFactorConfigurationService configurationService = mock(TwoFactorConfigurationService.class);
    private final AppUser user = mock(AppUser.class);
    private final TwoFactorServiceImpl underTest = new TwoFactorServiceImpl(accessTokenGenerationService, emailService,
            smsMessageScheduledJobService, otpRequestRepository, tfAccessTokenRepository, smsMessageRepository, configurationService);

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "test", "Test Tenant", "UTC", null));
        when(user.getId()).thenReturn(USER_ID);
        when(accessTokenGenerationService.generateRandomToken()).thenReturn("SYNTHETIC-ACCESS-TOKEN");
        when(configurationService.getAccessTokenLiveTime()).thenReturn(60);
        otpRequestRepository.deleteOTPRequestForUser(user);
    }

    @AfterEach
    void tearDown() {
        otpRequestRepository.deleteOTPRequestForUser(user);
    }

    @Test
    void failedOtpAttemptsInvalidateTokenAtLimit() {
        addValidRequest();

        for (int attempt = 1; attempt <= MAX_FAILED_ATTEMPTS; attempt++) {
            String invalidToken = "SYNTHETIC-WRONG-CODE-" + attempt;
            assertThrows(OTPTokenInvalidException.class, () -> underTest.createAccessTokenFromOTP(user, invalidToken));
        }

        assertThrows(OTPTokenInvalidException.class, () -> underTest.createAccessTokenFromOTP(user, VALID_TOKEN));
    }

    @Test
    void validOtpSucceedsBeforeAttemptLimit() {
        addValidRequest();

        assertNotNull(underTest.createAccessTokenFromOTP(user, VALID_TOKEN));
    }

    private void addValidRequest() {
        OTPDeliveryMethod deliveryMethod = new OTPDeliveryMethod().setName("email").setTarget("synthetic@example.test");
        otpRequestRepository.addOTPRequest(user, OTPRequest.create(VALID_TOKEN, 3_600, false, deliveryMethod));
    }
}
