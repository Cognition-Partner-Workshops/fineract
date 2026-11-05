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
package org.apache.fineract.infrastructure.jobs;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.configuration.DuplicateJobException;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ScheduledJobRunnerConfigTest {

    private final ScheduledJobRunnerConfig configuration = new ScheduledJobRunnerConfig();

    @Test
    void registersJobsOnContextRefresh() {
        Job job = mock(Job.class);
        when(job.getName()).thenReturn("testJob");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JobRegistry.class, configuration::jobRegistry);
            context.registerBean("job", Job.class, () -> job);
            context.refresh();

            JobRegistry registry = context.getBean(JobRegistry.class);
            assertSame(job, registry.getJob("testJob"));
            assertNull(registry.getJob("unknownJob"));
        }
    }

    @Test
    void rejectsDuplicateJobNames() {
        Job firstJob = mock(Job.class);
        Job secondJob = mock(Job.class);
        when(firstJob.getName()).thenReturn("duplicateJob");
        when(secondJob.getName()).thenReturn("duplicateJob");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JobRegistry.class, configuration::jobRegistry);
            context.registerBean("firstJob", Job.class, () -> firstJob);
            context.registerBean("secondJob", Job.class, () -> secondJob);

            IllegalStateException exception = assertThrows(IllegalStateException.class, context::refresh);
            assertInstanceOf(DuplicateJobException.class, exception.getCause());
        }
    }
}
