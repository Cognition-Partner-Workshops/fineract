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
package org.apache.fineract.accounting.closure.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import org.apache.fineract.accounting.AccountingTestSupport;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.closure.domain.GLClosureRepository;
import org.apache.fineract.accounting.closure.exception.GLClosureDuplicateException;
import org.apache.fineract.accounting.closure.exception.GLClosureInvalidDeleteException;
import org.apache.fineract.accounting.closure.exception.GLClosureInvalidException;
import org.apache.fineract.accounting.closure.exception.GLClosureNotFoundException;
import org.apache.fineract.accounting.closure.serialization.GLClosureCommandFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GLClosureWritePlatformServiceTest extends AccountingTestSupport {

    @Mock
    private GLClosureRepository repository;
    @Mock
    private OfficeRepositoryWrapper offices;
    @Mock
    private Office office;
    @Captor
    private ArgumentCaptor<GLClosure> saved;
    private GLClosureWritePlatformServiceJpaRepositoryImpl service;

    @BeforeEach
    void setUp() {
        service = new GLClosureWritePlatformServiceJpaRepositoryImpl(repository, offices,
                new GLClosureCommandFromApiJsonDeserializer(jsonHelper));
    }

    private JsonCommand createCommand(LocalDate date) {
        return command("{\"officeId\":1,\"closingDate\":\"" + date
                + "\",\"dateFormat\":\"yyyy-MM-dd\",\"locale\":\"en\",\"comments\":\"  close  \"}");
    }

    @Test
    void futureDateIsRejectedBeforeRepositoryLookupOrSave() {
        when(offices.findOneWithNotFoundDetection(1L)).thenReturn(office);
        GLClosureInvalidException error = assertThrows(GLClosureInvalidException.class,
                () -> service.createGLClosure(createCommand(BUSINESS_DATE.plusDays(1))));
        assertEquals("error.msg.glclosure.invalid.future.date", error.getGlobalisationMessageCode());
        verifyNoInteractions(repository);
    }

    @Test
    void dateBeforeLatestClosureIsRejected() {
        when(offices.findOneWithNotFoundDetection(1L)).thenReturn(office);
        when(repository.getLatestGLClosureByBranch(1L)).thenReturn(new GLClosure(office, BUSINESS_DATE.minusDays(1), null));
        GLClosureInvalidException error = assertThrows(GLClosureInvalidException.class,
                () -> service.createGLClosure(createCommand(BUSINESS_DATE.minusDays(2))));
        assertEquals("error.msg.glclosure.invalid.accounting.closed", error.getGlobalisationMessageCode());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void businessDateAndEqualLatestDateAreAllowedAndSavedContentsAreNormalized() {
        when(offices.findOneWithNotFoundDetection(1L)).thenReturn(office);
        when(repository.getLatestGLClosureByBranch(1L)).thenReturn(new GLClosure(office, BUSINESS_DATE, null));
        service.createGLClosure(createCommand(BUSINESS_DATE));
        verify(repository).saveAndFlush(saved.capture());
        assertSame(office, saved.getValue().getOffice());
        assertEquals(BUSINESS_DATE, saved.getValue().getClosingDate());
        assertEquals("close", saved.getValue().getComments());
        assertFalse(saved.getValue().isDeleted());
    }

    @Test
    void firstClosureDoesNotRequireAnExistingClosure() {
        when(offices.findOneWithNotFoundDetection(1L)).thenReturn(office);
        service.createGLClosure(createCommand(BUSINESS_DATE.minusDays(1)));
        verify(repository).saveAndFlush(saved.capture());
        assertEquals(BUSINESS_DATE.minusDays(1), saved.getValue().getClosingDate());
    }

    @Test
    void cannotDeleteClosureBeforeLatest() {
        when(office.getId()).thenReturn(1L);
        GLClosure old = new GLClosure(office, BUSINESS_DATE.minusDays(1), null);
        when(repository.findById(4L)).thenReturn(Optional.of(old));
        when(repository.getLatestGLClosureByBranch(1L)).thenReturn(new GLClosure(office, BUSINESS_DATE, null));
        GLClosureInvalidDeleteException error = assertThrows(GLClosureInvalidDeleteException.class, () -> service.deleteGLClosure(4L));
        assertEquals("error.msg.glclosure.invalid.delete", error.getGlobalisationMessageCode());
        verify(repository, never()).delete(any(GLClosure.class));
    }

    @Test
    void latestClosureCanBeDeleted() {
        when(office.getId()).thenReturn(1L);
        GLClosure latest = new GLClosure(office, BUSINESS_DATE, null);
        when(repository.findById(4L)).thenReturn(Optional.of(latest));
        when(repository.getLatestGLClosureByBranch(1L)).thenReturn(latest);
        service.deleteGLClosure(4L);
        verify(repository).delete(latest);
    }

    @Test
    void missingClosureCannotBeUpdatedOrDeleted() {
        assertThrows(GLClosureNotFoundException.class, () -> service.deleteGLClosure(4L));
        assertThrows(GLClosureNotFoundException.class, () -> service.updateGLClosure(4L, command("{\"comments\":\"new\"}")));
        verify(repository, never()).delete(any(GLClosure.class));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updatePersistsOnlyActualCommentChanges() {
        when(office.getId()).thenReturn(1L);
        GLClosure closure = new GLClosure(office, BUSINESS_DATE, "old");
        when(repository.findById(4L)).thenReturn(Optional.of(closure));
        service.updateGLClosure(4L, command("{\"comments\":\"old\"}"));
        verify(repository, never()).saveAndFlush(any());
        service.updateGLClosure(4L, command("{\"comments\":\"new\"}"));
        verify(repository).saveAndFlush(closure);
        assertEquals("new", closure.getComments());
        assertEquals(BUSINESS_DATE, closure.getClosingDate());
    }

    @Test
    void duplicateOfficeDateIsTranslatedToDomainError() {
        when(offices.findOneWithNotFoundDetection(1L)).thenReturn(office);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("office_id_closing_date"));
        GLClosureDuplicateException error = assertThrows(GLClosureDuplicateException.class,
                () -> service.createGLClosure(createCommand(BUSINESS_DATE)));
        assertTrue(error.getGlobalisationMessageCode().contains("duplicate"));
    }
}
