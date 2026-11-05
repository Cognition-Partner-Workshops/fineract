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
package org.apache.fineract.infrastructure.documentmanagement.retention;

import java.util.List;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientIdentifier;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierRepository;
import org.springframework.stereotype.Service;

@Service
public class IdentityDocumentRetentionPolicy {

    private final ClientIdentifierRepository identifiers;
    private final DocumentRepository documents;

    public IdentityDocumentRetentionPolicy(ClientIdentifierRepository identifiers, DocumentRepository documents) {
        this.identifiers = identifiers;
        this.documents = documents;
    }

    public void validateContentRemoval(Document document) {
        if (!"client_identifiers".equalsIgnoreCase(document.getParentEntityType())) {
            return;
        }
        if (document.getParentEntityId() == null) {
            throw retained();
        }
        ClientIdentifier identifier = identifiers.findById(document.getParentEntityId())
                .orElseThrow(IdentityDocumentRetentionPolicy::retained);
        validateClient(identifier.client());
    }

    public List<Document> documentsForIdentifierDeletion(ClientIdentifier identifier) {
        List<Document> attachments = documents.findAll((root, query, builder) -> builder.and(
                builder.equal(builder.lower(root.<String>get("parentEntityType")), "client_identifiers"),
                builder.equal(root.get("parentEntityId"), identifier.getId())));
        if (!attachments.isEmpty()) {
            validateClient(identifier.client());
        }
        return attachments;
    }

    private void validateClient(Client client) {
        if (client == null || !client.isClosed() || client.getClosureDate() == null
                || DateUtils.getLocalDateOfTenant().isBefore(client.getClosureDate().plusYears(7))) {
            throw retained();
        }
    }

    private static GeneralPlatformDomainRuleException retained() {
        return new GeneralPlatformDomainRuleException("error.msg.identity.document.retained",
                "Identity documents must be retained until at least seven years after client closure; an unknown client or closure date prevents removal.");
    }
}
