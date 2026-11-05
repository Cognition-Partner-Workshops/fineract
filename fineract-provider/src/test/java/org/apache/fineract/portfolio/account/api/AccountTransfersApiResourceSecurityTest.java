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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.account.api;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.account.data.request.AccountTransSearchParam;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountTransfersApiResourceSecurityTest {

    private static final Long OFFICE_ID = 23L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser authenticatedUser;
    @Mock
    private OfficeScopeValidator officeScopeValidator;
    @Mock
    private AccountTransfersReadPlatformService accountTransfersReadPlatformService;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @InjectMocks
    private AccountTransfersApiResource underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(authenticatedUser);
    }

    @Test
    void rejectsTemplateLookupForOfficeOutsideUserScope() {
        final AccountTransSearchParam searchParam = new AccountTransSearchParam();
        searchParam.setToOfficeId(OFFICE_ID);
        doThrow(new NoAuthorizationException("outside office scope")).when(officeScopeValidator).validateOffice(OFFICE_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.template(searchParam));

        verifyNoInteractions(accountTransfersReadPlatformService);
    }

    @Test
    void permitsTemplateLookupForOfficeWithinUserScope() {
        final AccountTransSearchParam searchParam = new AccountTransSearchParam();
        searchParam.setToOfficeId(OFFICE_ID);
        when(accountTransfersReadPlatformService.retrieveTemplate(null, null, null, null, OFFICE_ID, null, null, null)).thenReturn(null);

        assertNull(underTest.template(searchParam));

        verify(officeScopeValidator).validateOffice(OFFICE_ID);
    }
}
