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
package org.apache.fineract.portfolio.savings.jobs.postinterestforsavings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.savings.data.SavingsAccountData;
import org.apache.fineract.portfolio.savings.service.SavingsAccountReadPlatformService;
import org.apache.fineract.portfolio.savings.service.SavingsSchedularInterestPosterTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class PostInterestForSavingTaskletTest {

    @BeforeEach
    void setUpContext() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.now(ZoneId.systemDefault()))));
    }

    @AfterEach
    void resetContext() {
        ThreadLocalContextUtil.reset();
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void interruptedWhileWaitingForPostersRestoresInterruptStatus() throws Exception {
        ChunkContext chunkContext = mock(ChunkContext.class, RETURNS_DEEP_STUBS);
        when(chunkContext.getStepContext().getJobParameters()).thenReturn(Map.of("thread-pool-size", "1", "batch-size", "1"));
        SavingsAccountData account = mock(SavingsAccountData.class);
        when(account.getId()).thenReturn(1L);
        SavingsAccountReadPlatformService readService = mock(SavingsAccountReadPlatformService.class);
        when(readService.retrieveAllSavingsDataForInterestPosting(anyBoolean(), anyInt(), any(), anyLong()))
                .thenReturn(new ArrayList<>(List.of(account)), new ArrayList<>());
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(SavingsSchedularInterestPosterTask.class))
                .thenReturn(mock(SavingsSchedularInterestPosterTask.class));
        Future<?> future = mock(Future.class);
        when(future.get()).thenThrow(new InterruptedException("interrupted"));
        ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
        doReturn(future).when(taskExecutor).submit(any(Callable.class));

        PostInterestForSavingTasklet underTest = new PostInterestForSavingTasklet(readService, mock(ConfigurationDomainService.class),
                applicationContext, taskExecutor);
        underTest.execute(mock(StepContribution.class), chunkContext);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
