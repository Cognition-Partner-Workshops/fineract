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

import java.util.UUID;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.port.AccountNumberPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Fallback account number generation adapter for standalone deployment. Uses the entity ID when available, or a UUID
 * for pre-persist clients. In the monolith WAR assembly, this bean is not registered because the full
 * AccountNumberGenerator from fineract-provider is available.
 */
@Component
@ConditionalOnMissingBean(name = "accountNumberGenerator")
public class SharedDbAccountNumberAdapter implements AccountNumberPort {

    @Override
    public String generateClientAccountNumber(Client client) {
        if (client.getId() != null) {
            return String.format("%09d", client.getId());
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    }
}
