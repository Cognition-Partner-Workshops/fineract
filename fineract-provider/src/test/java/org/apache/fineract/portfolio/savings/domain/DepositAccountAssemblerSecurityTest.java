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
package org.apache.fineract.portfolio.savings.domain;

import static org.apache.fineract.portfolio.savings.DepositAccountType.FIXED_DEPOSIT;
import static org.apache.fineract.portfolio.savings.SavingsApiConstants.clientIdParamName;
import static org.apache.fineract.portfolio.savings.SavingsApiConstants.groupIdParamName;
import static org.apache.fineract.portfolio.savings.SavingsApiConstants.productIdParamName;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.group.domain.GroupRepositoryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepositAccountAssemblerSecurityTest {

    @Mock
    private FromJsonHelper fromApiJsonHelper;
    @Mock
    private FixedDepositProductRepository fixedDepositProductRepository;
    @Mock
    private ClientRepositoryWrapper clientRepository;
    @Mock
    private GroupRepositoryWrapper groupRepository;
    @Mock
    private SavingsAccountRepositoryWrapper savingsAccountRepository;
    @Mock
    private OfficeScopeValidator officeScopeValidator;
    @Mock
    private JsonCommand command;
    @InjectMocks
    private DepositAccountAssembler underTest;

    @Test
    void rejectsOutOfScopeClientBeforeRepositoryLoad() {
        final long clientId = 82L;
        parsedJson(200L, clientId, null);
        when(fixedDepositProductRepository.findById(200L)).thenReturn(Optional.of(org.mockito.Mockito.mock(FixedDepositProduct.class)));
        lenient().when(clientRepository.findOneWithNotFoundDetection(eq(clientId), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new IllegalStateException("client repository reached"));
        doThrow(new NoAuthorizationException("client outside user office")).when(officeScopeValidator).validateClient(clientId);

        assertAll(() -> assertThrows(NoAuthorizationException.class, () -> underTest.assembleFrom(command, null, FIXED_DEPOSIT)),
                () -> verify(clientRepository, never()).findOneWithNotFoundDetection(eq(clientId),
                        org.mockito.ArgumentMatchers.anyBoolean()));
    }

    @Test
    void rejectsOutOfScopeGroupBeforeRepositoryLoad() {
        final long groupId = 83L;
        parsedJson(200L, null, groupId);
        when(fixedDepositProductRepository.findById(200L)).thenReturn(Optional.of(org.mockito.Mockito.mock(FixedDepositProduct.class)));
        lenient().when(groupRepository.findOneWithNotFoundDetection(groupId))
                .thenThrow(new IllegalStateException("group repository reached"));
        doThrow(new NoAuthorizationException("group outside user office")).when(officeScopeValidator).validateGroup(groupId);

        assertAll(() -> assertThrows(NoAuthorizationException.class, () -> underTest.assembleFrom(command, null, FIXED_DEPOSIT)),
                () -> verify(groupRepository, never()).findOneWithNotFoundDetection(groupId));
    }

    @Test
    void rejectsOutOfScopeSavingsAccountBeforeRepositoryLoad() {
        final long savingsId = 84L;
        doThrow(new NoAuthorizationException("savings account outside user office")).when(officeScopeValidator).validateSavings(savingsId);
        lenient().when(savingsAccountRepository.findOneWithNotFoundDetection(savingsId, FIXED_DEPOSIT))
                .thenThrow(new IllegalStateException("savings repository reached"));

        assertAll(() -> assertThrows(NoAuthorizationException.class, () -> underTest.assembleFrom(savingsId, FIXED_DEPOSIT)),
                () -> verify(savingsAccountRepository, never()).findOneWithNotFoundDetection(savingsId, FIXED_DEPOSIT));
    }

    private JsonElement parsedJson(final Long productId, final Long clientId, final Long groupId) {
        final JsonElement parsedJson = JsonParser.parseString("{}");
        when(command.parsedJson()).thenReturn(parsedJson);
        when(fromApiJsonHelper.extractLongNamed(eq(productIdParamName), same(parsedJson))).thenReturn(productId);
        when(fromApiJsonHelper.extractLongNamed(eq(clientIdParamName), same(parsedJson))).thenReturn(clientId);
        lenient().when(fromApiJsonHelper.extractLongNamed(eq(groupIdParamName), same(parsedJson))).thenReturn(groupId);
        return parsedJson;
    }
}
