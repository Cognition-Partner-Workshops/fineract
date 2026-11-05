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
package org.apache.fineract.portfolio.savings.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SavingsAccountDataValidatorTest {

    private final SavingsAccountDataValidator validator = new SavingsAccountDataValidator(new FromJsonHelper());

    @Test
    void acceptsApplicationWithFinancialLimitsAndScheduledCharges() {
        assertDoesNotThrow(() -> validator.validateForSubmit(application().toString()));
        assertDoesNotThrow(() -> validator.validateForUpdate(application().toString()));
    }

    @Test
    void requiresEitherClientOrGroupAndAProductAndSubmissionDate() {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForSubmit("{}"));
        assertParameter(error, "clientId");
        assertParameter(error, "productId");
        assertParameter(error, "submittedOnDate");
        JsonObject json = application();
        json.remove("clientId");
        json.addProperty("groupId", 1);
        assertDoesNotThrow(() -> validator.validateForSubmit(json.toString()));
    }

    @ParameterizedTest
    @CsvSource({ "clientId,0", "groupId,-1", "productId,0", "fieldOfficerId,-1", "nominalAnnualInterestRate,-1",
            "interestCompoundingPeriodType,999", "interestPostingPeriodType,999", "interestCalculationType,999",
            "interestCalculationDaysInYearType,366", "minRequiredOpeningBalance,-1", "lockinPeriodFrequency,-1",
            "lockinPeriodFrequencyType,4", "overdraftLimit,-1", "nominalAnnualInterestRateOverdraft,-1",
            "minOverdraftForInterestCalculation,-1" })
    void rejectsInvalidIdentifiersAndFinancialSettings(String parameter, String value) {
        JsonObject json = application();
        json.addProperty(parameter, new BigDecimal(value));
        json.addProperty("lienAllowed", true);
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForSubmit(json.toString())),
                parameter);
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpdate(json.toString())),
                parameter);
    }

    @ParameterizedTest
    @CsvSource({ "amount,0", "amount,-1", "chargeId,-1", "feeInterval,0", "feeInterval,13" })
    void rejectsInvalidNestedChargeSettings(String parameter, int value) {
        JsonObject json = application();
        json.getAsJsonArray("charges").get(0).getAsJsonObject().addProperty(parameter, value);
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForSubmit(json.toString())),
                parameter);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " " })
    void rejectsBlankRequestsForAllAccountValidationEntryPoints(String json) {
        assertThrows(InvalidJsonException.class, () -> validator.validateForSubmit(json));
        assertThrows(InvalidJsonException.class, () -> validator.validateForUpdate(json));
        assertThrows(InvalidJsonException.class, () -> validator.validateForAssignSavingsOfficer(json));
        assertThrows(InvalidJsonException.class, () -> validator.validateForUnAssignSavingsOfficer(json));
    }

    @Test
    void officerAssignmentRequiresPositiveOfficerAndDate() {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForAssignSavingsOfficer("{\"toSavingsOfficerId\":0}"));
        assertParameter(error, "toSavingsOfficerId");
        assertParameter(error, "assignmentDate");
        assertDoesNotThrow(() -> validator.validateForAssignSavingsOfficer("""
                {"toSavingsOfficerId":1,"assignmentDate":"2024-02-29","dateFormat":"yyyy-MM-dd","locale":"en"}
                """));
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUnAssignSavingsOfficer("{}")),
                "unassignedDate");
        assertDoesNotThrow(() -> validator.validateForUnAssignSavingsOfficer("""
                {"unassignedDate":"2024-02-29","dateFormat":"yyyy-MM-dd","locale":"en"}
                """));
    }

    @Test
    void currentlyRejectsNegativeLienLimitOnSubmitButNotOnUpdate() {
        JsonObject json = application();
        json.addProperty("lienAllowed", true);
        json.addProperty("maxAllowedLienLimit", -1);
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForSubmit(json.toString())),
                "maxAllowedLienLimit");
        assertDoesNotThrow(() -> validator.validateForUpdate(json.toString()));
    }

    private JsonObject application() {
        return JsonParser.parseString("""
                {
                  "locale":"en","dateFormat":"yyyy-MM-dd","monthDayFormat":"MM-dd",
                  "clientId":1,"productId":1,"fieldOfficerId":1,"submittedOnDate":"2024-02-29",
                  "accountNo":"SAV-001","externalId":"savings-reference",
                  "nominalAnnualInterestRate":0,"interestCompoundingPeriodType":1,"interestPostingPeriodType":4,
                  "interestCalculationType":1,"interestCalculationDaysInYearType":365,
                  "minRequiredOpeningBalance":0,"lockinPeriodFrequency":1,"lockinPeriodFrequencyType":2,
                  "withdrawalFeeForTransfers":false,"withHoldTax":false,"allowOverdraft":false,
                  "overdraftLimit":0,"nominalAnnualInterestRateOverdraft":0,"minOverdraftForInterestCalculation":0,
                  "lienAllowed":false,"maxAllowedLienLimit":0,
                  "charges":[{"chargeId":1,"amount":10,"feeOnMonthDay":"02-29","feeInterval":1}]
                }
                """).getAsJsonObject();
    }

    private void assertParameter(PlatformApiDataValidationException error, String parameter) {
        assertTrue(error.getErrors().stream().anyMatch(e -> parameter.equals(e.getParameterName())),
                () -> "Expected error for " + parameter + ": " + error.getErrors());
    }
}
