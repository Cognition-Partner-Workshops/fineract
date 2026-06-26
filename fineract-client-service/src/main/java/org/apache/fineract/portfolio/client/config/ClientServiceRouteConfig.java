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
package org.apache.fineract.portfolio.client.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway route configuration for the Client Service.
 * <p>
 * Routes forwarded to this service (via reverse proxy or in-process delegation):
 * <ul>
 * <li>GET/POST/PUT/DELETE /fineract-provider/api/v1/clients/**</li>
 * <li>GET/POST/PUT/DELETE /fineract-provider/api/v1/client/**</li>
 * <li>GET /fineract-provider/api/v1/self/clients/**</li>
 * </ul>
 * <p>
 * Auth pass-through: the Authorization header (Basic or Bearer) and Fineract-Platform-TenantId header are forwarded
 * unchanged. The service validates credentials using the same PlatformSecurityContext that authenticates against the
 * shared tenant DB's m_appuser table.
 * <p>
 * When deployed as an in-process module (within fineract-war), no external gateway is needed — JAX-RS resource
 * registration handles routing internally.
 * <p>
 * For standalone deployment, configure the reverse proxy (nginx example):
 *
 * <pre>
 * location ~ ^/fineract-provider/api/v1/(clients|client|self/clients) {
 *     proxy_pass http://client-service:8443;
 *     proxy_set_header Authorization $http_authorization;
 *     proxy_set_header Fineract-Platform-TenantId $http_fineract_platform_tenantid;
 * }
 * </pre>
 */
@Configuration
public class ClientServiceRouteConfig {

    @Value("${fineract.client-service.standalone:false}")
    private boolean standaloneMode;

    public boolean isStandaloneMode() {
        return standaloneMode;
    }
}
