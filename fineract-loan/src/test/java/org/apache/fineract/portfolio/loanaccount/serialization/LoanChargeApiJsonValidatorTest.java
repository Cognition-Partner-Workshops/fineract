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
package org.apache.fineract.portfolio.loanaccount.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LoanChargeApiJsonValidatorTest {

    private final LoanChargeApiJsonValidator validator = new LoanChargeApiJsonValidator(new FromJsonHelper(),
            mock(ChargeRepositoryWrapper.class), mock(LoanChargeRepository.class));

    private Consumer<String> action(String name) {
        return switch (name) {
            case "add" -> validator::validateAddLoanCharge;
            case "update" -> validator::validateUpdateOfLoanCharge;
            case "payment" -> json -> validator.validateChargePaymentTransaction(json, true);
            case "refund" -> validator::validateLoanChargeRefundTransaction;
            case "adjust" -> json -> validator.validateLoanChargeAdjustmentRequest(1L, 2L, json);
            default -> validator::validateInstallmentChargeTransaction;
        };
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(Arguments.of("add", "{}", "chargeId", "cannot.be.blank"),
                Arguments.of("add", "{\"chargeId\":0,\"amount\":1,\"locale\":\"en\"}", "chargeId", "not.greater.than.zero"),
                Arguments.of("add", "{\"chargeId\":1,\"amount\":-1,\"locale\":\"en\"}", "amount", "not.greater.than.zero"),
                Arguments.of("update", "{}", "amount", "cannot.be.blank"),
                Arguments.of("update", "{\"amount\":0,\"locale\":\"en\"}", "amount", "not.greater.than.zero"),
                Arguments.of("payment", "{}", "transactionDate", "cannot.be.blank"), Arguments.of("payment",
                        "{\"chargeId\":1,\"installmentNumber\":0,\"locale\":\"en\"}", "installmentNumber", "not.greater.than.zero"),
                Arguments.of("refund", "{}", "loanChargeId", "cannot.be.blank"),
                Arguments.of("refund", "{\"loanChargeId\":1,\"transactionAmount\":-1,\"locale\":\"en\"}", "transactionAmount",
                        "not.greater.than.zero"),
                Arguments.of("adjust", "{\"amount\":0,\"locale\":\"en\"}", "amount", "not.greater.than.zero"),
                Arguments.of("installment", "{\"installmentNumber\":-1,\"locale\":\"en\"}", "installmentNumber", "not.greater.than.zero"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidInputsHaveParameterSpecificErrors(String action, String json, String parameter, String code) {
        var exception = assertThrows(PlatformApiDataValidationException.class, () -> action(action).accept(json));
        assertTrue(
                exception.getErrors().stream().anyMatch(
                        error -> parameter.equals(error.getParameterName()) && error.getUserMessageGlobalisationCode().endsWith(code)),
                () -> exception.getErrors().toString());
    }

    static Stream<Arguments> validRequests() {
        return Stream.of(Arguments.of("add", "{\"chargeId\":1,\"amount\":12.50,\"locale\":\"en\"}"),
                Arguments.of("update",
                        "{\"amount\":12.50,\"dueDate\":\"29 February 2024\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}"),
                Arguments.of("payment",
                        "{\"chargeId\":1,\"transactionDate\":\"29 February 2024\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}"),
                Arguments.of("refund", "{\"loanChargeId\":1,\"transactionAmount\":12.50,\"locale\":\"en\"}"),
                Arguments.of("adjust", "{\"amount\":12.50,\"locale\":\"en\"}"), Arguments.of("installment", "{}"));
    }

    @ParameterizedTest
    @MethodSource("validRequests")
    void validInputsPassAndBlankRequestsAreHandled(String action, String json) {
        assertDoesNotThrow(() -> action(action).accept(json));
        if (!action.equals("installment")) {
            assertThrows(InvalidJsonException.class, () -> action(action).accept(" "));
        } else {
            assertDoesNotThrow(() -> action(action).accept(" "));
        }
    }

    @Test
    void unsupportedParametersAndInvalidAdjustmentIdsAreRejected() {
        assertThrows(UnsupportedParameterException.class, () -> validator.validateAddLoanCharge("{\"unknown\":1}"));
        var exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateLoanChargeAdjustmentRequest(null, -1L, "{}"));
        assertTrue(exception.getErrors().stream().anyMatch(error -> "loanId".equals(error.getParameterName())));
        assertTrue(exception.getErrors().stream().anyMatch(error -> "loanChargeId".equals(error.getParameterName())));
    }
}
