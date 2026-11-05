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
package org.apache.fineract.portfolio.client.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientFamilyMemberCommandFromApiJsonDeserializerTest {

    private final ClientFamilyMemberCommandFromApiJsonDeserializer validator = new ClientFamilyMemberCommandFromApiJsonDeserializer(
            new FromJsonHelper());

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2025, 1, 20))));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " " })
    void rejectsBlankJson(String json) {
        assertThrows(InvalidJsonException.class, () -> validator.validateForCreate(1L, json));
        assertThrows(InvalidJsonException.class, () -> validator.validateForUpdate(1L, json));
    }

    @Test
    void rejectsUnsupportedParameters() {
        UnsupportedParameterException error = assertThrows(UnsupportedParameterException.class,
                () -> validator.validateForCreate(1L, "{\"unexpected\":true}"));
        assertThat(error.getUnsupportedParameters()).containsExactly("unexpected");
    }

    @Test
    void acceptsSupportedCreateAndUpdateFields() {
        String json = "{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"age\":25,\"genderId\":1,\"maritalStatusId\":1,"
                + "\"professionId\":1,\"qualification\":\"Graduate\",\"dateOfBirth\":\"15 January 2000\","
                + "\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}";
        assertDoesNotThrow(() -> validator.validateForCreate(1L, json));
        assertDoesNotThrow(() -> validator.validateForUpdate(1L, json));
    }

    @Test
    void currentImplementationDoesNotThrowAccumulatedFieldErrors() {
        String json = "{\"firstName\":\"\",\"age\":-1,\"genderId\":0,\"dateOfBirth\":\"21 January 2025\","
                + "\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}";
        assertDoesNotThrow(() -> validator.validateForCreate(0L, json));
        assertDoesNotThrow(() -> validator.validateForUpdate(0L, json));
        assertDoesNotThrow(() -> validator.validateForDelete(0L));
    }

    @Test
    void relationshipIdSpellingAcceptedByWhitelistDiffersFromValidation() {
        assertDoesNotThrow(() -> validator.validateForCreate(1L, "{\"firstName\":\"Jane\",\"relationshipId\":1}"));
        UnsupportedParameterException error = assertThrows(UnsupportedParameterException.class,
                () -> validator.validateForCreate(1L, "{\"firstName\":\"Jane\",\"relationShipId\":1}"));
        assertThat(error.getUnsupportedParameters()).containsExactly("relationShipId");
    }
}
