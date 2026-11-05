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
package org.apache.fineract.commands.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.commands.domain.CommandProcessingResultType;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.exception.UnsupportedCommandException;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.jobs.service.SchedulerJobRunnerReadService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Regression tests: a checker must not approve, reject or delete maker-checker entries that belong to an office outside
 * the checker's office hierarchy.
 */
class PortfolioCommandSourceWritePlatformServiceImplOfficeScopeTest {

    private static final Long ENTRY_ID = 1L;
    private static final Long BRANCH_A_OFFICE_ID = 2L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private CommandSourceRepository commandSourceRepository;
    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @Mock
    private CommandProcessingService processAndLogCommandService;
    @Mock
    private SchedulerJobRunnerReadService schedulerJobRunnerReadService;
    @Mock
    private ConfigurationDomainService configurationService;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;

    @InjectMocks
    private PortfolioCommandSourceWritePlatformServiceImpl underTest;

    private AutoCloseable mocks;
    private CommandSource entry;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        final AppUser maker = user(10L, ".1.2.");
        entry = new CommandSource();
        entry.setId(ENTRY_ID);
        entry.setStatus(CommandProcessingResultType.AWAITING_APPROVAL);
        entry.setActionName("REPAYMENT");
        entry.setEntityName("LOAN");
        entry.setMaker(maker);
        entry.setOfficeId(BRANCH_A_OFFICE_ID);
        when(commandSourceRepository.findById(ENTRY_ID)).thenReturn(Optional.of(entry));
        final Office branchA = mock(Office.class);
        when(branchA.getHierarchy()).thenReturn(".1.2.");
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(BRANCH_A_OFFICE_ID)).thenReturn(branchA);
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(anyLong())).thenReturn(branchA);
        when(configurationService.isSameMakerCheckerEnabled()).thenReturn(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void siblingBranchCheckerCannotDeleteEntry() {
        loginAs(user(20L, ".1.3."));

        assertThrows(NoAuthorizationException.class, () -> underTest.deleteEntry(ENTRY_ID));
        verify(commandSourceRepository, never()).deleteById(any());
    }

    @Test
    void siblingBranchCheckerCannotRejectEntry() {
        loginAs(user(20L, ".1.3."));

        assertThrows(NoAuthorizationException.class, () -> underTest.rejectEntry(ENTRY_ID));
        verify(commandSourceRepository, never()).save(any());
    }

    @Test
    void entryWithoutOfficeCanOnlyBeCheckedByHeadOffice() {
        entry.setOfficeId(null);
        loginAs(user(20L, ".1.3."));

        assertThrows(NoAuthorizationException.class, () -> underTest.deleteEntry(ENTRY_ID));
        verify(commandSourceRepository, never()).deleteById(any());
    }

    @Test
    void headOfficeCheckerCanDeleteBranchEntry() {
        loginAs(user(20L, "."));

        assertEquals(ENTRY_ID, underTest.deleteEntry(ENTRY_ID));
        verify(commandSourceRepository).deleteById(ENTRY_ID);
    }

    @Test
    void headOfficeCheckerCanDeleteEntryWithoutOffice() {
        entry.setOfficeId(null);
        loginAs(user(20L, "."));

        assertEquals(ENTRY_ID, underTest.deleteEntry(ENTRY_ID));
    }

    @Test
    void makerStillCannotCheckOwnEntry() {
        loginAs(entry.getMaker());

        assertThrows(UnsupportedCommandException.class, () -> underTest.deleteEntry(ENTRY_ID));
    }

    private void loginAs(final AppUser user) {
        when(context.authenticatedUser()).thenReturn(user);
        final String userHierarchy = user.getOffice().getHierarchy();
        doAnswer(invocation -> {
            if (!invocation.<String>getArgument(0).startsWith(userHierarchy)) {
                throw new NoAuthorizationException("The user doesn't have enough permissions to access the resource.");
            }
            return null;
        }).when(context).validateAccessRights(anyString());
    }

    private static AppUser user(final Long id, final String officeHierarchy) {
        final Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(officeHierarchy);
        final AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(id);
        when(user.getOffice()).thenReturn(office);
        when(user.isCheckerSuperUser()).thenReturn(false);
        return user;
    }
}
