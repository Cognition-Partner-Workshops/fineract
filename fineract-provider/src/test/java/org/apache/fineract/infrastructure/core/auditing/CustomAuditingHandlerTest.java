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
package org.apache.fineract.infrastructure.core.auditing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.context.PersistentEntities;

public class CustomAuditingHandlerTest {

    @BeforeEach
    public void init() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.now(ZoneId.of("Asia/Kolkata")))));
    }

    @AfterEach
    public void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    public void markCreated() {
        MappingContext mappingContext = Mockito.mock(MappingContext.class);
        CustomAuditingHandler testInstance = new CustomAuditingHandler(PersistentEntities.of(mappingContext));
        AbstractAuditableWithUTCDateTimeCustom<Long> targetObject = Mockito.spy(AbstractAuditableWithUTCDateTimeCustom.class);
        targetObject = testInstance.markCreated(targetObject);
        OffsetDateTime now = DateUtils.getAuditOffsetDateTime();

        assertTrue(targetObject.getCreatedDate().isPresent());
        assertEquals(now.getYear(), targetObject.getCreatedDate().get().getYear());
        assertEquals(now.getMonth(), targetObject.getCreatedDate().get().getMonth());
        assertEquals(now.getDayOfMonth(), targetObject.getCreatedDate().get().getDayOfMonth());
        assertEquals(now.getHour(), targetObject.getCreatedDate().get().getHour());
        assertEquals(now.getMinute(), targetObject.getCreatedDate().get().getMinute());
    }

    @Test
    public void markModified() {
        MappingContext mappingContext = Mockito.mock(MappingContext.class);
        CustomAuditingHandler testInstance = new CustomAuditingHandler(PersistentEntities.of(mappingContext));
        AbstractAuditableWithUTCDateTimeCustom<Long> targetObject = Mockito.spy(AbstractAuditableWithUTCDateTimeCustom.class);
        targetObject = testInstance.markModified(targetObject);
        OffsetDateTime now = DateUtils.getAuditOffsetDateTime();

        assertTrue(targetObject.getLastModifiedDate().isPresent());
        assertEquals(now.getYear(), targetObject.getLastModifiedDate().get().getYear());
        assertEquals(now.getMonth(), targetObject.getLastModifiedDate().get().getMonth());
        assertEquals(now.getDayOfMonth(), targetObject.getLastModifiedDate().get().getDayOfMonth());
        assertEquals(now.getHour(), targetObject.getLastModifiedDate().get().getHour());
        assertEquals(now.getMinute(), targetObject.getLastModifiedDate().get().getMinute());
    }

    @Test
    public void markModifiedOldDateTimeProvider() {
        MappingContext mappingContext = Mockito.mock(MappingContext.class);
        CustomAuditingHandler testInstance = new CustomAuditingHandler(PersistentEntities.of(mappingContext));
        AbstractAuditableCustom targetObject = Mockito.spy(AbstractAuditableCustom.class);
        targetObject = testInstance.markModified(targetObject);
        LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();

        assertTrue(targetObject.getLastModifiedDate().isPresent());
        assertEquals(now.getYear(), targetObject.getLastModifiedDate().get().getYear());
        assertEquals(now.getMonth(), targetObject.getLastModifiedDate().get().getMonth());
        assertEquals(now.getDayOfMonth(), targetObject.getLastModifiedDate().get().getDayOfMonth());
        assertEquals(now.getHour(), targetObject.getLastModifiedDate().get().getHour());
        assertEquals(now.getMinute(), targetObject.getLastModifiedDate().get().getMinute());
    }

    @Test
    public void markCreatedOldDateTimeProvider() {
        MappingContext mappingContext = Mockito.mock(MappingContext.class);
        CustomAuditingHandler testInstance = new CustomAuditingHandler(PersistentEntities.of(mappingContext));
        AbstractAuditableCustom targetObject = Mockito.spy(AbstractAuditableCustom.class);
        targetObject = testInstance.markCreated(targetObject);
        LocalDateTime now = DateUtils.getLocalDateTimeOfSystem();

        assertTrue(targetObject.getCreatedDate().isPresent());
        assertEquals(now.getYear(), targetObject.getCreatedDate().get().getYear());
        assertEquals(now.getMonth(), targetObject.getCreatedDate().get().getMonth());
        assertEquals(now.getDayOfMonth(), targetObject.getCreatedDate().get().getDayOfMonth());
        assertEquals(now.getHour(), targetObject.getCreatedDate().get().getHour());
        assertEquals(now.getMinute(), targetObject.getCreatedDate().get().getMinute());
    }

    @Test
    public void reusesAuditingHandler() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("jpaMappingContext", Mockito.mock(MappingContext.class));
        beanFactory.registerSingleton("auditorAware", (AuditorAware<Long>) () -> Optional.of(42L));
        new JpaAuditingHandlerRegistrar().registerBeanDefinitions(null, beanFactory);

        assertSame(beanFactory.getBean("jpaAuditingHandler"), beanFactory.getBean("jpaAuditingHandler"));
    }

    @Test
    public void preservesDateProvidersDuringConcurrentAuditing() throws Exception {
        CustomAuditingHandler handler = new CustomAuditingHandler(PersistentEntities.of(Mockito.mock(MappingContext.class)));
        CountDownLatch ready = new CountDownLatch(2);
        handler.setAuditorAware(() -> {
            ready.countDown();
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return Optional.of(42L);
        });
        OffsetDateTime utcNow = OffsetDateTime.parse("2025-03-01T10:00:00Z");
        LocalDateTime localNow = LocalDateTime.parse("2025-03-02T11:00:00");

        try (var executor = Executors.newFixedThreadPool(2)) {
            var utc = executor.submit(() -> {
                try (var dates = Mockito.mockStatic(DateUtils.class)) {
                    dates.when(DateUtils::getAuditOffsetDateTime).thenReturn(utcNow);
                    dates.when(DateUtils::getLocalDateTimeOfSystem).thenReturn(localNow);
                    return handler.markCreated(Mockito.spy(AbstractAuditableWithUTCDateTimeCustom.class));
                }
            });
            var local = executor.submit(() -> {
                try (var dates = Mockito.mockStatic(DateUtils.class)) {
                    dates.when(DateUtils::getAuditOffsetDateTime).thenReturn(utcNow);
                    dates.when(DateUtils::getLocalDateTimeOfSystem).thenReturn(localNow);
                    return handler.markModified(Mockito.spy(AbstractAuditableCustom.class));
                }
            });
            var utcEntity = utc.get(20, TimeUnit.SECONDS);
            var localEntity = local.get(20, TimeUnit.SECONDS);
            assertEquals(utcNow, utcEntity.getCreatedDate().orElseThrow());
            assertEquals(42L, utcEntity.getCreatedBy().orElseThrow());
            assertEquals(localNow, localEntity.getLastModifiedDate().orElseThrow());
            assertEquals(42L, localEntity.getLastModifiedBy().orElseThrow());
            assertTrue(localEntity.getCreatedDate().isEmpty());
        }
    }

    @Test
    public void preservesAuditingOptionsForBothDateProviders() {
        CustomAuditingHandler handler = new CustomAuditingHandler(PersistentEntities.of(Mockito.mock(MappingContext.class)));
        handler.setAuditorAware(() -> Optional.of(42L));
        handler.setModifyOnCreation(false);
        handler.setDateTimeForNow(false);
        var utc = handler.markCreated(Mockito.spy(AbstractAuditableWithUTCDateTimeCustom.class));
        var local = handler.markCreated(Mockito.spy(AbstractAuditableCustom.class));

        assertEquals(42L, utc.getCreatedBy().orElseThrow());
        assertEquals(42L, local.getCreatedBy().orElseThrow());
        assertTrue(utc.getLastModifiedBy().isEmpty());
        assertTrue(local.getLastModifiedBy().isEmpty());
        assertTrue(utc.getCreatedDate().isEmpty());
        assertTrue(local.getCreatedDate().isEmpty());
    }

    @Test
    public void rejectsNullSources() {
        CustomAuditingHandler handler = new CustomAuditingHandler(PersistentEntities.of(Mockito.mock(MappingContext.class)));
        assertThrows(IllegalArgumentException.class, () -> handler.markCreated(null));
        assertThrows(IllegalArgumentException.class, () -> handler.markModified(null));
    }
}
