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
package org.apache.fineract.portfolio.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.guarantor.data.ObligeeData;
import org.apache.fineract.portfolio.loanaccount.guarantor.service.GuarantorReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientsApiResourceSecurityTest {

    private static final Long CLIENT_ID = 23L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser authenticatedUser;
    @Mock
    private OfficeScopeValidator officeScopeValidator;
    @Mock
    private ClientReadPlatformService clientReadPlatformService;
    @Mock
    private GuarantorReadPlatformService guarantorReadPlatformService;
    @Mock
    private ToApiJsonSerializer<ClientData> toApiJsonSerializer;
    @InjectMocks
    private ClientsApiResource underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(authenticatedUser);
    }

    @Test
    void rejectsObligeeReadOutsideClientOfficeBeforeLoadingDetails() {
        doThrow(new NoAuthorizationException("outside office scope")).when(officeScopeValidator).validateClient(CLIENT_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.retrieveObligeeDetails(CLIENT_ID, null));

        verifyNoInteractions(guarantorReadPlatformService);
    }

    @Test
    void permitsObligeeReadWithinClientOffice() {
        when(guarantorReadPlatformService.retrieveObligeeDetails(CLIENT_ID)).thenReturn(List.<ObligeeData>of());
        when(toApiJsonSerializer.serialize(List.of())).thenReturn("[]");

        assertEquals("[]", underTest.retrieveObligeeDetails(CLIENT_ID, null));

        verify(officeScopeValidator).validateClient(CLIENT_ID);
    }

    @Test
    void rejectsTransferProposalDateReadOutsideClientOffice() {
        doThrow(new NoAuthorizationException("outside office scope")).when(officeScopeValidator).validateClient(CLIENT_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.retrieveTransferTemplate(CLIENT_ID, null));

        verifyNoInteractions(clientReadPlatformService);
    }

    @Test
    void permitsTransferProposalDateReadWithinClientOffice() {
        final LocalDate date = LocalDate.of(2025, 1, 1);
        when(clientReadPlatformService.retrieveClientTransferProposalDate(CLIENT_ID)).thenReturn(date);
        when(toApiJsonSerializer.serialize(date)).thenReturn("\"2025-01-01\"");

        assertEquals("\"2025-01-01\"", underTest.retrieveTransferTemplate(CLIENT_ID, null));

        verify(officeScopeValidator).validateClient(CLIENT_ID);
    }
}
