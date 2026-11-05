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
package org.apache.fineract.infrastructure.core.service.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.sql.DataSource;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

class TenantDatabaseUpgradeServiceTest {

    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void cleanUp() {
        Thread.interrupted();
        release.countDown();
    }

    @Test
    void interruptedWhileWaitingForTenantUpgradesRestoresInterruptStatus() {
        TenantDetailsService tenantDetailsService = mock(TenantDetailsService.class);
        when(tenantDetailsService.findAllTenants())
                .thenReturn(List.of(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null)));
        TenantDataSourceFactory tenantDataSourceFactory = mock(TenantDataSourceFactory.class);
        when(tenantDataSourceFactory.create(any())).thenAnswer(invocation -> {
            release.await();
            throw new IllegalStateException("upgrade aborted");
        });
        FineractProperties.FineractTaskExecutor taskExecutor = new FineractProperties.FineractTaskExecutor();
        taskExecutor.setTenantUpgradeTaskExecutorCorePoolSize(1);
        taskExecutor.setTenantUpgradeTaskExecutorMaxPoolSize(1);
        taskExecutor.setTenantUpgradeTaskExecutorQueueCapacity(1);
        FineractProperties fineractProperties = new FineractProperties();
        fineractProperties.setTaskExecutor(taskExecutor);
        TenantDatabaseUpgradeService underTest = new TenantDatabaseUpgradeService(tenantDetailsService, mock(DataSource.class),
                fineractProperties, mock(TenantDatabaseStateVerifier.class), mock(ExtendedSpringLiquibaseFactory.class),
                tenantDataSourceFactory, mock(Environment.class), List.of());

        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(underTest, "upgradeIndividualTenants"))
                .isInstanceOf(RuntimeException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
