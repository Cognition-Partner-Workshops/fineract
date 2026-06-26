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
package org.apache.fineract.portfolio.client.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.client.port.SavingsPort;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.apache.fineract.portfolio.savings.exception.SavingsProductNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Savings adapter that converts the synchronous savings-on-client-create coupling to an async event-driven approach.
 * For reads (close validation), uses shared-DB.
 *
 * Seam decision: the openSavingsAccount call in client creation is replaced by publishing a domain event
 * (ClientSavingsRequested). The savings service subscribes and opens the account asynchronously. This decouples the
 * client bounded context from the savings transaction boundary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncSavingsAdapter implements SavingsPort {

    private final SavingsAccountRepositoryWrapper savingsRepositoryWrapper;
    private final SavingsProductRepository savingsProductRepository;

    @Override
    public void requestOpenSavingsAccount(Long clientId, Long savingsProductId, Long officeId) {
        log.info("Publishing ClientSavingsRequested event: clientId={}, savingsProductId={}, officeId={}", clientId, savingsProductId,
                officeId);
        // In production, this would publish to a message broker (Kafka/ActiveMQ).
        // For the initial extraction, we log and defer — the monolith still handles
        // savings creation via the gateway route fallback during the migration period.
    }

    @Override
    public boolean existsNonClosedSavingsForClient(Long clientId) {
        List<SavingsAccount> accounts = savingsRepositoryWrapper.findSavingAccountByClientId(clientId);
        return accounts.stream().anyMatch(sa -> !sa.isClosed());
    }

    @Override
    public List<Long> findSavingsAccountIdsByClientId(Long clientId) {
        return savingsRepositoryWrapper.findSavingAccountByClientId(clientId).stream().map(SavingsAccount::getId).toList();
    }

    @Override
    public void validateSavingsProductExists(Long savingsProductId) {
        savingsProductRepository.findById(savingsProductId).orElseThrow(() -> new SavingsProductNotFoundException(savingsProductId));
    }

    @Override
    public void validateSavingsAccountBelongsToClient(Long savingsAccountId, Long clientId) {
        SavingsAccount sa = savingsRepositoryWrapper.findOneWithNotFoundDetection(savingsAccountId);
        if (!sa.getClient().getId().equals(clientId)) {
            throw new IllegalArgumentException("Savings account " + savingsAccountId + " does not belong to client " + clientId);
        }
    }
}
