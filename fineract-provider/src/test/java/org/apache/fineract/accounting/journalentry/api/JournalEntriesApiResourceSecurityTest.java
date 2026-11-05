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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fineract.accounting.journalentry.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.accounting.journalentry.service.JournalEntryReadPlatformService;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.bulkimport.service.BulkImportWorkbookPopulatorService;
import org.apache.fineract.infrastructure.bulkimport.service.BulkImportWorkbookService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JournalEntriesApiResourceSecurityTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JournalEntryReadPlatformService journalEntryReadPlatformService;
    @Mock
    private DefaultToApiJsonSerializer<Object> apiJsonSerializerService;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @Mock
    private BulkImportWorkbookService bulkImportWorkbookService;
    @Mock
    private BulkImportWorkbookPopulatorService bulkImportWorkbookPopulatorService;
    @Mock
    private SqlValidator sqlValidator;
    @Mock
    private AppUser user;
    @Mock
    private UriInfo uriInfo;
    @InjectMocks
    private JournalEntriesApiResource underTest;

    @Test
    void rejectsProvisioningJournalEntryReadWithoutReadPermission() {
        when(context.authenticatedUser()).thenReturn(user);
        doThrow(new NoAuthorizationException("read permission required")).when(user).validateHasReadPermission("JOURNALENTRY");

        assertThrows(NoAuthorizationException.class, () -> underTest.retrieveJournalEntries(0, 10, 18L, uriInfo));
    }

    @Test
    void permitsProvisioningJournalEntryReadWithReadPermission() {
        when(context.authenticatedUser()).thenReturn(user);

        assertDoesNotThrow(() -> underTest.retrieveJournalEntries(0, 10, 18L, uriInfo));
    }

    @Test
    void rejectsJournalEntryTemplateReadWithoutReadPermission() {
        when(context.authenticatedUser()).thenReturn(user);
        doThrow(new NoAuthorizationException("read permission required")).when(user).validateHasReadPermission("JOURNALENTRY");

        assertThrows(NoAuthorizationException.class, () -> underTest.getJournalEntriesTemplate(18L, "dd/MM/yyyy"));
    }

    @Test
    void permitsJournalEntryTemplateReadWithReadPermission() {
        org.mockito.Mockito.lenient().when(context.authenticatedUser()).thenReturn(user);

        assertDoesNotThrow(() -> underTest.getJournalEntriesTemplate(18L, "dd/MM/yyyy"));
    }
}
