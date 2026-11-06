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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTransactionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class LoanReadPlatformServiceSecurityTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private LoanTransactionRelationReadService loanTransactionRelationReadService;
    @Mock
    private LoanChargePaidByReadService loanChargePaidByReadService;
    @Mock
    private LoanTransactionRepository loanTransactionRepository;
    @Mock
    private LoanTransactionMapper loanTransactionMapper;
    @InjectMocks
    private LoanReadPlatformServiceImpl underTest;

    @Test
    void rejectsTransactionReadOutsideUserOffice() {
        final Long loanId = 91L;
        lenient().when(context.officeHierarchy()).thenReturn(".1.");
        lenient().when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(loanId), eq(loanId))).thenReturn(List.of(".2."));

        assertThrows(LoanNotFoundException.class, () -> underTest.retrieveLoanTransactions(loanId));

        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), eq(loanId));
    }

    @Test
    void permitsTransactionReadWithinUserOffice() {
        final Long loanId = 92L;
        lenient().when(context.officeHierarchy()).thenReturn(".1.");
        lenient().when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(loanId), eq(loanId))).thenReturn(List.of(".1.2."));
        lenient().when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(loanId))).thenReturn(List.of());
        lenient().when(loanTransactionRelationReadService.fetchLoanTransactionRelationDataFrom(List.of())).thenReturn(List.of());
        lenient().when(loanChargePaidByReadService.fetchLoanChargesPaidByDataTransactionId(List.of())).thenReturn(List.of());

        assertNotNull(underTest.retrieveLoanTransactions(loanId));
    }

    @Test
    void rejectsPagedTransactionReadOutsideUserOffice() {
        final Long loanId = 93L;
        final Pageable pageable = PageRequest.of(0, 10);
        lenient().when(context.officeHierarchy()).thenReturn(".1.");
        lenient().when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(loanId), eq(loanId))).thenReturn(List.of(".2."));
        lenient().when(loanTransactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<LoanTransaction>(List.of()));

        assertThrows(LoanNotFoundException.class, () -> underTest.retrieveLoanTransactions(loanId, Set.of(), pageable));

        verify(loanTransactionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void permitsPagedTransactionReadWithinUserOffice() {
        final Long loanId = 94L;
        final Pageable pageable = PageRequest.of(0, 10);
        lenient().when(context.officeHierarchy()).thenReturn(".1.");
        lenient().when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(loanId), eq(loanId))).thenReturn(List.of(".1.2."));
        lenient().when(loanTransactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<LoanTransaction>(List.of()));

        assertNotNull(underTest.retrieveLoanTransactions(loanId, Set.of(), pageable));

        verify(loanTransactionRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
