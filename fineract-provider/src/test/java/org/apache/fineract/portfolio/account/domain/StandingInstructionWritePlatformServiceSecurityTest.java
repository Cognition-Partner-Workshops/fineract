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
package org.apache.fineract.portfolio.account.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.portfolio.account.data.StandingInstructionDataValidator;
import org.apache.fineract.portfolio.account.exception.StandingInstructionNotFoundException;
import org.apache.fineract.portfolio.account.service.StandingInstructionWritePlatformServiceImpl;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class StandingInstructionWritePlatformServiceSecurityTest {

    private static final Long STANDING_INSTRUCTION_ID = 41L;
    private static final Long SOURCE_SAVINGS_ID = 23L;

    @Mock
    private StandingInstructionDataValidator standingInstructionDataValidator;
    @Mock
    private StandingInstructionAssembler standingInstructionAssembler;
    @Mock
    private AccountTransferDetailRepository accountTransferDetailRepository;
    @Mock
    private StandingInstructionRepository standingInstructionRepository;
    @Mock
    private OfficeScopeValidator officeScopeValidator;
    @InjectMocks
    private StandingInstructionWritePlatformServiceImpl underTest;

    private AutoCloseable mocks;
    private JsonCommand command;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        command = JsonCommand.from("{}");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void rejectsUpdateOutsideSourceAccountOfficeBeforeMutation() {
        final AccountTransferStandingInstruction instruction = standingInstruction();
        when(standingInstructionRepository.findById(STANDING_INSTRUCTION_ID)).thenReturn(Optional.of(instruction));
        doReturn(Map.of()).when(instruction).update(command);
        doThrow(new NoAuthorizationException("outside office scope")).when(officeScopeValidator).validateSavings(SOURCE_SAVINGS_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.update(STANDING_INSTRUCTION_ID, command));

        verify(instruction, never()).update(command);
    }

    @Test
    void permitsUpdateWithinSourceAccountOffice() {
        final AccountTransferStandingInstruction instruction = standingInstruction();
        when(standingInstructionRepository.findById(STANDING_INSTRUCTION_ID)).thenReturn(Optional.of(instruction));
        doReturn(Map.of()).when(instruction).update(command);

        assertDoesNotThrow(() -> underTest.update(STANDING_INSTRUCTION_ID, command));

        verify(officeScopeValidator).validateSavings(SOURCE_SAVINGS_ID);
        verify(instruction).update(command);
    }

    @Test
    void rejectsDeleteOutsideSourceAccountOfficeBeforeMutation() {
        final AccountTransferStandingInstruction instruction = standingInstruction();
        when(standingInstructionRepository.findById(STANDING_INSTRUCTION_ID)).thenReturn(Optional.of(instruction));
        doThrow(new NoAuthorizationException("outside office scope")).when(officeScopeValidator).validateSavings(SOURCE_SAVINGS_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.delete(STANDING_INSTRUCTION_ID));

        verify(instruction, never()).delete();
    }

    @Test
    void permitsDeleteWithinSourceAccountOffice() {
        final AccountTransferStandingInstruction instruction = standingInstruction();
        when(standingInstructionRepository.findById(STANDING_INSTRUCTION_ID)).thenReturn(Optional.of(instruction));

        assertDoesNotThrow(() -> underTest.delete(STANDING_INSTRUCTION_ID));

        verify(officeScopeValidator).validateSavings(SOURCE_SAVINGS_ID);
        verify(instruction).delete();
    }

    @Test
    void missingDeleteUsesStandingInstructionNotFoundException() {
        when(standingInstructionRepository.findById(STANDING_INSTRUCTION_ID)).thenReturn(Optional.empty());

        assertThrows(StandingInstructionNotFoundException.class, () -> underTest.delete(STANDING_INSTRUCTION_ID));
    }

    private AccountTransferStandingInstruction standingInstruction() {
        final AccountTransferStandingInstruction instruction = Mockito.spy(new AccountTransferStandingInstruction());
        final AccountTransferDetails details = Mockito.mock(AccountTransferDetails.class);
        final SavingsAccount savingsAccount = Mockito.mock(SavingsAccount.class);
        when(details.fromSavingsAccount()).thenReturn(savingsAccount);
        when(savingsAccount.getId()).thenReturn(SOURCE_SAVINGS_ID);
        ReflectionTestUtils.setField(instruction, "accountTransferDetails", details);
        return instruction;
    }
}
