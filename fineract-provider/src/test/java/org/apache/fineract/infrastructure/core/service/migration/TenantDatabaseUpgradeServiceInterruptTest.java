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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class TenantDatabaseUpgradeServiceInterruptTest {

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void restoresInterruptFlagAndShutsDownExecutor() throws Exception {
        Future<String> future = mock(Future.class);
        InterruptedException interruption = new InterruptedException("interrupted");
        when(future.get()).thenThrow(interruption);
        TenantDetailsService tenants = mock(TenantDetailsService.class);
        when(tenants.findAllTenants()).thenReturn(List.of(mock(FineractPlatformTenant.class)));
        TenantDatabaseUpgradeService service = mock(TenantDatabaseUpgradeService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "tenantDetailsService", tenants);
        FineractProperties properties = new FineractProperties();
        properties.setTaskExecutor(new FineractProperties.FineractTaskExecutor());
        ReflectionTestUtils.setField(service, "fineractProperties", properties);
        try (MockedConstruction<ThreadPoolTaskExecutor> executors = mockConstruction(ThreadPoolTaskExecutor.class,
                (executor, context) -> when(executor.submit(any(Callable.class))).thenReturn(future))) {
            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> ReflectionTestUtils.invokeMethod(service, "upgradeIndividualTenants"));
            assertTrue(Thread.interrupted());
            org.junit.jupiter.api.Assertions.assertEquals("Tenant upgrades had exceptions", exception.getMessage());
            verify(executors.constructed().get(0)).shutdown();
        }
    }
}
