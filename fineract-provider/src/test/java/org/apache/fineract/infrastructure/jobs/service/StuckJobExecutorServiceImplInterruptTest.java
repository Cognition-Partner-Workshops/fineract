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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.jobs.domain.JobExecutionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StuckJobExecutorServiceImplInterruptTest {

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void restoresInterruptFlag() throws Exception {
        JobExecutionRepository repository = mock(JobExecutionRepository.class);
        when(repository.getNotCompletedPartitionsCount(1L, "partition")).thenReturn(1L);
        StuckJobExecutorServiceImpl service = new StuckJobExecutorServiceImpl(repository, null, null);
        Thread.currentThread().interrupt();
        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(service, "handleStuckPartitionedJob", 1L, "partition"));
        assertTrue(Thread.interrupted());
    }
}
