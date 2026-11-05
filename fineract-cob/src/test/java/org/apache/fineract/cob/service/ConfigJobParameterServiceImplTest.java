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
package org.apache.fineract.cob.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.cob.COBBusinessStep;
import org.apache.fineract.cob.data.BusinessStep;
import org.apache.fineract.cob.data.JobBusinessStepConfigData;
import org.apache.fineract.cob.data.JobBusinessStepDetail;
import org.apache.fineract.cob.domain.BatchBusinessStep;
import org.apache.fineract.cob.domain.BatchBusinessStepRepository;
import org.apache.fineract.cob.exceptions.BusinessStepException;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

class ConfigJobParameterServiceImplTest {

    private final BatchBusinessStepRepository repository = mock(BatchBusinessStepRepository.class);
    private final BusinessStepConfigDataParser parser = mock(BusinessStepConfigDataParser.class);
    private final BusinessStepCategoryService categoryService = mock(BusinessStepCategoryService.class);
    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final BusinessStepMapper mapper = mock(BusinessStepMapper.class);
    private final ConfigJobParameterServiceImpl underTest = new ConfigJobParameterServiceImpl(repository, parser, categoryService,
            applicationContext, mapper);
    private final JsonCommand command = mock(JsonCommand.class);

    @BeforeEach
    void setUp() throws Exception {
        when(command.commandId()).thenReturn(42L);
        when(categoryService.getBusinessStepByCategory("LOAN")).thenReturn((Class) COBBusinessStep.class);
        when(applicationContext.getBeanNamesForType(COBBusinessStep.class)).thenReturn(new String[] { "first", "second" });
        COBBusinessStep<?> first = mock(COBBusinessStep.class);
        COBBusinessStep<?> second = mock(COBBusinessStep.class);
        when(first.getEnumStyledName()).thenReturn("FIRST");
        when(first.getHumanReadableName()).thenReturn("First step");
        when(second.getEnumStyledName()).thenReturn("SECOND");
        when(second.getHumanReadableName()).thenReturn("Second step");
        when(applicationContext.getBean("first")).thenReturn(first);
        when(applicationContext.getBean("second")).thenReturn(second);
        underTest.afterPropertiesSet();
    }

    @Test
    void updateReplacesConfiguredStepsAndReturnsCommandId() {
        BusinessStep first = step("FIRST", 2L);
        BusinessStep second = step("SECOND", 1L);
        when(parser.parseUpdate(command)).thenReturn(List.of(first, second));

        CommandProcessingResult result = underTest.updateStepConfigByJobName(command, "nightly");

        assertEquals(42L, result.getCommandId());
        verify(repository).deleteAllByJobName("nightly");
        ArgumentCaptor<BatchBusinessStep> saved = ArgumentCaptor.forClass(BatchBusinessStep.class);
        verify(repository, times(2)).save(saved.capture());
        assertEquals("nightly", saved.getAllValues().get(0).getJobName());
        assertEquals("FIRST", saved.getAllValues().get(0).getStepName());
        assertEquals(2L, saved.getAllValues().get(0).getStepOrder());
        assertEquals("nightly", saved.getAllValues().get(1).getJobName());
        assertEquals("SECOND", saved.getAllValues().get(1).getStepName());
        assertEquals(1L, saved.getAllValues().get(1).getStepOrder());
    }

    @Test
    void updateRejectsEmptyConfigurationWithoutDeleting() {
        when(parser.parseUpdate(command)).thenReturn(List.of());

        assertThrows(BusinessStepException.class, () -> underTest.updateStepConfigByJobName(command, "nightly"));
        verify(repository, never()).deleteAllByJobName(any());
    }

    @Test
    void updateRejectsUnavailableStepWithoutDeleting() {
        when(parser.parseUpdate(command)).thenReturn(List.of(step("UNKNOWN", 1L)));

        assertThrows(BusinessStepException.class, () -> underTest.updateStepConfigByJobName(command, "nightly"));
        verify(repository, never()).deleteAllByJobName(any());
        verify(repository, never()).save(any());
    }

    @Test
    void availableStepsContainBeanMetadata() {
        JobBusinessStepDetail detail = underTest.getAvailableBusinessStepsByJobName("LOAN");

        assertEquals("LOAN", detail.getJobName());
        assertEquals(2, detail.getAvailableBusinessSteps().size());
        assertEquals("FIRST", detail.getAvailableBusinessSteps().get(0).getStepName());
        assertEquals("First step", detail.getAvailableBusinessSteps().get(0).getStepDescription());
        assertEquals("SECOND", detail.getAvailableBusinessSteps().get(1).getStepName());
        assertEquals("Second step", detail.getAvailableBusinessSteps().get(1).getStepDescription());
    }

    @Test
    void availableStepsAreNullForUnknownCategory() {
        doReturn(null).when(categoryService).getBusinessStepByCategory("UNKNOWN");

        assertNull(underTest.getAvailableBusinessStepsByJobName("UNKNOWN"));
    }

    @Test
    void businessStepConfigUsesMappedRepositoryContent() {
        BatchBusinessStep stored = new BatchBusinessStep();
        List<BatchBusinessStep> storedSteps = List.of(stored);
        List<BusinessStep> mapped = List.of(step("FIRST", 1L));
        when(repository.findAllByJobName("nightly")).thenReturn(storedSteps);
        when(mapper.map(storedSteps)).thenReturn(mapped);

        JobBusinessStepConfigData result = underTest.getBusinessStepConfigByJobName("nightly");

        assertEquals("nightly", result.getJobName());
        assertEquals(mapped, result.getBusinessSteps());
    }

    @Test
    void configuredJobNamesDelegateToRepository() {
        when(repository.findConfiguredJobNames()).thenReturn(List.of("nightly"));
        assertEquals(List.of("nightly"), underTest.getAllConfiguredJobNames());
    }

    private static BusinessStep step(String name, long order) {
        BusinessStep result = new BusinessStep();
        result.setStepName(name);
        result.setOrder(order);
        return result;
    }
}
