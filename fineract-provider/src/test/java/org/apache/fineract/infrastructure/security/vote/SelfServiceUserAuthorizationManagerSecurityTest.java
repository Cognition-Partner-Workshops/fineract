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
package org.apache.fineract.infrastructure.security.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;

class SelfServiceUserAuthorizationManagerSecurityTest {

    private final SelfServiceUserAuthorizationManager underTest = new SelfServiceUserAuthorizationManager();

    @Test
    void forwardedPrefixDoesNotClassifyRegularEndpointAsSelfService() throws Exception {
        final HttpServletRequest request = forwardedRequest("/api/v1/accounttransfers", "/self");
        final AppUser user = mock(AppUser.class);
        when(user.isSelfServiceUser()).thenReturn(true);

        assertEquals("/api/v1/accounttransfers", request.getServletPath());
        assertTrue(request.getRequestURI().contains("/self/"));
        assertFalse(underTest.check(() -> authentication(user), new RequestAuthorizationContext(request)).isGranted());
    }

    @Test
    void nonSelfServiceUserCannotUseActualSelfServiceEndpoint() throws Exception {
        final HttpServletRequest request = forwardedRequest("/api/v1/self/accounttransfers", "/proxy");
        final AppUser user = mock(AppUser.class);
        when(user.isSelfServiceUser()).thenReturn(false);

        assertFalse(underTest.check(() -> authentication(user), new RequestAuthorizationContext(request)).isGranted());
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
