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

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Client Service module.
 * <p>
 * Gateway routing strategy: when deployed within the monolith WAR, this module registers its API resources alongside
 * the existing ones. The fineract-provider Jersey configuration picks up the @Path-annotated resources automatically.
 * <p>
 * For standalone deployment, a reverse proxy (nginx/Spring Cloud Gateway) routes:
 * <ul>
 * <li>/fineract-provider/api/v1/clients/** → client-service</li>
 * <li>/fineract-provider/api/v1/client/** → client-service</li>
 * <li>/fineract-provider/api/v1/self/clients/** → client-service</li>
 * </ul>
 * Auth/token pass-through is achieved by forwarding the Authorization header and Fineract-Platform-TenantId header
 * unchanged.
 */
@Configuration
@ComponentScan(basePackages = { "org.apache.fineract.portfolio.client.adapter", "org.apache.fineract.portfolio.client.service",
        "org.apache.fineract.portfolio.client.api" })
public class ClientServiceGatewayConfig {}
