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
package org.apache.fineract.fundservice.data;

/**
 * Read-model DTO for a fund, mirroring the monolith {@code FundData} contract.
 */
public final class FundData {

    private final Long id;
    private final String name;
    private final String externalId;

    private FundData(final Long id, final String name, final String externalId) {
        this.id = id;
        this.name = name;
        this.externalId = externalId;
    }

    public static FundData instance(final Long id, final String name, final String externalId) {
        return new FundData(id, name, externalId);
    }

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getExternalId() {
        return this.externalId;
    }
}
