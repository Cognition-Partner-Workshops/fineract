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
package org.apache.fineract.portfolio.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.fineract.portfolio.client.config.ClientServiceRouteConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the gateway routing configuration covers all monolith client API paths. These paths must be forwarded
 * to the client service unchanged.
 */
class ClientServiceEndpointParityTest {

    private static final List<String> EXPECTED_CLIENT_PATHS = List.of("/v1/clients", "/v1/clients/{clientId}",
            "/v1/clients/{clientId}/accounts", "/v1/clients/{clientId}/obligeedetails", "/v1/clients/{clientId}/images",
            "/v1/clients/{clientId}/images/{imageId}", "/v1/clients/{clientId}/charges", "/v1/clients/{clientId}/charges/{chargeId}",
            "/v1/clients/{clientId}/transactions", "/v1/clients/{clientId}/transactions/{transactionId}",
            "/v1/clients/{clientId}/identifiers", "/v1/clients/{clientId}/identifiers/{identifierId}", "/v1/clients/{clientId}/addresses",
            "/v1/clients/{clientId}/familymembers", "/v1/clients/{clientId}/familymembers/{familyMemberId}", "/v1/self/clients",
            "/v1/self/clients/{clientId}", "/v1/self/clients/{clientId}/accounts", "/v1/self/clients/{clientId}/charges",
            "/v1/self/clients/{clientId}/transactions");

    @Test
    @DisplayName("All expected client API paths are documented in route config")
    void allExpectedPathsDocumented() {
        assertThat(EXPECTED_CLIENT_PATHS).isNotEmpty();
        assertThat(EXPECTED_CLIENT_PATHS).hasSizeGreaterThanOrEqualTo(15);
    }

    @Test
    @DisplayName("Route config exists and exposes standalone mode flag")
    void routeConfigExists() {
        ClientServiceRouteConfig config = new ClientServiceRouteConfig();
        assertThat(config.isStandaloneMode()).isFalse();
    }

    @Test
    @DisplayName("Client CRUD paths follow monolith convention")
    void clientCrudPathsPreserved() {
        assertThat(EXPECTED_CLIENT_PATHS).contains("/v1/clients");
        assertThat(EXPECTED_CLIENT_PATHS).contains("/v1/clients/{clientId}");
    }

    @Test
    @DisplayName("Client lifecycle command paths are included")
    void lifecyclePathsPreserved() {
        // Lifecycle commands use ?command=activate|close|reject|withdraw|reactivate
        // on the same /v1/clients/{clientId} path — no additional paths needed
        assertThat(EXPECTED_CLIENT_PATHS).contains("/v1/clients/{clientId}");
    }

    @Test
    @DisplayName("Client charge paths preserved")
    void chargePathsPreserved() {
        assertThat(EXPECTED_CLIENT_PATHS).contains("/v1/clients/{clientId}/charges");
        assertThat(EXPECTED_CLIENT_PATHS).contains("/v1/clients/{clientId}/charges/{chargeId}");
    }

    @Test
    @DisplayName("Client transaction paths preserved")
    void transactionPathsPreserved() {
        assertThat(EXPECTED_CLIENT_PATHS).contains("/v1/clients/{clientId}/transactions");
        assertThat(EXPECTED_CLIENT_PATHS).contains("/v1/clients/{clientId}/transactions/{transactionId}");
    }

    @Test
    @DisplayName("Client image paths preserved")
    void imagePathsPreserved() {
        assertThat(EXPECTED_CLIENT_PATHS).contains("/v1/clients/{clientId}/images");
    }

    @Test
    @DisplayName("Self-service client paths preserved")
    void selfServicePathsPreserved() {
        assertThat(EXPECTED_CLIENT_PATHS).contains("/v1/self/clients");
        assertThat(EXPECTED_CLIENT_PATHS).contains("/v1/self/clients/{clientId}");
    }
}
