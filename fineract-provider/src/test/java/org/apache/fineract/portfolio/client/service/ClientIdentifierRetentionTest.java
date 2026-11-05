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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.documentmanagement.command.DocumentCommand;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.retention.IdentityDocumentRetentionPolicy;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentWritePlatformService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientIdentifier;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
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
    private final DocumentWritePlatformService documentWrites = mock(DocumentWritePlatformService.class);
    private final List<Document> attachments = new ArrayList<>();
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
        Document attachment = mock(Document.class);
        when(attachment.getId()).thenReturn(8L);
        when(attachment.getParentEntityType()).thenReturn("client_identifiers");
        when(attachment.getParentEntityId()).thenReturn(4L);
        attachments.add(attachment);
        when(documents.findAll(org.mockito.ArgumentMatchers.<Specification<Document>>any())).thenAnswer(i -> List.copyOf(attachments));
        beans = new AnnotationConfigApplicationContext();
        beans.registerBean(DocumentRepository.class, () -> documents);
        beans.registerBean(DocumentWritePlatformService.class, () -> documentWrites);
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
        attachments.clear();
        service.deleteClientIdentifier(22L, 4L, 1L);
        verify(identifiers).delete(identifier);
    }

    @Test
    void identifiersBecomeDeletableAfterTheRetentionPeriod() {
        when(client.isClosed()).thenReturn(true);
        when(client.getClosureDate()).thenReturn(today.minusYears(7));
        service.deleteClientIdentifier(22L, 4L, 1L);
        verify(identifiers).delete(identifier);
    }

    @Test
    void expiredDocumentsAreRemovedWhileTheirIdentifierStillExists() {
        when(client.isClosed()).thenReturn(true);
        when(client.getClosureDate()).thenReturn(today.minusYears(7));
        doAnswer(invocation -> {
            DocumentCommand command = invocation.getArgument(0);
            beans.getBean(IdentityDocumentRetentionPolicy.class).validateContentRemoval(attachments.get(0));
            org.junit.jupiter.api.Assertions.assertEquals(8L, command.getId());
            org.junit.jupiter.api.Assertions.assertEquals(4L, command.getParentEntityId());
            org.junit.jupiter.api.Assertions.assertEquals("client_identifiers", command.getParentEntityType());
            attachments.clear();
            return null;
        }).when(documentWrites).deleteDocument(any(DocumentCommand.class));
        doAnswer(invocation -> {
            when(identifiers.findById(4L)).thenReturn(Optional.empty());
            return null;
        }).when(identifiers).delete(identifier);
        service.deleteClientIdentifier(22L, 4L, 1L);
        org.junit.jupiter.api.Assertions.assertTrue(attachments.isEmpty());
        var order = inOrder(documentWrites, identifiers);
        order.verify(documentWrites).deleteDocument(any(DocumentCommand.class));
        order.verify(identifiers).delete(identifier);
    }

    @Test
    void allExpiredDocumentsAreDeletedBeforeTheIdentifier() {
        when(client.isClosed()).thenReturn(true);
        when(client.getClosureDate()).thenReturn(today.minusYears(7).minusDays(1));
        Document second = mock(Document.class);
        when(second.getId()).thenReturn(9L);
        when(second.getParentEntityType()).thenReturn("client_identifiers");
        when(second.getParentEntityId()).thenReturn(4L);
        attachments.add(second);
        service.deleteClientIdentifier(22L, 4L, 1L);
        var order = inOrder(documentWrites, identifiers);
        order.verify(documentWrites).deleteDocument(org.mockito.ArgumentMatchers.argThat(command -> command.getId().equals(8L)));
        order.verify(documentWrites).deleteDocument(org.mockito.ArgumentMatchers.argThat(command -> command.getId().equals(9L)));
        order.verify(identifiers).delete(identifier);
    }

    @Test
    void aDocumentDeletionFailureKeepsTheIdentifier() {
        when(client.isClosed()).thenReturn(true);
        when(client.getClosureDate()).thenReturn(today.minusYears(7));
        org.mockito.Mockito.doThrow(new IllegalStateException("storage failure")).when(documentWrites)
                .deleteDocument(any(DocumentCommand.class));
        assertThrows(IllegalStateException.class, () -> service.deleteClientIdentifier(22L, 4L, 1L));
        verify(identifiers, never()).delete(any(ClientIdentifier.class));
    }
}
