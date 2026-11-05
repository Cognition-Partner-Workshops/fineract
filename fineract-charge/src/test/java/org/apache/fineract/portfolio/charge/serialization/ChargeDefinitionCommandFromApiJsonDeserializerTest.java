/*
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
package org.apache.fineract.portfolio.charge.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.charge.exception.ChargeDueAtDisbursementCannotBePenaltyException;
import org.apache.fineract.portfolio.charge.exception.ChargeMustBePenaltyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ChargeDefinitionCommandFromApiJsonDeserializerTest {

    private ChargeDefinitionCommandFromApiJsonDeserializer deserializer;

    @BeforeEach
    void setUp() {
        deserializer = new ChargeDefinitionCommandFromApiJsonDeserializer(new FromJsonHelper());
    }

    @Test
    void validateForCreateAcceptsValidLoanCharge() {
        assertDoesNotThrow(() -> deserializer.validateForCreate(createJson(1, 1, 1)));
    }

    @Test
    void validateForCreateRejectsUnsupportedParameter() {
        UnsupportedParameterException exception = assertThrows(UnsupportedParameterException.class,
                () -> deserializer.validateForCreate(createJson(1, 1, 1).replace("}", ",\"unknown\":true}")));

        assertEquals(List.of("unknown"), exception.getUnsupportedParameters());
    }

    @Test
    void validateForCreateReportsMissingRequiredFields() {
        PlatformApiDataValidationException exception = assertValidation("{}");

        assertError(exception, "chargeAppliesTo", "validation.msg.charge.chargeAppliesTo.cannot.be.blank");
        assertError(exception, "chargeCalculationType", "validation.msg.charge.chargeCalculationType.cannot.be.blank");
        assertError(exception, "name", "validation.msg.charge.name.cannot.be.blank");
        assertError(exception, "currencyCode", "validation.msg.charge.currencyCode.cannot.be.blank");
        assertError(exception, "amount", "validation.msg.charge.amount.cannot.be.blank");
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    void validateForCreateRejectsNonPositiveAmount(String amount) {
        PlatformApiDataValidationException exception = assertValidation(createJson(1, 1, 1).replace("10", amount));

        assertError(exception, "amount", "validation.msg.charge.amount.not.greater.than.zero");
    }

    private static Stream<Arguments> invalidAmounts() {
        return Stream.of(Arguments.of("0"), Arguments.of("-1"));
    }

    @Test
    void validateForCreateRejectsLoanEnumerationsAndCombination() {
        String json = createJson(1, 3, 99).replace("\"chargePaymentMode\":0", "\"chargePaymentMode\":2");
        PlatformApiDataValidationException exception = assertValidation(json);

        assertError(exception, "chargeTimeType", "validation.msg.charge.chargeTimeType.is.not.one.of.expected.enumerations");
        assertError(exception, "chargeCalculationType", "validation.msg.charge.chargeCalculationType.is.not.one.of.expected.enumerations");
        assertError(exception, "chargePaymentMode", "validation.msg.charge.chargePaymentMode.is.not.one.of.expected.enumerations");
    }

    @Test
    void validateForCreateRejectsSavingsAndClientSpecificValues() {
        PlatformApiDataValidationException savings = assertValidation(createJson(2, 1, 3));
        assertError(savings, "chargeTimeType", "validation.msg.charge.chargeTimeType.is.not.one.of.expected.enumerations");
        assertError(savings, "chargeCalculationType", "validation.msg.charge.chargeCalculationType.is.not.one.of.expected.enumerations");

        PlatformApiDataValidationException client = assertValidation(createJson(3, 5, 2));
        assertError(client, "chargeTimeType", "validation.msg.charge.chargeTimeType.is.not.one.of.expected.enumerations");
        assertError(client, "chargeCalculationType", "validation.msg.charge.chargeCalculationType.is.not.one.of.expected.enumerations");
    }

    @Test
    void validateForCreateRejectsShareSpecificValues() {
        PlatformApiDataValidationException exception = assertValidation(createJson(4, 1, 3));

        assertError(exception, "chargeTimeType", "validation.msg.charge.chargeTimeType.is.not.one.of.expected.enumerations");
        assertError(exception, "chargeCalculationType", "validation.msg.charge.chargeCalculationType.is.not.one.of.expected.enumerations");
    }

    @Test
    void validateForCreateValidatesMonthlyFeeDateAndInterval() {
        PlatformApiDataValidationException exception = assertValidation(createJson(2, 7, 1).replace("}", ",\"feeInterval\":0}"));

        assertError(exception, "feeOnMonthDay", "validation.msg.charge.feeOnMonthDay.cannot.be.blank");
        assertError(exception, "feeInterval", "validation.msg.charge.feeInterval.not.greater.than.zero");
    }

    @Test
    void validateForCreateRejectsWeeklyFeeMonthDay() {
        String json = createJson(2, 11, 1).replace("}", ",\"feeOnMonthDay\":\"01-31\"}");
        PlatformApiDataValidationException exception = assertValidation(json);

        assertError(exception, "feeOnMonthDay", "validation.msg.charge.feeOnMonthDay.cannot.also.be.provided.when.chargeTimeType.is.11");
    }

    @Test
    void validateForCreateValidatesCapsAndFreeWithdrawalSettings() {
        String json = createJson(2, 5, 1).replace("}", ",\"minCap\":0,\"maxCap\":-1,\"enableFreeWithdrawalCharge\":true,"
                + "\"freeWithdrawalFrequency\":0,\"restartCountFrequency\":-1}");
        PlatformApiDataValidationException exception = assertValidation(json);

        assertError(exception, "minCap", "validation.msg.charge.minCap.not.greater.than.zero");
        assertError(exception, "maxCap", "validation.msg.charge.maxCap.not.greater.than.zero");
        assertError(exception, "freeWithdrawalFrequency", "validation.msg.charge.freeWithdrawalFrequency.not.greater.than.zero");
        assertError(exception, "restartCountFrequency", "validation.msg.charge.restartCountFrequency.not.greater.than.zero");
    }

    @Test
    void validateForCreateAcceptsFreeWithdrawalAndPaymentTypeSettings() {
        String json = createJson(2, 5, 2).replace("}", ",\"enableFreeWithdrawalCharge\":true,\"freeWithdrawalFrequency\":2,"
                + "\"restartCountFrequency\":3,\"countFrequencyType\":2,\"enablePaymentType\":true,\"paymentTypeId\":7}");

        assertDoesNotThrow(() -> deserializer.validateForCreate(json));
    }

    @Test
    void validateForUpdateValidatesProvidedFieldsOnly() {
        assertDoesNotThrow(() -> deserializer.validateForUpdate("{}"));

        PlatformApiDataValidationException exception = assertValidationUpdate("{\"amount\":0,\"feeFrequency\":4,\"chargePaymentMode\":2}");
        assertError(exception, "amount", "validation.msg.charge.amount.not.greater.than.zero");
        assertError(exception, "feeFrequency", "validation.msg.charge.feeFrequency.is.not.within.expected.range");
        assertError(exception, "chargePaymentMode", "validation.msg.charge.chargePaymentMode.is.not.within.expected.range");
    }

    @ParameterizedTest
    @MethodSource("chargeTimeAndCalculationValues")
    void validateChargeTimeAndCalculationTypeReportsExpectedCombinations(Integer timeType, Integer calculationType, String parameter,
            String code) {
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> deserializer.validateChargeTimeNCalculationType(timeType, calculationType));

        assertError(exception, parameter, code);
    }

    private static Stream<Arguments> chargeTimeAndCalculationValues() {
        return Stream.of(
                Arguments.of(ChargeTimeType.TRANCHE_DISBURSEMENT.getValue(), ChargeCalculationType.PERCENT_OF_AMOUNT.getValue(),
                        "chargeCalculationType", "validation.msg.charge.chargeCalculationType.is.not.one.of.expected.enumerations"),
                Arguments.of(ChargeTimeType.DISBURSEMENT.getValue(), ChargeCalculationType.PERCENT_OF_DISBURSEMENT_AMOUNT.getValue(),
                        "chargeCalculationType", "validation.msg.charge.chargeCalculationType.is.one.of.unwanted.enumerations"),
                Arguments.of(ChargeTimeType.SHAREACCOUNT_ACTIVATION.getValue(), ChargeCalculationType.PERCENT_OF_AMOUNT.getValue(),
                        "chargeCalculationType", "validation.msg.charge.chargeCalculationType.is.not.one.of.expected.enumerations"));
    }

    @Test
    void validateChargeTimeAndCalculationTypeAcceptsTrancheDisbursementPercentage() {
        assertDoesNotThrow(() -> deserializer.validateChargeTimeNCalculationType(ChargeTimeType.TRANCHE_DISBURSEMENT.getValue(),
                ChargeCalculationType.PERCENT_OF_DISBURSEMENT_AMOUNT.getValue()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\n" })
    void createAndUpdateRejectBlankJson(String json) {
        assertThrows(InvalidJsonException.class, () -> deserializer.validateForCreate(json));
        assertThrows(InvalidJsonException.class, () -> deserializer.validateForUpdate(json));
    }

    @Test
    void updateRejectsUnsupportedParameters() {
        UnsupportedParameterException exception = assertThrows(UnsupportedParameterException.class,
                () -> deserializer.validateForUpdate("{\"unknown\":1}"));
        assertEquals(List.of("unknown"), exception.getUnsupportedParameters());
    }

    @ParameterizedTest
    @CsvSource({ "1,1,1", "1,12,5", "2,5,2", "2,16,2", "3,2,1", "4,13,1", "4,14,2", "4,15,2" })
    void createAcceptsApplicableTimeAndCalculationTypes(int appliesTo, int timeType, int calculationType) {
        assertDoesNotThrow(() -> deserializer.validateForCreate(createJson(appliesTo, timeType, calculationType)));
    }

    @Test
    void createRejectsInvalidApplicabilityAndShareActivationPercentage() {
        assertError(assertValidation(createJson(99, 1, 1)), "chargeAppliesTo",
                "validation.msg.charge.chargeAppliesTo.is.not.one.of.expected.enumerations");
        assertError(assertValidation(createJson(4, 13, 2)), "chargeCalculationType",
                "validation.msg.charge.chargeCalculationType.is.not.one.of.expected.enumerations");
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateFields")
    void updateReportsInvalidProvidedField(String fields, String parameter, String suffix) {
        assertError(assertValidationUpdate("{\"locale\":\"en\"," + fields + "}"), parameter,
                "validation.msg.charge." + parameter + "." + suffix);
    }

    private static Stream<Arguments> invalidUpdateFields() {
        return Stream.of(Arguments.of("\"name\":\"\"", "name", "cannot.be.blank"),
                Arguments.of("\"name\":\"" + "a".repeat(101) + "\"", "name", "exceeds.max.length"),
                Arguments.of("\"currencyCode\":\"USDD\"", "currencyCode", "exceeds.max.length"),
                Arguments.of("\"amount\":-1", "amount", "not.greater.than.zero"),
                Arguments.of("\"minCap\":0", "minCap", "not.greater.than.zero"),
                Arguments.of("\"maxCap\":-1", "maxCap", "not.greater.than.zero"),
                Arguments.of("\"chargeAppliesTo\":99", "chargeAppliesTo", "is.not.one.of.expected.enumerations"),
                Arguments.of("\"chargeTimeType\":99", "chargeTimeType", "is.not.one.of.expected.enumerations"),
                Arguments.of("\"chargeCalculationType\":6", "chargeCalculationType", "is.not.within.expected.range"),
                Arguments.of("\"feeInterval\":0", "feeInterval", "not.greater.than.zero"),
                Arguments.of("\"feeFrequency\":-1", "feeFrequency", "is.not.within.expected.range"),
                Arguments.of("\"penalty\":null", "penalty", "cannot.be.blank"),
                Arguments.of("\"active\":null", "active", "cannot.be.blank"),
                Arguments.of("\"feeOnMonthDay\":null", "feeOnMonthDay", "cannot.be.blank"),
                Arguments.of("\"taxGroupId\":-1", "taxGroupId", "not.greater.than.zero"),
                Arguments.of("\"enableFreeWithdrawalCharge\":true,\"freeWithdrawalFrequency\":0", "freeWithdrawalFrequency",
                        "not.greater.than.zero"),
                Arguments.of("\"enableFreeWithdrawalCharge\":true,\"restartCountFrequency\":0", "restartCountFrequency",
                        "not.greater.than.zero"));
    }

    @Test
    void createRequiresFeeIntervalWhenFrequencyIsProvided() {
        assertError(assertValidation(createJson(2, 5, 1).replace("}", ",\"feeFrequency\":1}")), "feeInterval",
                "validation.msg.charge.feeInterval.cannot.be.blank");
        assertError(assertValidation(createJson(2, 5, 1).replace("}", ",\"feeFrequency\":4,\"feeInterval\":1}")), "feeFrequency",
                "validation.msg.charge.feeFrequency.is.not.within.expected.range");
    }

    @ParameterizedTest
    @ValueSource(ints = { 6, 7 })
    void createValidatesAnnualAndMonthlyFeeDates(int timeType) {
        String json = createJson(2, timeType, 1).replace("}",
                ",\"locale\":\"en\",\"monthDayFormat\":\"MM-dd\"," + "\"feeOnMonthDay\":\"02-29\",\"feeInterval\":12}");
        assertDoesNotThrow(() -> deserializer.validateForCreate(json));
        PlatformApiDataValidationException exception = assertValidation(json.replace("02-29", "02-30"));
        assertError(exception, "feeOnMonthDay", "validation.msg.invalid.month.day");
    }

    @Test
    void monthlyFeeRejectsIntervalAboveTwelveAndAnnualFeeRequiresDate() {
        String json = createJson(2, 7, 1).replace("}",
                ",\"locale\":\"en\",\"monthDayFormat\":\"MM-dd\"," + "\"feeOnMonthDay\":\"01-31\",\"feeInterval\":13}");
        assertError(assertValidation(json), "feeInterval", "validation.msg.charge.feeInterval.is.not.within.expected.range");
        assertError(assertValidation(createJson(2, 6, 1)), "feeOnMonthDay", "validation.msg.charge.feeOnMonthDay.cannot.be.blank");
    }

    @Test
    void createAndUpdateAcceptPositiveCapsAndValidOptionalSettings() {
        String fields = "\"locale\":\"en\",\"minCap\":1,\"maxCap\":3,\"feeFrequency\":0,\"feeInterval\":1,"
                + "\"enableFreeWithdrawalCharge\":true,\"freeWithdrawalFrequency\":2,\"restartCountFrequency\":3,"
                + "\"countFrequencyType\":2,\"enablePaymentType\":true,\"paymentTypeId\":4";
        assertDoesNotThrow(() -> deserializer.validateForCreate(createJson(2, 5, 2).replace("}", "," + fields + "}")));
        assertDoesNotThrow(() -> deserializer.validateForUpdate("{" + fields + "}"));
    }

    @Test
    void updateCurrentlyValidatesPaymentTypeOnlyWhenFreeWithdrawalFlagIsPresent() {
        assertDoesNotThrow(() -> deserializer.validateForUpdate("{\"enablePaymentType\":true,\"paymentTypeId\":0}"));
        assertError(assertValidationUpdate("{\"enableFreeWithdrawalCharge\":false,\"enablePaymentType\":true,\"paymentTypeId\":0}"),
                "paymentTypeId", "validation.msg.charge.paymentTypeId.not.greater.than.zero");
    }

    @ParameterizedTest
    @CsvSource({ "1,true", "12,true", "9,false" })
    void penaltyRestrictionsAreEnforcedByDomainAfterJsonValidation(int timeType, boolean penalty) {
        String json = createJson(1, timeType, 1).replace("\"penalty\":false", "\"penalty\":" + penalty).replace("}", ",\"locale\":\"en\"}");
        assertDoesNotThrow(() -> deserializer.validateForCreate(json));
        FromJsonHelper helper = new FromJsonHelper();
        JsonCommand command = new JsonCommand(null, helper.parse(json), helper);
        Class<? extends RuntimeException> expected = penalty ? ChargeDueAtDisbursementCannotBePenaltyException.class
                : ChargeMustBePenaltyException.class;
        assertThrows(expected, () -> Charge.fromJson(command, null, null, null));
    }

    private PlatformApiDataValidationException assertValidation(String json) {
        return assertThrows(PlatformApiDataValidationException.class, () -> deserializer.validateForCreate(json));
    }

    private PlatformApiDataValidationException assertValidationUpdate(String json) {
        return assertThrows(PlatformApiDataValidationException.class, () -> deserializer.validateForUpdate(json));
    }

    private static void assertError(PlatformApiDataValidationException exception, String parameter, String code) {
        assertTrue(exception.getErrors().stream().map(ApiParameterError::getParameterName).anyMatch(parameter::equals),
                () -> "Missing validation parameter " + parameter + ": " + exception.getErrors());
        assertTrue(
                exception.getErrors().stream().anyMatch(
                        error -> parameter.equals(error.getParameterName()) && code.equals(error.getUserMessageGlobalisationCode())),
                () -> "Missing validation error " + code + ": " + exception.getErrors());
    }

    private static String createJson(int appliesTo, int timeType, int calculationType) {
        return "{\"name\":\"Charge\",\"currencyCode\":\"USD\",\"amount\":10," + "\"chargeAppliesTo\":" + appliesTo + ",\"chargeTimeType\":"
                + timeType + "," + "\"chargeCalculationType\":" + calculationType + ",\"chargePaymentMode\":0,"
                + "\"penalty\":false,\"active\":true}";
    }
}
