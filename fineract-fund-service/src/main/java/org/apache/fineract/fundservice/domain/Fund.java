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
package org.apache.fineract.fundservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Fund JPA entity for the carved-out Fund microservice.
 *
 * <p>Self-contained, plain JPA mapping of the {@code m_fund} table. It deliberately uses a plain generated identity and
 * does not extend any monolith base class or reference any shared monolith infrastructure.
 */
@Entity
@Table(name = "m_fund", uniqueConstraints = {
        @UniqueConstraint(name = "fund_name_org", columnNames = { "name" }),
        @UniqueConstraint(name = "fund_externalid_org", columnNames = { "external_id" }) })
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The physical column is VARCHAR(255); input is capped at 100 chars by the (T3) validator.
    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "external_id", length = 100)
    private String externalId;

    protected Fund() {
        // JPA
    }

    public Fund(final String name, final String externalId) {
        this.name = name;
        this.externalId = externalId;
    }

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getExternalId() {
        return this.externalId;
    }

    public void setExternalId(final String externalId) {
        this.externalId = externalId;
    }
}
