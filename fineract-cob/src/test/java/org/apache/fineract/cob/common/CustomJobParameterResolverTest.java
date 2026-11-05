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
package org.apache.fineract.cob.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonSyntaxException;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.cob.exceptions.CustomJobParameterNotFoundException;
import org.apache.fineract.infrastructure.jobs.data.JobParameterDTO;
import org.apache.fineract.infrastructure.jobs.domain.CustomJobParameter;
import org.apache.fineract.infrastructure.jobs.domain.CustomJobParameterRepository;
import org.apache.fineract.infrastructure.springbatch.SpringBatchJobConstants;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;

class CustomJobParameterResolverTest {

    private final CustomJobParameterRepository repository = mock(CustomJobParameterRepository.class);
    private final CustomJobParameterResolver underTest = new CustomJobParameterResolver(repository);

    @Test
    void resolveCopiesRequestedParameterIntoExecutionContext() {
        StepExecution stepExecution = stepExecution(7L);
        storedJson(7L, "[{\"parameterName\":\"BusinessDate\",\"parameterValue\":\"2023-01-01\"}]");
        StepContribution contribution = new StepContribution(stepExecution);

        underTest.resolve(contribution, chunkContext(stepExecution), "BusinessDate", "businessDate");

        assertEquals("2023-01-01", stepExecution.getExecutionContext().get("businessDate"));
    }

    @Test
    void resolveFailsWhenParameterRowIsMissing() {
        StepExecution stepExecution = stepExecution(7L);
        when(repository.findById(7L)).thenReturn(Optional.empty());

        CustomJobParameterNotFoundException exception = assertThrows(CustomJobParameterNotFoundException.class,
                () -> underTest.resolve(new StepContribution(stepExecution), chunkContext(stepExecution), "BusinessDate", "businessDate"));

        assertEquals("Custom job parameter not found with the given name: " + SpringBatchJobConstants.CUSTOM_JOB_PARAMETER_ID_KEY,
                exception.getDefaultUserMessage());
    }

    @Test
    void resolveFailsWhenRequestedKeyIsAbsent() {
        StepExecution stepExecution = stepExecution(7L);
        storedJson(7L, "[{\"parameterName\":\"OtherKey\",\"parameterValue\":\"value\"}]");

        CustomJobParameterNotFoundException exception = assertThrows(CustomJobParameterNotFoundException.class,
                () -> underTest.resolve(new StepContribution(stepExecution), chunkContext(stepExecution), "BusinessDate", "businessDate"));

        assertEquals("Custom job parameter not found with the given name: BusinessDate", exception.getDefaultUserMessage());
    }

    @Test
    void parameterSetIsParsedFromStoredJson() {
        StepExecution stepExecution = stepExecution(7L);
        storedJson(7L, "[{\"parameterName\":\"BusinessDate\",\"parameterValue\":\"2023-01-01\"}]");

        Optional<Set<JobParameterDTO>> result = underTest.getCustomJobParameterSet(stepExecution);

        assertEquals(Set.of(new JobParameterDTO("BusinessDate", "2023-01-01")), result.orElseThrow());
    }

    @Test
    void parameterSetIsEmptyOptionalWhenRowIsMissing() {
        StepExecution stepExecution = stepExecution(7L);
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertTrue(underTest.getCustomJobParameterSet(stepExecution).isEmpty());
    }

    @Test
    void parameterSetIsEmptyCollectionForEmptyJsonArray() {
        StepExecution stepExecution = stepExecution(7L);
        storedJson(7L, "[]");

        assertEquals(Set.of(), underTest.getCustomJobParameterSet(stepExecution).orElseThrow());
    }

    @Test
    void parameterSetFailsOnMalformedJson() {
        StepExecution stepExecution = stepExecution(7L);
        storedJson(7L, "{not json");

        assertThrows(JsonSyntaxException.class, () -> underTest.getCustomJobParameterSet(stepExecution));
    }

    @Test
    void parameterByIdReturnsMatchingValueOnly() {
        StepExecution stepExecution = stepExecution(7L);
        storedJson(7L, "[{\"parameterName\":\"BusinessDate\",\"parameterValue\":\"2023-01-01\"}]");

        assertEquals(Optional.of("2023-01-01"), underTest.getCustomJobParameterById(stepExecution, "BusinessDate"));
        assertTrue(underTest.getCustomJobParameterById(stepExecution, "Missing").isEmpty());
    }

    private void storedJson(Long id, String json) {
        CustomJobParameter parameter = new CustomJobParameter();
        parameter.setParameterJson(json);
        when(repository.findById(id)).thenReturn(Optional.of(parameter));
    }

    private static StepExecution stepExecution(Long customJobParameterId) {
        JobParameters jobParameters = new JobParameters(java.util.Map.of(SpringBatchJobConstants.CUSTOM_JOB_PARAMETER_ID_KEY,
                new JobParameter<>(customJobParameterId, Long.class)));
        return new StepExecution("step", new JobExecution(1L, jobParameters));
    }

    private static ChunkContext chunkContext(StepExecution stepExecution) {
        return new ChunkContext(new StepContext(stepExecution));
    }
}
