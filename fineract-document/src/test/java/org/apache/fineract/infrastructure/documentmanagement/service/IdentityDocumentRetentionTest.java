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
package org.apache.fineract.infrastructure.documentmanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.documentmanagement.command.DocumentCommand;
import org.apache.fineract.infrastructure.documentmanagement.contentrepository.ContentRepository;
import org.apache.fineract.infrastructure.documentmanagement.contentrepository.ContentRepositoryFactory;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.domain.StorageType;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientIdentifier;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityDocumentRetentionTest {

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final ClientIdentifierRepository identifiers = mock(ClientIdentifierRepository.class);
    private final ContentRepositoryFactory stores = mock(ContentRepositoryFactory.class);
    private final ContentRepository store = mock(ContentRepository.class);
    private final Client client = mock(Client.class);
    private final LocalDate today = LocalDate.of(2026, 11, 5);
    private AnnotationConfigApplicationContext beans;
    private MockedStatic<DateUtils> dates;
    private DocumentWritePlatformServiceJpaRepositoryImpl service;
    private Document document;

    @BeforeEach
    void setup() {
        dates = mockStatic(DateUtils.class);
        dates.when(DateUtils::getLocalDateOfTenant).thenReturn(today);
        ClientIdentifier identifier = mock(ClientIdentifier.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(identifier, "client", client);
        when(identifiers.findById(4L)).thenReturn(Optional.of(identifier));
        document = Document.createNew("client_identifiers", 4L, "ID", "id.pdf", 2L, "application/pdf", "ID", "/old",
                StorageType.FILE_SYSTEM);
        ReflectionTestUtils.setField(document, "id", 8L);
        when(documents.findById(8L)).thenReturn(Optional.of(document));
        when(stores.getRepository()).thenReturn(store);
        when(stores.getRepository(any(StorageType.class))).thenReturn(store);
        when(store.saveFile(any(), any())).thenReturn("/new");
        when(store.getStorageType()).thenReturn(StorageType.FILE_SYSTEM);
        beans = new AnnotationConfigApplicationContext();
        beans.registerBean(DocumentRepository.class, () -> documents);
        beans.registerBean(ClientIdentifierRepository.class, () -> identifiers);
        beans.registerBean(ContentRepositoryFactory.class, () -> stores);
        beans.registerBean(PlatformSecurityContext.class, () -> mock(PlatformSecurityContext.class));
        beans.registerBean(BusinessEventNotifierService.class, () -> mock(BusinessEventNotifierService.class));
        beans.scan("org.apache.fineract.infrastructure.documentmanagement.retention");
        beans.register(DocumentWritePlatformServiceJpaRepositoryImpl.class);
        beans.refresh();
        service = beans.getBean(DocumentWritePlatformServiceJpaRepositoryImpl.class);
    }

    @AfterEach
    void cleanup() {
        beans.close();
        dates.close();
    }

    private DocumentCommand command(Set<String> changes) {
        return new DocumentCommand(changes, 8L, "clients", 99L, "ID", "new.pdf", 2L, "application/pdf", "description", "/new");
    }

    private void rejected(Runnable action) {
        assertThrows(GeneralPlatformDomainRuleException.class, action::run);
        verifyNoInteractions(stores, store);
        verify(documents, never()).delete(any(Document.class));
        verify(documents, never()).saveAndFlush(any());
        assertEquals("/old", document.getLocation());
    }

    @Test
    void activeClientDocumentCannotBeDeletedEvenUsingAnotherParentInTheCommand() {
        rejected(() -> service.deleteDocument(command(Set.of())));
    }

    @Test
    void closedClientMustWaitSevenYears() {
        when(client.isClosed()).thenReturn(true);
        when(client.getClosureDate()).thenReturn(today.minusYears(7).plusDays(1));
        rejected(() -> service.deleteDocument(command(Set.of())));
    }

    @Test
    void missingClosureDateFailsClosed() {
        when(client.isClosed()).thenReturn(true);
        rejected(() -> service.deleteDocument(command(Set.of())));
    }

    @Test
    void reopenedClientsCannotUseAnOldClosureDateToRemoveDocuments() {
        when(client.getClosureDate()).thenReturn(today.minusYears(8));
        rejected(() -> service.deleteDocument(command(Set.of())));
    }

    @Test
    void replacementCannotWriteOrDeleteAnyFileDuringRetention() {
        rejected(() -> service.updateDocument(command(Set.of("fileName")), new ByteArrayInputStream(new byte[] { 1 })));
    }

    @Test
    void locationOnlyReplacementIsAlsoProtected() {
        rejected(() -> service.updateDocument(command(Set.of("location")), null));
    }

    @Test
    void missingIdentifierOwnerFailsClosed() {
        when(identifiers.findById(4L)).thenReturn(Optional.empty());
        rejected(() -> service.deleteDocument(command(Set.of())));
    }

    @Test
    void metadataCanChangeWithoutReplacingTheFile() {
        service.updateDocument(command(Set.of("name", "description")), null);
        verifyNoInteractions(stores, store);
        verify(documents).saveAndFlush(document);
        assertEquals("/old", document.getLocation());
    }

    @Test
    void deletionIsAllowedOnTheSevenYearAnniversary() {
        when(client.isClosed()).thenReturn(true);
        when(client.getClosureDate()).thenReturn(today.minusYears(7));
        service.deleteDocument(command(Set.of()));
        verify(documents).delete(document);
        verify(store).deleteFile("/old");
    }

    @Test
    void replacementIsAllowedAfterRetentionExpires() {
        when(client.isClosed()).thenReturn(true);
        when(client.getClosureDate()).thenReturn(today.minusYears(7).minusDays(1));
        service.updateDocument(command(Set.of("fileName")), new ByteArrayInputStream(new byte[] {1}));
        verify(store).saveFile(any(), any());
        verify(store).deleteFile("/old");
    }

    @Test
    void unrelatedDocumentsKeepTheirExistingDeletionBehavior() {
        document.setParentEntityType("loans");
        service.deleteDocument(command(Set.of()));
        verify(store).deleteFile("/old");
    }
}
