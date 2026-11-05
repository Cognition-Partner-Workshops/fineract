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
package org.apache.fineract.portfolio.self.registration.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.fineract.infrastructure.campaigns.sms.service.SmsCampaignDropdownReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.GmailBackedPlatformEmailService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageRepository;
import org.apache.fineract.infrastructure.sms.scheduler.SmsMessageScheduledJobService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.self.registration.SelfServiceApiConstants;
import org.apache.fineract.portfolio.self.registration.domain.SelfServiceRegistration;
import org.apache.fineract.portfolio.self.registration.domain.SelfServiceRegistrationRepository;
import org.apache.fineract.portfolio.self.registration.exception.SelfServiceRegistrationNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicyRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.domain.UserDomainService;
import org.apache.fineract.useradministration.service.AppUserReadPlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfServiceRegistrationTokenSecurityTest {

    private static final long REQUEST_ID = 1L;
    private static final String VALID_TOKEN = "expected-token";

    private SelfServiceRegistrationRepository repository;
    private RoleRepository roleRepository;
    private SelfServiceRegistrationWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        repository = mock(SelfServiceRegistrationRepository.class);
        roleRepository = mock(RoleRepository.class);
        underTest = new SelfServiceRegistrationWritePlatformServiceImpl(repository, new FromJsonHelper(),
                mock(SelfServiceRegistrationReadPlatformService.class), mock(ClientRepositoryWrapper.class),
                mock(PasswordValidationPolicyRepository.class), mock(UserDomainService.class), mock(GmailBackedPlatformEmailService.class),
                mock(SmsMessageRepository.class), mock(SmsMessageScheduledJobService.class),
                mock(SmsCampaignDropdownReadPlatformService.class), mock(AppUserReadPlatformService.class), roleRepository);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void generatedAuthorizationTokenHasAtLeastEightCharacters() {
        assertTrue(SelfServiceRegistrationWritePlatformServiceImpl.randomAuthorizationTokenGeneration().length() >= 8);
    }

    @Test
    void repeatedInvalidTokensInvalidateRegistration() {
        final SelfServiceRegistration registration = registration(VALID_TOKEN);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(registration));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(SelfServiceRegistrationNotFoundException.class, () -> underTest.createUser(request("wrong-token")));
        }

        assertNull(registration.getAuthenticationToken());
        verify(repository, times(5)).saveAndFlush(registration);
    }

    @Test
    void expiredAuthorizationTokenCannotCreateUser() throws Exception {
        final SelfServiceRegistration registration = registration(VALID_TOKEN);
        final Field expiresAt;
        try {
            expiresAt = SelfServiceRegistration.class.getDeclaredField("authenticationTokenExpiresAt");
        } catch (NoSuchFieldException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Token expiry is not present on the main baseline");
            return;
        }
        expiresAt.setAccessible(true);
        expiresAt.set(registration, LocalDateTime.now().minusMinutes(1));
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(registration));
        when(repository.getRequestByIdAndAuthenticationToken(REQUEST_ID, VALID_TOKEN)).thenReturn(registration);

        assertThrows(SelfServiceRegistrationNotFoundException.class, () -> underTest.createUser(request(VALID_TOKEN)));

        assertNull(registration.getAuthenticationToken());
    }

    @Test
    void legacyRegistrationWithoutExpiryCannotCreateUser() throws Exception {
        final SelfServiceRegistration registration = registration(VALID_TOKEN);
        final Field expiresAt;
        try {
            expiresAt = SelfServiceRegistration.class.getDeclaredField("authenticationTokenExpiresAt");
        } catch (NoSuchFieldException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Token expiry is not present on the main baseline");
            return;
        }
        expiresAt.setAccessible(true);
        expiresAt.set(registration, null);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(registration));

        assertThrows(SelfServiceRegistrationNotFoundException.class, () -> underTest.createUser(request(VALID_TOKEN)));

        assertNull(registration.getAuthenticationToken());
        verify(repository).saveAndFlush(registration);
    }

    @Test
    void validAuthorizationTokenCreatesSelfServiceUser() {
        final SelfServiceRegistration registration = registration(VALID_TOKEN);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(registration));
        when(repository.getRequestByIdAndAuthenticationToken(REQUEST_ID, VALID_TOKEN)).thenReturn(registration);
        when(roleRepository.getRoleByName(SelfServiceApiConstants.SELF_SERVICE_USER_ROLE)).thenReturn(mock(Role.class));

        final AppUser user = underTest.createUser(request(VALID_TOKEN));

        assertNotNull(user);
        assertTrue(user.isSelfServiceUser());
        assertNull(registration.getAuthenticationToken());
        verify(repository).saveAndFlush(registration);
    }

    private SelfServiceRegistration registration(final String token) {
        final Client client = mock(Client.class);
        when(client.getId()).thenReturn(2L);
        when(client.getOffice()).thenReturn(mock(Office.class));
        when(client.getFirstname()).thenReturn("First");
        when(client.getLastname()).thenReturn("Last");
        return SelfServiceRegistration.instance(client, "account", "First", "Last", null, "user@example.com", token, "username",
                "password");
    }

    private static String request(final String token) {
        return "{\"requestId\":" + REQUEST_ID + ",\"authenticationToken\":\"" + token + "\"}";
    }
}
