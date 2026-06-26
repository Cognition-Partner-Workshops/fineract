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

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reproducible integration test for the database-per-service gate: it stands up a REAL PostgreSQL via Testcontainers,
 * lets the application's own Liquibase changelog create {@code m_fund}, and runs with Hibernate {@code ddl-auto=validate}
 * so the T1 entity is validated against the migrated table. It then asserts the physical schema (columns + the two named
 * unique constraints) and a save/find round-trip through the repository.
 */
@SpringBootTest
@Testcontainers
class FundSchemaMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16").withDatabaseName("fineract_fund");

    @DynamicPropertySource
    static void datasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // The migration is authoritative: Liquibase runs, Hibernate only validates.
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.xml");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private FundRepository fundRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void liquibaseCreatesMFundWithExpectedColumns() {
        final Object[] nameColumn = (Object[]) entityManager.createNativeQuery(
                "SELECT data_type, character_maximum_length, is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'm_fund' AND column_name = 'name'")
                .getSingleResult();
        assertThat(nameColumn[0]).isEqualTo("character varying");
        assertThat(((Number) nameColumn[1]).intValue()).isEqualTo(255);
        assertThat(nameColumn[2]).isEqualTo("NO");

        final Object[] externalIdColumn = (Object[]) entityManager.createNativeQuery(
                "SELECT data_type, character_maximum_length FROM information_schema.columns "
                        + "WHERE table_name = 'm_fund' AND column_name = 'external_id'")
                .getSingleResult();
        assertThat(externalIdColumn[0]).isEqualTo("character varying");
        assertThat(((Number) externalIdColumn[1]).intValue()).isEqualTo(100);
    }

    @Test
    void liquibaseCreatesTheTwoNamedUniqueConstraints() {
        @SuppressWarnings("unchecked")
        final List<String> constraints = entityManager.createNativeQuery(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_name = 'm_fund' AND constraint_type = 'UNIQUE'")
                .getResultList();
        assertThat(constraints).contains("fund_name_org", "fund_externalid_org");
    }

    @Test
    void saveAndFindRoundTripAgainstRealPostgres() {
        final Fund saved = fundRepository.saveAndFlush(new Fund("Growth Fund", "EXT-001"));
        assertThat(saved.getId()).isNotNull();

        final Optional<Fund> reloaded = fundRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getName()).isEqualTo("Growth Fund");
        assertThat(reloaded.get().getExternalId()).isEqualTo("EXT-001");
    }
}
