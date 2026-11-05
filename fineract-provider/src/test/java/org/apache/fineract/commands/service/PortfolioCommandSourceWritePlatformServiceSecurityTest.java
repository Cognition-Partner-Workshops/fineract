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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.exception.CommandNotAwaitingApprovalException;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.jobs.service.SchedulerJobRunnerReadService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class PortfolioCommandSourceWritePlatformServiceSecurityTest {

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
    private OfficeScopeValidator officeScopeValidator;
    @InjectMocks
    private PortfolioCommandSourceWritePlatformServiceImpl underTest;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(fromApiJsonHelper.parse(anyString())).thenAnswer(invocation -> JsonParser.parseString(invocation.getArgument(0)));
        commandSourceRepository = commandSourceRepositoryWithClaimResult(1);
        ReflectionTestUtils.setField(underTest, "commandSourceRepository", commandSourceRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void rejectsLoanCommandOutsideUserOfficeBeforeExecution() {
        final CommandWrapper wrapper = new CommandWrapperBuilder().loanRepaymentTransaction(23L).withJson("{}").build();
        authenticatedUserFor(wrapper, 7L);
        doThrow(new NoAuthorizationException("outside office scope")).when(officeScopeValidator).validate(wrapper);

        assertThrows(NoAuthorizationException.class, () -> underTest.logCommandSource(wrapper));

        verify(processAndLogCommandService, never()).executeCommand(any(), any(), anyBoolean());
    }

    @Test
    void permitsLoanCommandWithinUserOffice() {
        final CommandWrapper wrapper = new CommandWrapperBuilder().loanRepaymentTransaction(23L).withJson("{}").build();
        authenticatedUserFor(wrapper, 7L);
        final CommandProcessingResult expected = Mockito.mock(CommandProcessingResult.class);
        when(processAndLogCommandService.executeCommand(eq(wrapper), any(JsonCommand.class), eq(false))).thenReturn(expected);

        assertSame(expected, underTest.logCommandSource(wrapper));

        verify(processAndLogCommandService).executeCommand(eq(wrapper), any(JsonCommand.class), eq(false));
    }

    @Test
    void rejectsSelfUpdateOfRolesOfficeOrSelfServiceStatus() {
        final CommandWrapper wrapper = new CommandWrapperBuilder().updateUser(7L)
                .withJson("{\"roles\":[1],\"officeId\":2,\"isSelfServiceUser\":true}").build();
        authenticatedUserFor(wrapper, 7L);

        assertThrows(NoAuthorizationException.class, () -> underTest.logCommandSource(wrapper));

        verify(processAndLogCommandService, never()).executeCommand(any(), any(), anyBoolean());
    }

    @Test
    void allowsOwnPasswordOnlyUpdate() {
        final CommandWrapper wrapper = new CommandWrapperBuilder().updateUser(7L)
                .withJson("{\"password\":\"synthetic-password\",\"repeatPassword\":\"synthetic-password\"}").build();
        authenticatedUserFor(wrapper, 7L);
        final CommandProcessingResult expected = Mockito.mock(CommandProcessingResult.class);
        when(processAndLogCommandService.executeCommand(eq(wrapper), any(JsonCommand.class), eq(true))).thenReturn(expected);

        assertSame(expected, underTest.logCommandSource(wrapper));

        verify(processAndLogCommandService).executeCommand(eq(wrapper), any(JsonCommand.class), eq(true));
    }

    @Test
    void rejectsCheckerOutsideCommandOfficeBeforeDeletingPendingCommand() {
        final Long commandId = 41L;
        final CommandSource commandSource = awaitingCommand("REPAYMENT_LOAN");
        when(commandSourceRepository.findById(commandId)).thenReturn(Optional.of(commandSource));
        checkerSetup();
        doThrow(new NoAuthorizationException("outside office scope")).when(officeScopeValidator).validate(commandSource);

        assertThrows(NoAuthorizationException.class, () -> underTest.deleteEntry(commandId));

        verify(commandSourceRepository, never()).deleteById(commandId);
    }

    @Test
    void permitsCheckerWithinCommandOffice() {
        final Long commandId = 41L;
        final CommandSource commandSource = awaitingCommand("REPAYMENT_LOAN");
        when(commandSourceRepository.findById(commandId)).thenReturn(Optional.of(commandSource));
        checkerSetup();

        assertEquals(commandId, underTest.deleteEntry(commandId));

        verify(commandSourceRepository).deleteById(commandId);
    }

    @Test
    void onlyOneConcurrentApprovalExecutesTheCommand() {
        final Long commandId = 42L;
        final CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.isAwaitingApproval()).thenReturn(true);
        when(commandSource.getPermissionCode()).thenReturn("APPROVE_LOAN");
        when(commandSource.getActionName()).thenReturn("APPROVE");
        when(commandSource.getEntityName()).thenReturn("LOAN");
        when(commandSource.getCommandAsJson()).thenReturn("{}");
        when(commandSourceRepository.findById(commandId)).thenReturn(Optional.of(commandSource));
        checkerSetup();
        final AtomicInteger claims = new AtomicInteger();
        final CommandSourceRepository claimRepository = Mockito.mock(CommandSourceRepository.class, invocation -> {
            if ("claimForApproval".equals(invocation.getMethod().getName())) {
                return claims.getAndIncrement() == 0 ? 1 : 0;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        when(claimRepository.findById(commandId)).thenReturn(Optional.of(commandSource));
        ReflectionTestUtils.setField(underTest, "commandSourceRepository", claimRepository);
        when(processAndLogCommandService.executeCommand(any(), any(), eq(true))).thenReturn(Mockito.mock(CommandProcessingResult.class));

        underTest.approveEntry(commandId);

        assertThrows(CommandNotAwaitingApprovalException.class, () -> underTest.approveEntry(commandId));

        verify(processAndLogCommandService, times(1)).executeCommand(any(), any(), eq(true));
    }

    private void authenticatedUserFor(final CommandWrapper wrapper, final Long userId) {
        final AppUser user = Mockito.mock(AppUser.class);
        when(context.authenticatedUser(wrapper)).thenReturn(user);
        when(user.getId()).thenReturn(userId);
    }

    private void checkerSetup() {
        final AppUser checker = Mockito.mock(AppUser.class);
        when(context.authenticatedUser()).thenReturn(checker);
        when(configurationService.isSameMakerCheckerEnabled()).thenReturn(true);
    }

    private CommandSource awaitingCommand(final String permissionCode) {
        final CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.isAwaitingApproval()).thenReturn(true);
        when(commandSource.getPermissionCode()).thenReturn(permissionCode);
        return commandSource;
    }

    private CommandSourceRepository commandSourceRepositoryWithClaimResult(final int claimResult) {
        return Mockito.mock(CommandSourceRepository.class, invocation -> {
            if ("claimForApproval".equals(invocation.getMethod().getName())) {
                return claimResult;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }
}
