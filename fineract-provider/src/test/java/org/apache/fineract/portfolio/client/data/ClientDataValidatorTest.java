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
package org.apache.fineract.portfolio.client.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClientDataValidatorTest {

    private final FromJsonHelper helper = new FromJsonHelper();
    private final ClientDataValidator validator = new ClientDataValidator(helper,
            mock(ConfigurationReadPlatformService.class, RETURNS_DEEP_STUBS));

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2025, 1, 20))));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void createAcceptsInactiveIndividual() {
        assertDoesNotThrow(() -> validator
                .validateForCreate("{\"officeId\":1,\"firstname\":\"Jane\",\"lastname\":\"Doe\",\"active\":false,\"legalFormId\":1}"));
    }

    @Test
    void createReportsRequiredFieldsWithCodes() {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate("{}"));
        assertThat(error.getErrors()).anySatisfy(parameter -> {
            assertThat(parameter.getParameterName()).isEqualTo("officeId");
            assertThat(parameter.getUserMessageGlobalisationCode()).endsWith(".officeId.cannot.be.blank");
        });
        assertThat(error.getErrors()).extracting("parameterName").contains("id", "legalFormId");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " " })
    void createRejectsBlankJson(String json) {
        assertThrows(InvalidJsonException.class, () -> validator.validateForCreate(json));
    }

    @Test
    void updateAcceptsSingleNameAndRejectsEmptyUpdate() {
        assertDoesNotThrow(() -> validator.validateForUpdate("{\"accountNo\":\"ABC-123\"}"));
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpdate("{}"));
    }

    @Test
    void updateRejectsInvalidLegalForm() {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate("{\"legalFormId\":3}"));
        assertThat(error.getErrors()).extracting("parameterName").contains("legalFormId");
    }

    @Test
    void updateRejectsFutureBirthDate() {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class, () -> validator
                .validateForUpdate("{\"dateOfBirth\":\"21 January 2025\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}"));
        assertThat(error.getErrors()).extracting("parameterName").contains("dateOfBirth");
    }

    @ParameterizedTest
    @CsvSource({ "activate,activationDate", "close,closureDate", "reject,rejectionDate", "withdraw,withdrawalDate",
            "reactivate,reactivationDate", "undoReject,reopenedDate", "undoWithdraw,reopenedDate" })
    void lifecycleRequiresCommandDate(String action, String dateParameter) {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class, () -> validate(action, "{}"));
        assertThat(error.getErrors()).anySatisfy(parameter -> {
            assertThat(parameter.getParameterName()).isEqualTo(dateParameter);
            assertThat(parameter.getUserMessageGlobalisationCode()).endsWith("." + dateParameter + ".cannot.be.blank");
        });
    }

    @ParameterizedTest
    @CsvSource({ "close,closureDate,closureReasonId", "reject,rejectionDate,rejectionReasonId",
            "withdraw,withdrawalDate,withdrawalReasonId" })
    void lifecycleRejectsNonPositiveReason(String action, String dateParameter, String reasonParameter) {
        String json = "{\"" + dateParameter + "\":\"20 January 2025\",\"" + reasonParameter
                + "\":0,\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}";
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class, () -> validate(action, json));
        assertThat(error.getErrors()).extracting("parameterName").contains(reasonParameter);
    }

    @ParameterizedTest
    @ValueSource(strings = { "undoReject", "undoWithdraw" })
    void undoRejectsFutureDateAndAcceptsBusinessDate(String action) {
        String json = "{\"reopenedDate\":\"21 January 2025\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}";
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class, () -> validate(action, json));
        assertThat(error.getErrors()).extracting("parameterName").contains("reopenedDate");
        assertDoesNotThrow(() -> validate(action, json.replace("21 January", "20 January")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "activate", "close", "reject", "withdraw", "reactivate", "undoReject", "undoWithdraw" })
    void lifecycleRejectsUnsupportedParameters(String action) {
        UnsupportedParameterException error = assertThrows(UnsupportedParameterException.class,
                () -> validate(action, "{\"unexpected\":true}"));
        assertThat(error.getUnsupportedParameters()).containsExactly("unexpected");
    }

    private void validate(String action, String json) {
        JsonCommand command = new JsonCommand(1L, helper.parse(json), helper);
        switch (action) {
            case "activate" -> validator.validateActivation(command);
            case "close" -> validator.validateClose(command);
            case "reject" -> validator.validateRejection(command);
            case "withdraw" -> validator.validateWithdrawn(command);
            case "reactivate" -> validator.validateReactivate(command);
            case "undoReject" -> validator.validateUndoRejection(command);
            case "undoWithdraw" -> validator.validateUndoWithDrawn(command);
            default -> throw new IllegalArgumentException(action);
        }
    }
}
