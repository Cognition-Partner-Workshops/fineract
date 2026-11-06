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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fineract.infrastructure.dataqueries.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableReadService;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableWriteService;
import org.apache.fineract.infrastructure.dataqueries.service.GenericDataService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DatatablesApiResourceSecurityTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private GenericDataService genericDataService;
    @Mock
    private DatatableReadService datatableReadService;
    @Mock
    private DatatableWriteService datatableWriteService;
    @Mock
    private ToApiJsonSerializer<GenericResultsetData> toApiJsonSerializer;
    @Mock
    private org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @InjectMocks
    private DatatablesApiResource underTest;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void requiresDeregisterPermission() {
        final AppUser user = org.mockito.Mockito.mock(AppUser.class);
        when(context.authenticatedUser()).thenReturn(user);
        doThrow(new NoAuthorizationException("missing deregister permission")).when(user).validateHasPermissionTo("DEREGISTER_DATATABLE");

        assertThrows(NoAuthorizationException.class, () -> underTest.deregisterDatatable("test_table"));
    }

    @Test
    void authorizedUserCanDeregisterDatatable() {
        final AppUser user = org.mockito.Mockito.mock(AppUser.class);
        when(context.authenticatedUser()).thenReturn(user);

        assertDoesNotThrow(() -> underTest.deregisterDatatable("test_table"));

        verify(datatableWriteService).deregisterDatatable("test_table");
    }
}
