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
package org.apache.fineract.infrastructure.security.filter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateReadPlatformService;
import org.apache.fineract.infrastructure.cache.service.CacheWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.AuthTenantDetailsService;
import org.apache.fineract.notification.service.UserNotificationService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.ForwardedHeaderFilter;

class TenantAwareBasicAuthenticationFilterSecurityTest {

    @Test
    void forwardedPrefixDoesNotClassifyRegularEndpointAsSelfService() throws Exception {
        final HttpServletRequest request = forwardedRequest("/api/v1/accounttransfers", "/self");
        final AppUser user = user(false);

        assertDoesNotThrow(() -> filter().onSuccessfulAuthentication(request, new MockHttpServletResponse(), authentication(user)));
    }

    @Test
    void nonSelfServiceUserCannotAuthenticateToActualSelfServiceEndpoint() throws Exception {
        final HttpServletRequest request = forwardedRequest("/api/v1/self/accounttransfers", "/proxy");
        final AppUser user = user(false);

        assertThrows(BadCredentialsException.class,
                () -> filter().onSuccessfulAuthentication(request, new MockHttpServletResponse(), authentication(user)));
    }

    private static TenantAwareBasicAuthenticationFilter filter() throws Exception {
        final UserNotificationService notifications = mock(UserNotificationService.class);
        when(notifications.hasUnreadUserNotifications(1L)).thenReturn(false);
        return new TenantAwareBasicAuthenticationFilter(mock(AuthenticationManager.class), mock(AuthenticationEntryPoint.class),
                mock(ToApiJsonSerializer.class), mock(ConfigurationDomainService.class), mock(CacheWritePlatformService.class),
                notifications, mock(AuthTenantDetailsService.class), mock(BusinessDateReadPlatformService.class));
    }

    private static AppUser user(final boolean selfServiceUser) {
        final AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(1L);
        when(user.isSelfServiceUser()).thenReturn(selfServiceUser);
        return user;
    }

    private static Authentication authentication(final AppUser user) {
        final Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(user);
        return authentication;
    }

    private static HttpServletRequest forwardedRequest(final String servletPath, final String forwardedPrefix) throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", servletPath);
        request.setServletPath(servletPath);
        request.addHeader("X-Forwarded-Prefix", forwardedPrefix);
        final AtomicReference<HttpServletRequest> forwardedRequest = new AtomicReference<>();
        final FilterChain filterChain = (wrappedRequest, response) -> forwardedRequest.set((HttpServletRequest) wrappedRequest);
        new ForwardedHeaderFilter().doFilter(request, new MockHttpServletResponse(), filterChain);
        return forwardedRequest.get();
    }
}
