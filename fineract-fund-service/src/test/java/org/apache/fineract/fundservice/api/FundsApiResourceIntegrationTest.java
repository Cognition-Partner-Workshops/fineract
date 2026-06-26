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
package org.apache.fineract.fundservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.fineract.fundservice.domain.FundRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end web-layer test against a real PostgreSQL (Testcontainers) with the application's own Liquibase migration
 * and the full Spring Security filter chain active. Proves the validation gate: 401 without auth, CRUD round-trip with
 * valid Basic auth, ordering, and the 404/400/409 error mappings.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FundsApiResourceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16").withDatabaseName("fineract_fund");

    @DynamicPropertySource
    static void datasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.xml");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FundRepository fundRepository;

    @BeforeEach
    void clean() {
        this.fundRepository.deleteAll();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
        return httpBasic("mifos", "password");
    }

    @Test
    void getFundsWithoutAuthReturns401() throws Exception {
        this.mockMvc.perform(get("/v1/funds")).andExpect(status().isUnauthorized());
    }

    @Test
    void getFundsWithInvalidCredentialsReturns401() throws Exception {
        this.mockMvc.perform(get("/v1/funds").with(httpBasic("mifos", "wrong"))).andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPermittedWithoutAuth() throws Exception {
        this.mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void apiDocsArePermittedWithoutAuth() throws Exception {
        this.mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void createThenGetByIdRoundTrip() throws Exception {
        final long id = createFund("{\"name\":\"Growth Fund\",\"externalId\":\"EXT-001\"}");

        this.mockMvc.perform(get("/v1/funds/" + id).with(auth())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id)).andExpect(jsonPath("$.name").value("Growth Fund"))
                .andExpect(jsonPath("$.externalId").value("EXT-001"));
    }

    @Test
    void listIsOrderedByName() throws Exception {
        createFund("{\"name\":\"Zeta Fund\"}");
        createFund("{\"name\":\"Alpha Fund\"}");
        createFund("{\"name\":\"Mu Fund\"}");

        this.mockMvc.perform(get("/v1/funds").with(auth())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha Fund")).andExpect(jsonPath("$[1].name").value("Mu Fund"))
                .andExpect(jsonPath("$[2].name").value("Zeta Fund"));
    }

    @Test
    void putUpdatesFund() throws Exception {
        final long id = createFund("{\"name\":\"Old Name\",\"externalId\":\"EXT-PUT\"}");

        this.mockMvc.perform(put("/v1/funds/" + id).with(auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New Name"));

        this.mockMvc.perform(get("/v1/funds/" + id).with(auth())).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name")).andExpect(jsonPath("$.externalId").value("EXT-PUT"));
    }

    @Test
    void putEmptyExternalIdWhenAlreadyNullReportsNoChange() throws Exception {
        final long id = createFund("{\"name\":\"No Ext Fund\"}");

        this.mockMvc
                .perform(put("/v1/funds/" + id).with(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.externalId").doesNotExist());
    }

    @Test
    void getMissingIdReturns404() throws Exception {
        this.mockMvc.perform(get("/v1/funds/999999").with(auth())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatusCode").value("404"));
    }

    @Test
    void postBlankNameReturns400() throws Exception {
        this.mockMvc.perform(post("/v1/funds").with(auth()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].parameterName").value("name"));
    }

    @Test
    void postNameTooLongReturns400() throws Exception {
        final String longName = "x".repeat(101);
        this.mockMvc
                .perform(post("/v1/funds").with(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].parameterName").value("name"));
    }

    @Test
    void postUnknownFieldReturns400() throws Exception {
        this.mockMvc
                .perform(post("/v1/funds").with(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Valid\",\"bogus\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].parameterName").value("bogus"));
    }

    @Test
    void postDuplicateNameReturns409() throws Exception {
        createFund("{\"name\":\"Unique Fund\"}");

        this.mockMvc.perform(post("/v1/funds").with(auth()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Unique Fund\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("error.msg.fund.duplicate.name"));
    }

    @Test
    void postDuplicateNameContainingExternalIdTokenReturns409WithNameCode() throws Exception {
        createFund("{\"name\":\"external_id fund\"}");

        this.mockMvc
                .perform(post("/v1/funds").with(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"external_id fund\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("error.msg.fund.duplicate.name"));
    }

    @Test
    void postDuplicateExternalIdReturns409() throws Exception {
        createFund("{\"name\":\"Fund A\",\"externalId\":\"DUP-EXT\"}");

        this.mockMvc
                .perform(post("/v1/funds").with(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fund B\",\"externalId\":\"DUP-EXT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("error.msg.fund.duplicate.externalId"));
    }

    private long createFund(final String json) throws Exception {
        final MvcResult result = this.mockMvc
                .perform(post("/v1/funds").with(auth()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resourceId").exists()).andReturn();
        final JsonNode body = this.objectMapper.readTree(result.getResponse().getContentAsString());
        final long id = body.get("resourceId").asLong();
        assertThat(id).isPositive();
        return id;
    }
}
