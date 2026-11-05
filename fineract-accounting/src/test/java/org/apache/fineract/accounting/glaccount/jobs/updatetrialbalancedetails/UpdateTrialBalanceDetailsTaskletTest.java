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
package org.apache.fineract.accounting.glaccount.jobs.updatetrialbalancedetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.accounting.AccountingTestSupport;
import org.apache.fineract.accounting.glaccount.domain.TrialBalance;
import org.apache.fineract.accounting.glaccount.domain.TrialBalanceRepository;
import org.apache.fineract.accounting.glaccount.domain.TrialBalanceRepositoryWrapper;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.infrastructure.core.service.database.RoutingDataSourceServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class UpdateTrialBalanceDetailsTaskletTest extends AccountingTestSupport {

    @Captor
    private ArgumentCaptor<List<TrialBalance>> captor;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private RoutingDataSourceServiceFactory factory;
    @Mock
    private TrialBalanceRepository repository;
    @Mock
    private TrialBalanceRepositoryWrapper wrapper;
    @Mock
    private JournalEntryRepository entries;
    @Mock
    private StepContribution contribution;
    @Mock
    private ChunkContext chunkContext;
    private UpdateTrialBalanceDetailsTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new UpdateTrialBalanceDetailsTasklet(factory, wrapper, repository, entries);
    }

    private TrialBalance row(String amount) {
        TrialBalance row = new TrialBalance();
        row.setAmount(new BigDecimal(amount));
        return row;
    }

    @Test
    void accumulatesExactPositiveAndNegativeBalancesFromPriorClosingBalance() throws Exception {
        when(repository.findDistinctOfficeIdsWithNullClosingBalance()).thenReturn(List.of(1L));
        when(repository.findDistinctAccountIdsWithNullClosingBalanceByOfficeId(1L)).thenReturn(List.of(2L));
        when(repository.findLastClosingBalance(1L, 2L)).thenReturn(List.of(new BigDecimal("100.123456")));
        TrialBalance debit = row("0.000001");
        TrialBalance credit = row("-20.123457");
        when(wrapper.findNewByOfficeAndAccount(1L, 2L)).thenReturn(List.of(debit, credit));
        assertEquals(RepeatStatus.FINISHED, tasklet.execute(contribution, chunkContext));
        assertEquals(new BigDecimal("100.123457"), debit.getClosingBalance());
        assertEquals(new BigDecimal("80.000000"), credit.getClosingBalance());
    }

    @Test
    void missingPreviousBalanceStartsAtZeroAndBusinessDateGapsAreSkipped() throws Exception {
        when(entries.findTransactionDatesAfter(any())).thenReturn(List.of(BUSINESS_DATE, BUSINESS_DATE.plusDays(1)));
        when(repository.findDistinctOfficeIdsWithNullClosingBalance()).thenReturn(List.of(1L));
        when(repository.findDistinctAccountIdsWithNullClosingBalanceByOfficeId(1L)).thenReturn(List.of(2L));
        TrialBalance row = row("-0.000001");
        when(wrapper.findNewByOfficeAndAccount(1L, 2L)).thenReturn(List.of(row));
        tasklet.execute(contribution, chunkContext);
        assertEquals(new BigDecimal("-0.000001"), row.getClosingBalance());
        verify(entries, never()).findTrialBalanceLinesForDate(any());
        verify(wrapper, never()).save(any());
    }

    @Test
    void priorDateGapIsConvertedToTrialBalanceRowsWithoutLosingDecimalValues() throws Exception {
        when(repository.findMaxCreatedDate()).thenReturn(BUSINESS_DATE.minusDays(3));
        when(entries.findTransactionDatesAfter(BUSINESS_DATE.minusDays(3))).thenReturn(List.of(BUSINESS_DATE.minusDays(1)));
        Object[] result = {1L, 2L, new BigDecimal("1.234567"), BUSINESS_DATE.minusDays(1), BUSINESS_DATE.minusDays(2), new BigDecimal("9.876543")};
        when(entries.findTrialBalanceLinesForDate(BUSINESS_DATE.minusDays(1))).thenReturn(java.util.Collections.singletonList(result));
        tasklet.execute(contribution, chunkContext);
        verify(wrapper).save(captor.capture());
        TrialBalance saved = captor.getValue().getFirst();
        assertEquals(1L, saved.getOfficeId());
        assertEquals(2L, saved.getGlAccountId());
        assertEquals(new BigDecimal("1.234567"), saved.getAmount());
        assertEquals(new BigDecimal("9.876543"), saved.getClosingBalance());
        assertEquals(BUSINESS_DATE.minusDays(1), saved.getEntryDate());
        assertEquals(BUSINESS_DATE.minusDays(2), saved.getTransactionDate());
    }
}
