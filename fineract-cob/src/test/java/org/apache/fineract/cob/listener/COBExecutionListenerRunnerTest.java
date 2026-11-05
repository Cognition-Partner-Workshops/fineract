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
package org.apache.fineract.cob.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.context.ApplicationContext;

class COBExecutionListenerRunnerTest {

    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final JobExecution jobExecution = new JobExecution(1L);

    @Test
    void onlyListenersOfTheGivenJobAreInvoked() {
        FineractCOBBeforeJobListener matchingBefore = beforeListener("matching", "COB");
        FineractCOBBeforeJobListener otherBefore = beforeListener("other", "OTHER_JOB");
        FineractCOBAfterJobListener matchingAfter = afterListener("matchingAfter", "COB");
        FineractCOBAfterJobListener otherAfter = afterListener("otherAfter", "OTHER_JOB");
        when(applicationContext.getBeanNamesForType(FineractCOBBeforeJobListener.class)).thenReturn(new String[] { "matching", "other" });
        when(applicationContext.getBeanNamesForType(FineractCOBAfterJobListener.class))
                .thenReturn(new String[] { "matchingAfter", "otherAfter" });

        COBExecutionListenerRunner underTest = new COBExecutionListenerRunner(applicationContext, "COB");
        underTest.beforeJob(jobExecution);
        underTest.afterJob(jobExecution);

        verify(matchingBefore).beforeJob(jobExecution);
        verify(matchingAfter).afterJob(jobExecution);
        verify(otherBefore, never()).beforeJob(jobExecution);
        verify(otherAfter, never()).afterJob(jobExecution);
    }

    @Test
    void listenersOfTheSamePhaseRunInDiscoveryOrder() {
        FineractCOBBeforeJobListener first = beforeListener("first", "COB");
        FineractCOBBeforeJobListener second = beforeListener("second", "COB");
        when(applicationContext.getBeanNamesForType(FineractCOBBeforeJobListener.class)).thenReturn(new String[] { "first", "second" });
        when(applicationContext.getBeanNamesForType(FineractCOBAfterJobListener.class)).thenReturn(new String[0]);

        new COBExecutionListenerRunner(applicationContext, "COB").beforeJob(jobExecution);

        var order = inOrder(first, second);
        order.verify(first).beforeJob(jobExecution);
        order.verify(second).beforeJob(jobExecution);
    }

    @Test
    void noListenersMeansNoInvocations() {
        when(applicationContext.getBeanNamesForType(FineractCOBBeforeJobListener.class)).thenReturn(new String[0]);
        when(applicationContext.getBeanNamesForType(FineractCOBAfterJobListener.class)).thenReturn(new String[0]);

        COBExecutionListenerRunner underTest = new COBExecutionListenerRunner(applicationContext, "COB");
        assertDoesNotThrow(() -> underTest.beforeJob(jobExecution));
        assertDoesNotThrow(() -> underTest.afterJob(jobExecution));
    }

    @Test
    void listenerFailureStopsTheRemainingCallbacks() {
        FineractCOBBeforeJobListener first = beforeListener("first", "COB");
        FineractCOBBeforeJobListener second = beforeListener("second", "COB");
        when(applicationContext.getBeanNamesForType(FineractCOBBeforeJobListener.class)).thenReturn(new String[] { "first", "second" });
        when(applicationContext.getBeanNamesForType(FineractCOBAfterJobListener.class)).thenReturn(new String[0]);
        doThrow(new IllegalStateException("failed")).when(first).beforeJob(jobExecution);
        COBExecutionListenerRunner underTest = new COBExecutionListenerRunner(applicationContext, "COB");

        assertThrows(IllegalStateException.class, () -> underTest.beforeJob(jobExecution));
        verify(second, never()).beforeJob(jobExecution);
    }

    private FineractCOBBeforeJobListener beforeListener(String beanName, String jobName) {
        FineractCOBBeforeJobListener listener = mock(FineractCOBBeforeJobListener.class);
        when(listener.getJobName()).thenReturn(jobName);
        when(applicationContext.getBean(beanName)).thenReturn(listener);
        return listener;
    }

    private FineractCOBAfterJobListener afterListener(String beanName, String jobName) {
        FineractCOBAfterJobListener listener = mock(FineractCOBAfterJobListener.class);
        when(listener.getJobName()).thenReturn(jobName);
        when(applicationContext.getBean(beanName)).thenReturn(listener);
        return listener;
    }
}
