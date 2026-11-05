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
package org.apache.fineract.cob.domain;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomLoanAccountLockRepositoryImplTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private DatabaseSpecificSQLGenerator generator;
    @Mock
    private Query query;
    private CustomLoanAccountLockRepositoryImpl underTest;

    @BeforeEach
    void setUp() {
        underTest = new CustomLoanAccountLockRepositoryImpl(generator);
        ReflectionTestUtils.setField(underTest, "entityManager", entityManager);
    }

    @Test
    void updateBuildsRecoveryQueryAndExecutesBeforeFlushing() {
        when(generator.subDate("lck.lock_placed_on_cob_business_date", "1", "DAY")).thenReturn("DATE_MINUS_ONE");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);

        underTest.updateLoanFromAccountLocks();

        verify(generator).subDate("lck.lock_placed_on_cob_business_date", "1", "DAY");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        var order = inOrder(entityManager, query);
        order.verify(entityManager).createNativeQuery(sql.capture());
        order.verify(query).executeUpdate();
        order.verify(entityManager).flush();
        assertTrue(sql.getValue().contains("UPDATE m_loan SET last_closed_business_date = (select DATE_MINUS_ONE"));
        assertTrue(sql.getValue().contains("where last_closed_business_date is null and exists"));
        assertTrue(sql.getValue().contains("lck.lock_placed_on_cob_business_date is not null"));
        assertTrue(sql.getValue().contains("lck.error is not null"));
        assertTrue(sql.getValue().contains("lck.loan_id = id"));
        assertTrue(sql.getValue().contains("'LOAN_COB_CHUNK_PROCESSING','LOAN_INLINE_COB_PROCESSING'"));
    }

    @Test
    void failedUpdatePropagatesWithoutFlushing() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        IllegalStateException failure = new IllegalStateException("update failed");
        when(query.executeUpdate()).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, underTest::updateLoanFromAccountLocks));
        verify(entityManager, never()).flush();
    }
}
