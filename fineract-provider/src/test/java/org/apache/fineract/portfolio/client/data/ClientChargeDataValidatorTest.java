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

import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientChargeDataValidatorTest {

    private final ClientChargeDataValidator validator = new ClientChargeDataValidator(new FromJsonHelper());

    @Test
    void validateAddAcceptsAmountAndDueDate() {
        assertDoesNotThrow(() -> validator
                .validateAdd("{\"amount\":\"10.25\",\"dueDate\":\"15 January 2025\",\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\"}"));
    }

    @Test
    void validateAddReportsRequiredAndPositiveFields() {
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateAdd("{\"amount\":0}"));

        assertThat(exception.getErrors()).extracting("parameterName").containsExactlyInAnyOrder("amount", "dueDate");
    }

    @Test
    void validatePayChargeRejectsUnsupportedParameter() {
        UnsupportedParameterException exception = assertThrows(UnsupportedParameterException.class,
                () -> validator.validatePayCharge("{\"amount\":10,\"transactionDate\":\"15 January 2025\",\"unexpected\":true}"));

        assertThat(exception.getUnsupportedParameters()).containsExactly("unexpected");
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1" })
    void updateRejectsNonPositiveAmount(String amount) {
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateUpdate("{\"amount\":" + amount + "}"));
        assertThat(exception.getErrors()).anySatisfy(error -> {
            assertThat(error.getParameterName()).isEqualTo("amount");
            assertThat(error.getUserMessageGlobalisationCode()).contains("amount");
        });
    }

    @Test
    void updateAllowsEmptyObjectButRejectsExplicitNullDueDate() {
        assertDoesNotThrow(() -> validator.validateUpdate("{}"));
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateUpdate("{\"dueDate\":null}"));
        assertThat(exception.getErrors()).extracting("parameterName").contains("dueDate");
    }

    @Test
    void paymentRequiresAmountAndTransactionDate() {
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validatePayCharge("{}"));
        assertThat(exception.getErrors()).extracting("parameterName").contains("amount", "transactionDate");
        assertDoesNotThrow(() -> validator.validatePayCharge(
                "{\"amount\":10,\"transactionDate\":\"20 January 2025\",\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " " })
    void allChargeCommandsRejectBlankJson(String json) {
        assertThrows(InvalidJsonException.class, () -> validator.validateAdd(json));
        assertThrows(InvalidJsonException.class, () -> validator.validateUpdate(json));
        assertThrows(InvalidJsonException.class, () -> validator.validatePayCharge(json));
    }
}
