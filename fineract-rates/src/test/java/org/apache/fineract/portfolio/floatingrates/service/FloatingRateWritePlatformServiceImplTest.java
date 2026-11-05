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
package org.apache.fineract.portfolio.floatingrates.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import jakarta.persistence.PersistenceException;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.floatingrates.domain.FloatingRate;
import org.apache.fineract.portfolio.floatingrates.domain.FloatingRateRepositoryWrapper;
import org.apache.fineract.portfolio.floatingrates.exception.FloatingRateNotFoundException;
import org.apache.fineract.portfolio.floatingrates.serialization.FloatingRateDataValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FloatingRateWritePlatformServiceImplTest {

    @Mock
    private FloatingRateDataValidator validator;
    @Mock
    private FloatingRateRepositoryWrapper repository;
    @Mock
    private FloatingRate existingRate;
    @Mock
    private JsonCommand updateCommand;

    private FloatingRateWritePlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FloatingRateWritePlatformServiceImpl(validator, repository);
    }

    @Test
    void createValidatesPersistsAndReturnsNewResourceId() {
        JsonCommand command = command("{\"name\":\"Prime\"}");
        doAnswer(invocation -> {
            FloatingRate rate = invocation.getArgument(0);
            ReflectionTestUtils.setField(rate, "id", 42L);
            return null;
        }).when(repository).saveAndFlush(any(FloatingRate.class));

        CommandProcessingResult result = service.createFloatingRate(command);

        ArgumentCaptor<FloatingRate> captured = ArgumentCaptor.forClass(FloatingRate.class);
        verify(validator).validateForCreate(command.json());
        verify(repository).saveAndFlush(captured.capture());
        assertEquals("Prime", captured.getValue().getName());
        assertTrue(captured.getValue().isActive());
        assertEquals(42L, result.getResourceId());
    }

    @Test
    void updatePersistsChangesAndReturnsThem() {
        when(updateCommand.entityId()).thenReturn(42L);
        when(updateCommand.commandId()).thenReturn(9L);
        when(updateCommand.json()).thenReturn("{\"name\":\"Updated\"}");
        when(repository.findOneWithNotFoundDetection(42L)).thenReturn(existingRate);
        Map<String, Object> changes = Map.of("name", "Updated");
        when(existingRate.update(updateCommand)).thenReturn(changes);

        CommandProcessingResult result = service.updateFloatingRate(updateCommand);

        verify(validator).validateForUpdate(updateCommand.json(), existingRate);
        verify(repository).save(existingRate);
        assertEquals(42L, result.getResourceId());
        assertEquals(9L, result.getCommandId());
        assertEquals(changes, result.getChanges());
    }

    @Test
    void unchangedUpdateDoesNotSave() {
        when(updateCommand.entityId()).thenReturn(42L);
        when(repository.findOneWithNotFoundDetection(42L)).thenReturn(existingRate);
        when(existingRate.update(updateCommand)).thenReturn(Map.of());

        CommandProcessingResult result = service.updateFloatingRate(updateCommand);

        verify(repository, never()).save(any(FloatingRate.class));
        assertNull(result.getChanges());
    }

    @Test
    void updatePropagatesNotFoundBeforeValidation() {
        when(updateCommand.entityId()).thenReturn(42L);
        FloatingRateNotFoundException missing = new FloatingRateNotFoundException(42L);
        when(repository.findOneWithNotFoundDetection(42L)).thenThrow(missing);

        assertSame(missing, assertThrows(FloatingRateNotFoundException.class, () -> service.updateFloatingRate(updateCommand)));
        verifyNoInteractions(validator);
    }

    @Test
    void createMapsDuplicateNameToPlatformDataIntegrityException() {
        JsonCommand command = command("{\"name\":\"Prime\"}");
        doThrow(new DataIntegrityViolationException("duplicate unq_name")).when(repository).saveAndFlush(any(FloatingRate.class));

        PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> service.createFloatingRate(command));

        assertEquals("error.msg.floatingrates.duplicate.name", exception.getGlobalisationMessageCode());
        assertEquals("name", exception.getParameterName());
        assertEquals("Floating Rate with name `Prime` already exists", exception.getMessage());
    }

    @Test
    void updateMapsDuplicatePeriodToPlatformDataIntegrityException() {
        when(updateCommand.entityId()).thenReturn(42L);
        when(repository.findOneWithNotFoundDetection(42L)).thenReturn(existingRate);
        when(existingRate.update(updateCommand)).thenReturn(Map.of("ratePeriods", "changed"));
        doThrow(new DataIntegrityViolationException("duplicate unq_rate_period")).when(repository).save(existingRate);

        PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> service.updateFloatingRate(updateCommand));

        assertEquals("error.msg.floatingrates.duplicate.active.fromdate", exception.getGlobalisationMessageCode());
        assertEquals("fromdate", exception.getParameterName());
        assertEquals("Attempt to add multiple floating rate periods with same fromdate", exception.getMessage());
    }

    @Test
    void createMapsNestedPersistenceConstraintFailure() {
        JsonCommand command = command("{\"name\":\"Prime\"}");
        doThrow(new PersistenceException(new IllegalStateException(new IllegalArgumentException("unq_name")))).when(repository)
                .saveAndFlush(any(FloatingRate.class));

        PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> service.createFloatingRate(command));

        assertEquals("error.msg.floatingrates.duplicate.name", exception.getGlobalisationMessageCode());
    }

    @Test
    void createMapsPersistenceFailureWithSingleCause() {
        JsonCommand command = command("{\"name\":\"Prime\"}");
        doThrow(new PersistenceException(new IllegalArgumentException("unq_name"))).when(repository).saveAndFlush(any(FloatingRate.class));

        PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> service.createFloatingRate(command));
        assertEquals("error.msg.floatingrates.duplicate.name", exception.getGlobalisationMessageCode());
    }

    private static JsonCommand command(String json) {
        return JsonCommand.fromJsonElement(null, JsonParser.parseString(json), new FromJsonHelper());
    }
}
