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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class CommandSourceServiceSecurityTest {

    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private CommandSourceRepository commandSourceRepository;
    @Mock
    private ErrorHandler errorHandler;
    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @InjectMocks
    private CommandSourceService underTest;

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
    void evaluatesMakerCheckerConfigurationBeforeExecutingTheHandler() {
        final NewCommandSourceHandler handler = Mockito.mock(NewCommandSourceHandler.class);
        final JsonCommand command = JsonCommand.from("{}");
        final CommandSource commandSource = Mockito.mock(CommandSource.class);
        final AppUser user = Mockito.mock(AppUser.class);
        final CommandProcessingResult result = Mockito.mock(CommandProcessingResult.class);
        when(commandSource.getPermissionCode()).thenReturn("REPAYMENT_LOAN");
        when(configurationDomainService.isMakerCheckerEnabledForTask("REPAYMENT_LOAN")).thenReturn(false);
        when(handler.processCommand(command)).thenReturn(result);
        final InOrder inOrder = inOrder(configurationDomainService, handler);

        underTest.processCommand(handler, command, commandSource, user, false);

        inOrder.verify(configurationDomainService).isMakerCheckerEnabledForTask("REPAYMENT_LOAN");
        inOrder.verify(handler).processCommand(command);
    }
}
