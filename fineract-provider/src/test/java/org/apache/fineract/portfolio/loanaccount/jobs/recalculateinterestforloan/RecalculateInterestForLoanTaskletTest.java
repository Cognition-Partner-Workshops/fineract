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
package org.apache.fineract.portfolio.loanaccount.jobs.recalculateinterestforloan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.office.data.OfficeData;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.service.RecalculateInterestPoster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class RecalculateInterestForLoanTaskletTest {

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
        Map<String, JobParameter<?>> parameters = Map.of("officeId", new JobParameter<>("1", String.class), "thread-pool-size",
                new JobParameter<>("1", String.class), "batch-size", new JobParameter<>("1", String.class));
        when(chunkContext.getStepContext().getStepExecution().getJobParameters().getParameters()).thenReturn(parameters);
        OfficeReadPlatformService officeReadPlatformService = mock(OfficeReadPlatformService.class);
        OfficeData office = mock(OfficeData.class);
        when(office.getHierarchy()).thenReturn(".");
        when(officeReadPlatformService.retrieveOffice(1L)).thenReturn(office);
        LoanReadPlatformService loanReadPlatformService = mock(LoanReadPlatformService.class);
        when(loanReadPlatformService.fetchLoansForInterestRecalculation(anyInt(), anyLong(), anyString())).thenReturn(List.of(1L),
                List.of());
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(RecalculateInterestPoster.class)).thenReturn(mock(RecalculateInterestPoster.class));
        Future<?> future = mock(Future.class);
        when(future.get()).thenThrow(new InterruptedException("interrupted"));
        ThreadPoolTaskExecutor taskExecutor = mock(ThreadPoolTaskExecutor.class);
        doReturn(future).when(taskExecutor).submit(any(Callable.class));

        RecalculateInterestForLoanTasklet underTest = new RecalculateInterestForLoanTasklet(loanReadPlatformService,
                mock(LoanWritePlatformService.class), applicationContext, officeReadPlatformService, taskExecutor);
        underTest.execute(mock(StepContribution.class), chunkContext);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
