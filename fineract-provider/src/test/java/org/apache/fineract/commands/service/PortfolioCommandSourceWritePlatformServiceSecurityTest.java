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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.google.gson.JsonSyntaxException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fineract.commands.domain.CommandProcessingResultType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    void malformedApproveCommandJsonReleasesClaim() {
        final Long commandId = 43L;
        final CommandSource commandSource = approvableCommand("{\"invalid\":");
        final AtomicInteger claimState = new AtomicInteger();
        final CommandSourceRepository statefulRepository = Mockito.mock(CommandSourceRepository.class, invocation -> {
            if ("claimForApproval".equals(invocation.getMethod().getName())) {
                return claimState.compareAndSet(0, 1) ? 1 : 0;
            }
            if ("releaseApprovalClaim".equals(invocation.getMethod().getName())) {
                return claimState.compareAndSet(1, 0) ? 1 : 0;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        ReflectionTestUtils.setField(underTest, "commandSourceRepository", statefulRepository);
        when(statefulRepository.findById(commandId)).thenReturn(Optional.of(commandSource));
        checkerSetup();

        assertThrows(JsonSyntaxException.class, () -> underTest.approveEntry(commandId));

        assertAll(
                () -> verify(statefulRepository, times(1)).releaseApprovalClaim(eq(commandId), any(AppUser.class), anyString(),
                        eq(CommandProcessingResultType.AWAITING_APPROVAL.getValue())),
                () -> assertEquals(commandId, assertDoesNotThrow(() -> underTest.rejectEntry(commandId))));
    }

    @Test
    void deleteFailureReleasesClaim() {
        final Long commandId = 44L;
        final CommandSource commandSource = awaitingCommand("REPAYMENT_LOAN");
        when(commandSourceRepository.findById(commandId)).thenReturn(Optional.of(commandSource));
        checkerSetup();
        doThrow(new IllegalStateException("delete failed")).when(commandSourceRepository).deleteById(commandId);

        assertThrows(IllegalStateException.class, () -> underTest.deleteEntry(commandId));

        verifyClaimReleased(commandId);
    }

    @Test
    void rollbackAfterSuccessfulApprovalReleasesClaim() {
        final Long commandId = 45L;
        final CommandSource commandSource = approvableCommand("{}");
        when(commandSourceRepository.findById(commandId)).thenReturn(Optional.of(commandSource));
        checkerSetup();
        final CommandProcessingResult expected = Mockito.mock(CommandProcessingResult.class);
        when(processAndLogCommandService.executeCommand(any(), any(), eq(true))).thenReturn(expected);
        TransactionSynchronizationManager.initSynchronization();

        try {
            underTest.approveEntry(commandId);

            final List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        verifyClaimReleased(commandId);
    }

    @Test
    void commitAfterSuccessfulApprovalKeepsClaim() {
        final Long commandId = 46L;
        final CommandSource commandSource = approvableCommand("{}");
        when(commandSourceRepository.findById(commandId)).thenReturn(Optional.of(commandSource));
        checkerSetup();
        final CommandProcessingResult expected = Mockito.mock(CommandProcessingResult.class);
        when(processAndLogCommandService.executeCommand(any(), any(), eq(true))).thenReturn(expected);
        TransactionSynchronizationManager.initSynchronization();

        try {
            underTest.approveEntry(commandId);

            final List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        verify(commandSourceRepository, never()).releaseApprovalClaim(eq(commandId), any(AppUser.class), anyString(),
                eq(CommandProcessingResultType.AWAITING_APPROVAL.getValue()));
    }

    @Test
    void rejectSaveFailureReleasesClaim() {
        final Long commandId = 47L;
        final CommandSource commandSource = awaitingCommand("REPAYMENT_LOAN");
        when(commandSourceRepository.findById(commandId)).thenReturn(Optional.of(commandSource));
        checkerSetup();
        doThrow(new IllegalStateException("save failed")).when(commandSourceRepository).save(commandSource);

        assertThrows(IllegalStateException.class, () -> underTest.rejectEntry(commandId));

        verifyClaimReleased(commandId);
    }

    @Test
    void synchronizationDefersFailureReleaseUntilRollbackAndOnlyOnce() {
        final Long commandId = 48L;
        final CommandSource commandSource = approvableCommand("{\"invalid\":");
        when(commandSourceRepository.findById(commandId)).thenReturn(Optional.of(commandSource));
        checkerSetup();
        final ArgumentCaptor<OffsetDateTime> claimedAtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        final ArgumentCaptor<String> claimTokenCaptor = ArgumentCaptor.forClass(String.class);
        TransactionSynchronizationManager.initSynchronization();

        try {
            assertThrows(JsonSyntaxException.class, () -> underTest.approveEntry(commandId));

            verify(commandSourceRepository, never()).releaseApprovalClaim(any(), any(), anyString(), any());
            verify(commandSourceRepository).claimForApproval(eq(commandId), any(AppUser.class), claimedAtCaptor.capture(),
                    claimTokenCaptor.capture(), eq(CommandProcessingResultType.AWAITING_APPROVAL.getValue()));
            assertTrue(claimedAtCaptor.getValue().getNano() % 1_000 == 0);

            final List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            synchronizations.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(commandSourceRepository, times(1)).releaseApprovalClaim(eq(commandId), any(AppUser.class),
                    eq(claimTokenCaptor.getValue()), eq(CommandProcessingResultType.AWAITING_APPROVAL.getValue()));
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    void secondApprovalAfterClaimIsRejected_protocol() {
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

    private CommandSource approvableCommand(final String commandAsJson) {
        final CommandSource commandSource = awaitingCommand("APPROVE_LOAN");
        when(commandSource.getActionName()).thenReturn("APPROVE");
        when(commandSource.getEntityName()).thenReturn("LOAN");
        when(commandSource.getCommandAsJson()).thenReturn(commandAsJson);
        return commandSource;
    }

    private void verifyClaimReleased(final Long commandId) {
        verify(commandSourceRepository, times(1)).releaseApprovalClaim(eq(commandId), any(AppUser.class), anyString(),
                eq(CommandProcessingResultType.AWAITING_APPROVAL.getValue()));
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
