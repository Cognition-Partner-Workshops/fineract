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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.apache.fineract.infrastructure.jobs.ScheduledJobRunnerConfig;
import org.apache.fineract.infrastructure.jobs.data.JobParameterDTO;
import org.apache.fineract.infrastructure.jobs.domain.JobParameter;
import org.apache.fineract.infrastructure.jobs.domain.JobParameterRepository;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetail;
import org.apache.fineract.infrastructure.jobs.service.jobname.JobNameData;
import org.apache.fineract.infrastructure.jobs.service.jobname.JobNameService;
import org.apache.fineract.infrastructure.jobs.service.jobparameterprovider.JobParameterProvider;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.quartz.JobExecutionException;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersIncrementer;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class JobStarterTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobOperator jobOperator;
    @Mock
    private JobParameterRepository jobParameterRepository;
    @Mock
    private List<JobParameterProvider<?>> jobParameterProviders;
    @Mock
    private JobNameService jobNameService;

    @Mock
    private TenantDetailsService tenantDetailsService;
    @Mock
    private AppUserRepositoryWrapper userRepository;
    @Mock
    private BusinessDateReadPlatformService businessDateReadPlatformService;

    @Captor
    private ArgumentCaptor<Set<JobParameterDTO>> jobParameterDTOCaptor;

    @InjectMocks
    private JobStarter underTest;

    @Test
    public void getJobParameterTest() {
        ScheduledJobDetail scheduledJobDetail = Mockito.mock(ScheduledJobDetail.class);
        when(scheduledJobDetail.getId()).thenReturn(1L);
        when(jobParameterRepository.findJobParametersByJobId(1L))
                .thenReturn(List.of(new JobParameter().setJobId(1L).setParameterName("testParamKey").setParameterValue("testParamValue")));
        Map<String, org.springframework.batch.core.job.parameters.JobParameter<?>> result = underTest.getJobParameter(scheduledJobDetail);
        Assertions.assertEquals("testParamValue", result.get("testParamKey").value());
    }

    @Test
    public void provideCustomJobParameters() {
        JobParameterProvider<?> jobParameterProvider = Mockito.mock(JobParameterProvider.class);
        when(jobParameterProvider.canProvideParametersForJob("testJobName")).thenReturn(true);
        when(jobParameterProviders.stream()).thenReturn(Stream.of(jobParameterProvider));
        underTest.provideCustomJobParameters("testJobName", Set.of(new JobParameterDTO("testKey", "testValue")));
        verify(jobParameterProvider, times(1)).provide(jobParameterDTOCaptor.capture());
    }

    @Test
    public void runWithComplete() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException,
            InvalidJobParametersException, JobRestartException, JobExecutionException {
        JobExecution jobExecution = Mockito.mock(JobExecution.class);
        Job job = Mockito.mock(Job.class);
        ScheduledJobDetail scheduledJobDetail = Mockito.mock(ScheduledJobDetail.class);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        setupMocks(jobExecution, job, scheduledJobDetail);
        JobExecution result = underTest.run(job, scheduledJobDetail, Set.of(), "default");
        Assertions.assertEquals(jobExecution, result);
    }

    @Test
    public void runWithFailed() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException,
            InvalidJobParametersException, JobRestartException, JobExecutionException {
        JobExecution jobExecution = Mockito.mock(JobExecution.class);
        Job job = Mockito.mock(Job.class);
        ScheduledJobDetail scheduledJobDetail = Mockito.mock(ScheduledJobDetail.class);

        for (BatchStatus failedStatus : JobStarter.FAILED_STATUSES) {
            setupMocks(jobExecution, job, scheduledJobDetail);
            when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
            when(jobExecution.getExitStatus()).thenReturn(new ExitStatus(failedStatus.name(), "testException"));
            JobExecutionException exception = Assertions.assertThrows(JobExecutionException.class,
                    () -> underTest.run(job, scheduledJobDetail, Set.of(), "default"));
            Assertions.assertEquals(
                    String.format("exitCode=%s;exitDescription=%s;exitException=null", failedStatus.name(), "testException"),
                    exception.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void preservesParametersForIncrementedJobs(boolean configuredParameters) throws Exception {
        Job job = Mockito.mock(Job.class);
        ScheduledJobDetail detail = Mockito.mock(ScheduledJobDetail.class);
        setupMocks(Mockito.mock(JobExecution.class), job, detail);
        when(job.getName()).thenReturn("parameterTestJob");
        when(job.getJobParametersIncrementer()).thenReturn(new RunIdIncrementer());
        when(job.getJobParametersValidator()).thenReturn(new DefaultJobParametersValidator());
        Mockito.doAnswer(invocation -> {
            JobExecution execution = invocation.getArgument(0);
            execution.setStatus(BatchStatus.COMPLETED);
            execution.setExitStatus(ExitStatus.COMPLETED);
            return null;
        }).when(job).execute(any(JobExecution.class));

        JobParameterProvider<String> provider = new JobParameterProvider<>() {

            @Override
            public boolean canProvideParametersForJob(String jobName) {
                return "testEnumstyleName".equals(jobName);
            }

            @Override
            public Map<String, org.springframework.batch.core.job.parameters.JobParameter<String>> provide(
                    Set<JobParameterDTO> parameters) {
                return Map.of("provided",
                        new org.springframework.batch.core.job.parameters.JobParameter<>("provided", "providedValue", String.class));
            }
        };
        if (!configuredParameters) {
            when(jobParameterRepository.findJobParametersByJobId(1L)).thenReturn(List.of());
        }
        JobRepository repository = new ResourcelessJobRepository();
        JobOperator operator = new ScheduledJobRunnerConfig().jobOperator(repository, new MapJobRegistry());
        JobStarter starter = new JobStarter(repository, operator, jobParameterRepository,
                configuredParameters ? List.of(provider) : List.of(), jobNameService, tenantDetailsService, userRepository,
                businessDateReadPlatformService);

        JobExecution execution = starter.run(job, detail, Set.of(), "default");

        Assertions.assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        Assertions.assertEquals(1L, execution.getJobParameters().getLong("run.id"));
        Assertions.assertEquals(configuredParameters ? 3 : 1, execution.getJobParameters().parameters().size());
        if (configuredParameters) {
            Assertions.assertEquals("testParamValue", execution.getJobParameters().getString("testParamKey"));
            Assertions.assertEquals("providedValue", execution.getJobParameters().getString("provided"));
        }
    }

    private void setupMocks(JobExecution jobExecution, Job job, ScheduledJobDetail scheduledJobDetail) throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, InvalidJobParametersException, JobRestartException {
        when(scheduledJobDetail.getId()).thenReturn(1L);
        when(scheduledJobDetail.getJobName()).thenReturn("testJobName");
        when(jobParameterRepository.findJobParametersByJobId(1L)).thenReturn(List.of(new JobParameter().setJobId(1L).setParameterName("testParamKey").setParameterValue("testParamValue")));
        when(jobOperator.run(any(Job.class), any(JobParameters.class))).thenReturn(jobExecution);
        JobParametersIncrementer jobParametersIncrementer = Mockito.mock(JobParametersIncrementer.class);
        when(jobParametersIncrementer.getNext(any(JobParameters.class))).thenReturn(new JobParameters());
        when(job.getJobParametersIncrementer()).thenReturn(jobParametersIncrementer);
        JobParameterProvider<?> jobParameterProvider = Mockito.mock(JobParameterProvider.class);
        when(jobParameterProvider.canProvideParametersForJob("testJobName")).thenReturn(true);
        when(jobParameterProviders.stream()).thenReturn(Stream.of(jobParameterProvider));
        when(jobNameService.getJobByHumanReadableName(any(String.class))).thenReturn(new JobNameData("testEnumstyleName", "testHumanReadableName"));
        when(tenantDetailsService.loadTenantById(anyString())).thenReturn(FineractPlatformTenant.builder().build());
        AppUser appUser = Mockito.mock(AppUser.class);
        when(appUser.getPassword()).thenReturn("");
        when(appUser.getAuthorities()).thenReturn(List.of());
        when(userRepository.fetchSystemUser()).thenReturn(appUser);
        when(businessDateReadPlatformService.getBusinessDates()).thenReturn(new HashMap<>());
    }
}
