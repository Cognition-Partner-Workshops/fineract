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
package org.apache.fineract.infrastructure.jobs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.jobs.data.partitionedjobs.PartitionedJob;
import org.apache.fineract.infrastructure.jobs.domain.JobExecutionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.transaction.support.TransactionTemplate;

class StuckJobExecutorServiceImplTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void interruptedWhileWaitingForPartitionsRestoresInterruptStatus() {
        String jobName = PartitionedJob.LOAN_COB.name();
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        when(jobExecutionRepository.getStuckJobIdsByJobName(jobName)).thenReturn(List.of(1L));
        when(jobExecutionRepository.getStuckJobCountByJobName(jobName)).thenReturn(1L);
        when(jobExecutionRepository.getNotCompletedPartitionsCount(anyLong(), any())).thenReturn(1L);
        StuckJobExecutorServiceImpl underTest = new StuckJobExecutorServiceImpl(jobExecutionRepository, mock(TransactionTemplate.class),
                mock(JobOperator.class));

        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> underTest.resumeStuckJob(jobName)).isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
