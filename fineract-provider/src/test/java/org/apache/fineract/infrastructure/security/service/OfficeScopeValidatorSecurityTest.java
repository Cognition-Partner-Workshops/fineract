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
package org.apache.fineract.infrastructure.security.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

class OfficeScopeValidatorSecurityTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSecurityContext context;
    @InjectMocks
    private OfficeScopeValidator underTest;
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
    void rejectsSavingsAccountOwnedByDifferentClient() {
        when(jdbcTemplate.queryForList("select client_id, group_id from m_savings_account where id = ?", 44L))
                .thenReturn(List.of(savingsOwner(19L, null)));

        assertThrows(NoAuthorizationException.class, () -> underTest.validateSavingsHasSameOwner(44L, 7L, null));
    }

    @Test
    void allowsSavingsAccountOwnedBySameClientInScope() {
        when(jdbcTemplate.queryForList("select client_id, group_id from m_savings_account where id = ?", 44L))
                .thenReturn(List.of(savingsOwner(7L, null)));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(44L))).thenReturn(List.of(".4."));
        when(context.officeHierarchy()).thenReturn(".4.");

        assertDoesNotThrow(() -> underTest.validateSavingsHasSameOwner(44L, 7L, null));

        verify(context).validateAccessRights(".4.");
    }

    @Test
    void rejectsSavingsAccountOutsideUserOfficeEvenForSameClient() {
        when(jdbcTemplate.queryForList("select client_id, group_id from m_savings_account where id = ?", 44L))
                .thenReturn(List.of(savingsOwner(7L, null)));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(44L))).thenReturn(List.of(".5."));
        when(context.officeHierarchy()).thenReturn(".4.");
        org.mockito.Mockito.doThrow(new NoAuthorizationException("outside office scope")).when(context).validateAccessRights(".5.");

        assertThrows(NoAuthorizationException.class, () -> underTest.validateSavingsHasSameOwner(44L, 7L, null));
    }

    @Test
    void allowsSavingsAccountOwnedBySameGroupInScope() {
        when(jdbcTemplate.queryForList("select client_id, group_id from m_savings_account where id = ?", 44L))
                .thenReturn(List.of(savingsOwner(null, 12L)));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(44L))).thenReturn(List.of(".4."));
        when(context.officeHierarchy()).thenReturn(".4.");

        assertDoesNotThrow(() -> underTest.validateSavingsHasSameOwner(44L, null, 12L));
    }

    @Test
    void rejectsTransferToSavingsOwnedByDifferentClient() {
        when(jdbcTemplate.queryForList("select client_id, group_id from m_savings_account where id = ?", 44L))
                .thenReturn(List.of(savingsOwner(19L, null)));
        when(jdbcTemplate.queryForList("select client_id, group_id from m_savings_account where id = ?", 55L))
                .thenReturn(List.of(savingsOwner(7L, null)));

        assertThrows(NoAuthorizationException.class, () -> underTest.validateSavingsHasSameOwner(44L, 55L));
    }

    private Map<String, Object> savingsOwner(final Long clientId, final Long groupId) {
        final Map<String, Object> owner = new HashMap<>();
        owner.put("client_id", clientId);
        owner.put("group_id", groupId);
        return owner;
    }
}
