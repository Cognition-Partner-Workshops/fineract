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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

class JobExecutionContextCopyListenerTest {

    @Test
    void onlyListedKeysAreCopied() {
        StepExecution stepExecution = stepExecution();
        stepExecution.getJobExecution().getExecutionContext().put("copied", "value");
        stepExecution.getJobExecution().getExecutionContext().put("ignored", "other");

        new JobExecutionContextCopyListener(List.of("copied")).beforeStep(stepExecution);

        assertEquals("value", stepExecution.getExecutionContext().get("copied"));
        assertFalse(stepExecution.getExecutionContext().containsKey("ignored"));
    }

    @Test
    void existingStepValuesAreNotOverwritten() {
        StepExecution stepExecution = stepExecution();
        stepExecution.getJobExecution().getExecutionContext().put("key", "fromJob");
        stepExecution.getExecutionContext().put("key", "fromStep");

        new JobExecutionContextCopyListener(List.of("key")).beforeStep(stepExecution);

        assertEquals("fromStep", stepExecution.getExecutionContext().get("key"));
    }

    @Test
    void emptyJobContextLeavesStepContextUntouched() {
        StepExecution stepExecution = stepExecution();

        new JobExecutionContextCopyListener(List.of("key")).beforeStep(stepExecution);

        assertEquals(0, stepExecution.getExecutionContext().size());
    }

    private static StepExecution stepExecution() {
        return new StepExecution("step", new JobExecution(1L));
    }
}
