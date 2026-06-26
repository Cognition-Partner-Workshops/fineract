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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

/**
 * H2-backed slice test proving the {@link Fund} entity maps to {@code m_fund}, that save/find round-trips the {@code name}
 * and {@code external_id} columns, and that the {@code fund_name_org} unique constraint on {@code name} is enforced.
 *
 * <p>The main {@code application.properties} excludes the JDBC/JPA auto-configuration (no DB until T2); this slice clears
 * that exclusion so {@code @DataJpaTest} can stand up an embedded H2 datasource and create the schema from the entity.
 */
@DataJpaTest
@TestPropertySource(properties = { "spring.autoconfigure.exclude=", "spring.jpa.hibernate.ddl-auto=create-drop" })
class FundRepositoryDataJpaTest {

    @Autowired
    private FundRepository fundRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void entityMapsToMFundTable() {
        // A native query against m_fund only succeeds if the entity is mapped to that physical table.
        final Object count = entityManager.createNativeQuery("SELECT count(*) FROM m_fund").getSingleResult();
        assertThat(((Number) count).longValue()).isZero();
    }

    @Test
    void savePersistsNameAndExternalIdAndFindReturnsThem() {
        final Fund saved = fundRepository.saveAndFlush(new Fund("Growth Fund", "EXT-001"));
        assertThat(saved.getId()).isNotNull();

        final Optional<Fund> reloaded = fundRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getName()).isEqualTo("Growth Fund");
        assertThat(reloaded.get().getExternalId()).isEqualTo("EXT-001");

        assertThat(fundRepository.findByName("Growth Fund")).isPresent();
    }

    @Test
    void duplicateNameViolatesUniqueConstraint() {
        fundRepository.saveAndFlush(new Fund("Duplicate", "EXT-A"));

        assertThatThrownBy(() -> fundRepository.saveAndFlush(new Fund("Duplicate", "EXT-B")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
