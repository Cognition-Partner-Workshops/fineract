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
package org.apache.fineract.portfolio.client.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.documentmanagement.command.DocumentCommand;
import org.apache.fineract.infrastructure.documentmanagement.contentrepository.ContentRepository;
import org.apache.fineract.infrastructure.documentmanagement.contentrepository.ContentRepositoryFactory;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.domain.StorageType;
import org.apache.fineract.infrastructure.documentmanagement.retention.IdentityDocumentRetentionPolicy;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientIdentifier;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientIdentifierNotFoundException;
import org.apache.fineract.portfolio.client.serialization.ClientIdentifierCommandFromApiJsonDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class ClientIdentifierRetentionTest {

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final ClientIdentifierRepository identifiers = mock(ClientIdentifierRepository.class);
    private final ClientRepositoryWrapper clients = mock(ClientRepositoryWrapper.class);
    private final Client client = mock(Client.class);
    private final ClientIdentifier identifier = mock(ClientIdentifier.class, CALLS_REAL_METHODS);
    private final LocalDate today = LocalDate.of(2026, 11, 5);
    private AnnotationConfigApplicationContext beans;
    private MockedStatic<DateUtils> dates;
    private ClientIdentifierWritePlatformServiceJpaRepositoryImpl service;

    @BeforeEach
    void setup() {
        dates = mockStatic(DateUtils.class);
        dates.when(DateUtils::getLocalDateOfTenant).thenReturn(today);
        ReflectionTestUtils.setField(identifier, "client", client);
        ReflectionTestUtils.setField(identifier, "id", 4L);
        when(identifiers.findById(4L)).thenReturn(Optional.of(identifier));
        when(clients.findOneWithNotFoundDetection(22L)).thenReturn(client);
        when(client.getId()).thenReturn(22L);
        when(documents.count(org.mockito.ArgumentMatchers.<Specification<Document>>any())).thenReturn(1L);
        beans = new AnnotationConfigApplicationContext();
        beans.registerBean(DocumentRepository.class, () -> documents);
        beans.registerBean(ClientIdentifierRepository.class, () -> identifiers);
        beans.registerBean(ClientRepositoryWrapper.class, () -> clients);
        beans.registerBean(PlatformSecurityContext.class, () -> mock(PlatformSecurityContext.class));
        beans.registerBean(CodeValueRepositoryWrapper.class, () -> mock(CodeValueRepositoryWrapper.class));
        beans.registerBean(ClientIdentifierCommandFromApiJsonDeserializer.class,
                () -> mock(ClientIdentifierCommandFromApiJsonDeserializer.class));
        beans.scan("org.apache.fineract.infrastructure.documentmanagement.retention");
        beans.register(ClientIdentifierWritePlatformServiceJpaRepositoryImpl.class);
        beans.refresh();
        service = beans.getBean(ClientIdentifierWritePlatformServiceJpaRepositoryImpl.class);
    }

    @AfterEach
    void cleanup() {
        beans.close();
        dates.close();
    }

    @Test
    void retainedDocumentsPreventIdentifierDeletion() {
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.deleteClientIdentifier(22L, 4L, 1L));
        verify(identifiers, never()).delete(any(ClientIdentifier.class));
    }

    @Test
    void recentlyClosedClientsStillRetainTheirIdentifiers() {
        when(client.isClosed()).thenReturn(true);
        when(client.getClosureDate()).thenReturn(today.minusYears(7).plusDays(1));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.deleteClientIdentifier(22L, 4L, 1L));
        verify(identifiers, never()).delete(any(ClientIdentifier.class));
    }

    @Test
    void identifiersWithoutDocumentsRemainDeletable() {
        when(documents.count(org.mockito.ArgumentMatchers.<Specification<Document>>any())).thenReturn(0L);
        service.deleteClientIdentifier(22L, 4L, 1L);
        verify(identifiers).delete(identifier);
    }

    @Test
    void expiredDocumentsStillPreventIdentifierDeletion() {
        when(client.isClosed()).thenReturn(true);
        when(client.getClosureDate()).thenReturn(today.minusYears(7));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.deleteClientIdentifier(22L, 4L, 1L));
        verify(identifiers, never()).delete(any(ClientIdentifier.class));
    }

    @Test
    void staffDeleteExpiredDocumentsBeforeDeletingTheirIdentifier() {
        when(client.isClosed()).thenReturn(true);
        when(client.getClosureDate()).thenReturn(today.minusYears(7));
        Document document = Document.createNew("client_identifiers", 4L, "ID", "id.pdf", 2L, "application/pdf", "ID", "/expired",
                StorageType.FILE_SYSTEM);
        ReflectionTestUtils.setField(document, "id", 8L);
        AtomicBoolean attached = new AtomicBoolean(true);
        when(documents.count(org.mockito.ArgumentMatchers.<Specification<Document>>any())).thenAnswer(i -> attached.get() ? 1L : 0L);
        when(documents.findById(8L)).thenReturn(Optional.of(document));
        doAnswer(i -> {
            attached.set(false);
            return null;
        }).when(documents).delete(document);
        ContentRepositoryFactory stores = mock(ContentRepositoryFactory.class);
        ContentRepository store = mock(ContentRepository.class);
        when(stores.getRepository(StorageType.FILE_SYSTEM)).thenReturn(store);
        var documentWrites = new DocumentWritePlatformServiceJpaRepositoryImpl(mock(PlatformSecurityContext.class), documents, stores,
                mock(BusinessEventNotifierService.class), beans.getBean(IdentityDocumentRetentionPolicy.class));

        assertThrows(GeneralPlatformDomainRuleException.class, () -> service.deleteClientIdentifier(22L, 4L, 1L));
        verify(identifiers, never()).delete(any(ClientIdentifier.class));
        documentWrites.deleteDocument(new DocumentCommand(Set.of(), 8L, "client_identifiers", 4L, null, null, null, null, null, null));
        service.deleteClientIdentifier(22L, 4L, 1L);
        var order = inOrder(documents, store, identifiers);
        order.verify(documents).delete(document);
        order.verify(store).deleteFile("/expired");
        order.verify(identifiers).delete(identifier);
    }

    @Test
    void identifiersCannotBeDeletedThroughAnotherClient() {
        when(documents.count(org.mockito.ArgumentMatchers.<Specification<Document>>any())).thenReturn(0L);
        Client other = mock(Client.class);
        when(other.getId()).thenReturn(23L);
        ReflectionTestUtils.setField(identifier, "client", other);
        assertThrows(ClientIdentifierNotFoundException.class, () -> service.deleteClientIdentifier(22L, 4L, 1L));
        verify(identifiers, never()).delete(any(ClientIdentifier.class));
    }

    @Test
    void identifiersWithoutAnOwnerCannotBeDeleted() {
        when(documents.count(org.mockito.ArgumentMatchers.<Specification<Document>>any())).thenReturn(0L);
        ReflectionTestUtils.setField(identifier, "client", null);
        assertThrows(ClientIdentifierNotFoundException.class, () -> service.deleteClientIdentifier(22L, 4L, 1L));
        verify(identifiers, never()).delete(any(ClientIdentifier.class));
    }
}
