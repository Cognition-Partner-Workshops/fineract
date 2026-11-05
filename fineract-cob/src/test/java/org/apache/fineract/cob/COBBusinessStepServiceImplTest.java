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
package org.apache.fineract.cob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.apache.fineract.cob.data.BusinessStepNameAndOrder;
import org.apache.fineract.cob.domain.BatchBusinessStep;
import org.apache.fineract.cob.domain.BatchBusinessStepRepository;
import org.apache.fineract.cob.exceptions.BusinessStepException;
import org.apache.fineract.cob.service.ReloaderService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;

class COBBusinessStepServiceImplTest {

    private final BatchBusinessStepRepository repository = mock(BatchBusinessStepRepository.class);
    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
    private final BusinessEventNotifierService eventNotifier = mock(BusinessEventNotifierService.class);
    private final ConfigurationDomainService configuration = mock(ConfigurationDomainService.class);
    private final ReloaderService reloader = mock(ReloaderService.class);
    private final COBBusinessStepServiceImpl underTest = new COBBusinessStepServiceImpl(repository, applicationContext, beanFactory,
            eventNotifier, configuration, reloader);

    @AfterEach
    void cleanup() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void runRejectsMissingExecutionMap() {
        assertThrows(BusinessStepException.class, () -> underTest.run(new TreeMap<>(), item()));
        assertThrows(BusinessStepException.class, () -> underTest.run(null, item()));
    }

    @Test
    void runReloadsAndExecutesStepsInOrder() {
        AbstractPersistableCustom<Long> first = item();
        AbstractPersistableCustom<Long> reloaded = item();
        AbstractPersistableCustom<Long> result = item();
        COBBusinessStep<AbstractPersistableCustom<Long>> stepOne = mock(COBBusinessStep.class);
        COBBusinessStep<AbstractPersistableCustom<Long>> stepTwo = mock(COBBusinessStep.class);
        when(configuration.isCOBBulkEventEnabled()).thenReturn(false);
        when(applicationContext.getBean("one")).thenReturn(stepOne);
        when(applicationContext.getBean("two")).thenReturn(stepTwo);
        when(reloader.reload(first)).thenReturn(reloaded);
        when(reloader.reload(reloaded)).thenReturn(reloaded);
        when(stepOne.execute(reloaded)).thenReturn(reloaded);
        when(stepTwo.execute(reloaded)).thenReturn(result);

        TreeMap<Long, String> executionMap = new TreeMap<>();
        executionMap.put(2L, "two");
        executionMap.put(1L, "one");

        assertSame(result, underTest.run(executionMap, first));
        var order = inOrder(applicationContext, reloader, stepOne, stepTwo);
        order.verify(applicationContext).getBean("one");
        order.verify(reloader).reload(first);
        order.verify(stepOne).execute(reloaded);
        order.verify(applicationContext).getBean("two");
        order.verify(reloader).reload(reloaded);
        order.verify(stepTwo).execute(reloaded);
        assertEquals(org.apache.fineract.infrastructure.core.domain.ActionContext.COB, ThreadLocalContextUtil.getActionContext());
    }

    @Test
    void runRecordsBulkEventsAndResetsOnFailure() {
        COBBusinessStep<AbstractPersistableCustom<Long>> step = mock(COBBusinessStep.class);
        when(configuration.isCOBBulkEventEnabled()).thenReturn(true);
        when(applicationContext.getBean("step")).thenReturn(step);
        when(reloader.reload(any())).thenReturn(item());
        when(step.execute(any())).thenThrow(new IllegalStateException("boom"));

        BusinessStepException exception = assertThrows(BusinessStepException.class,
                () -> underTest.run(new TreeMap<>(java.util.Map.of(1L, "step")), item()));

        assertEquals("Error happened during business step execution", exception.getMessage());
        verify(eventNotifier).startExternalEventRecording();
        verify(eventNotifier).resetEventRecording();
    }

    @Test
    void runStopsBulkEventRecordingAfterSuccess() {
        AbstractPersistableCustom<Long> item = item();
        COBBusinessStep<AbstractPersistableCustom<Long>> step = mock(COBBusinessStep.class);
        when(configuration.isCOBBulkEventEnabled()).thenReturn(true);
        when(applicationContext.getBean("step")).thenReturn(step);
        when(reloader.reload(item)).thenReturn(item);
        when(step.execute(item)).thenReturn(item);

        assertSame(item, underTest.run(new TreeMap<>(java.util.Map.of(1L, "step")), item));
        var order = inOrder(eventNotifier, step);
        order.verify(eventNotifier).startExternalEventRecording();
        order.verify(step).execute(item);
        order.verify(eventNotifier).stopExternalEventRecording();
    }

    @Test
    void getCOBBusinessStepsReturnsOnlyConfiguredBeans() {
        BatchBusinessStep configured = new BatchBusinessStep();
        configured.setStepName("CONFIGURED");
        configured.setStepOrder(3L);
        COBBusinessStep<AbstractPersistableCustom<Long>> included = mock(COBBusinessStep.class);
        COBBusinessStep<AbstractPersistableCustom<Long>> excluded = mock(COBBusinessStep.class);
        when(repository.findAllByJobName("job")).thenReturn(List.of(configured));
        when(beanFactory.getBeanNamesForType(COBBusinessStep.class)).thenReturn(new String[] { "included", "excluded" });
        when(applicationContext.getBean("included")).thenReturn(included);
        when(applicationContext.getBean("excluded")).thenReturn(excluded);
        when(included.getEnumStyledName()).thenReturn("CONFIGURED");
        when(excluded.getEnumStyledName()).thenReturn("NOT_CONFIGURED");

        Set<BusinessStepNameAndOrder> result = underTest.getCOBBusinessSteps(COBBusinessStep.class, "job");

        assertEquals(1, result.size());
        BusinessStepNameAndOrder step = result.iterator().next();
        assertEquals("included", step.getStepName());
        assertEquals(3L, step.getStepOrder());
    }

    @Test
    void getCOBBusinessStepsReturnsEmptySetWhenThereAreNoBeans() {
        when(repository.findAllByJobName("job")).thenReturn(List.of());
        when(beanFactory.getBeanNamesForType(COBBusinessStep.class)).thenReturn(new String[0]);

        assertEquals(Set.of(), underTest.getCOBBusinessSteps(COBBusinessStep.class, "job"));
    }

    private static AbstractPersistableCustom<Long> item() {
        return mock(AbstractPersistableCustom.class);
    }
}
