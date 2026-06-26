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
/**
 * Domain layer for the Client bounded context.
 *
 * <h2>Shared-DB Read Strategy</h2> During the migration period, this service uses the same physical database as the
 * monolith (shared-DB read). The Client entity ({@code m_client}) and related tables remain in fineract-core as the
 * canonical domain model. This module adds:
 * <ul>
 * <li>Local JPA repository interfaces for cross-context entities (Staff, Group)</li>
 * <li>Service-specific repository extensions if needed</li>
 * </ul>
 *
 * <h2>Future: Separate DB</h2> When the service moves to its own database:
 * <ol>
 * <li>Run the Liquibase changelog in {@code db/changelog/} to create the schema</li>
 * <li>Backfill data from the monolith DB</li>
 * <li>Replace shared-DB adapters with remote-call adapters (HTTP/gRPC)</li>
 * <li>Replace soft FK references with eventual consistency</li>
 * </ol>
 */
package org.apache.fineract.portfolio.client.domain;
