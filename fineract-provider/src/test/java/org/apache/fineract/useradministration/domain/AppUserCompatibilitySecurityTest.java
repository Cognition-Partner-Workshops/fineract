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
package org.apache.fineract.useradministration.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.commands.domain.CommandProcessingResultType;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.service.CommandProcessingService;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformServiceImpl;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.jobs.service.SchedulerJobRunnerReadService;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class AppUserCompatibilitySecurityTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private CommandSourceRepository commandSourceRepository;
    private CommandSourceRepository claimRepository;
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

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        claimRepository = Mockito.mock(CommandSourceRepository.class, invocation -> {
            if ("claimForApproval".equals(invocation.getMethod().getName())) {
                return 1;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        ReflectionTestUtils.setField(underTest, "commandSourceRepository", claimRepository);
        when(fromApiJsonHelper.parse(anyString())).thenAnswer(invocation -> JsonParser.parseString(invocation.getArgument(0)));
    }

    @Test
    void allFunctionsStillMakesTheUserACheckerSuperUser() {
        final AppUser user = userWithPermission("ALL_FUNCTIONS", 9L);

        assertTrue(user.isCheckerSuperUser());
    }

    @Test
    void allFunctionsCanApproveItsOwnMakerCommandWhenSameMakerCheckerIsDisabled() {
        final AppUser user = userWithPermission("ALL_FUNCTIONS", 9L);
        final CommandSource commandSource = CommandSource.builder().actionName("APPROVE").entityName("LOAN").maker(user).commandAsJson("{}")
                .status(CommandProcessingResultType.AWAITING_APPROVAL.getValue()).build();
        ReflectionTestUtils.setField(commandSource, "id", 51L);
        when(claimRepository.findById(51L)).thenReturn(Optional.of(commandSource));
        when(context.authenticatedUser()).thenReturn(user);
        when(configurationService.isSameMakerCheckerEnabled()).thenReturn(false);
        when(processAndLogCommandService.executeCommand(any(), any(JsonCommand.class), eq(true)))
                .thenReturn(Mockito.mock(CommandProcessingResult.class));

        assertDoesNotThrow(() -> underTest.approveEntry(51L));
    }

    private AppUser userWithPermission(final String permission, final Long id) {
        final AppUser user = new AppUser();
        final Role role = Mockito.mock(Role.class);
        when(role.hasPermissionTo(permission)).thenReturn(true);
        ReflectionTestUtils.setField(user, "roles", Set.of(role));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
