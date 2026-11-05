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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SavingsProductDataValidatorTest {

    private final FromJsonHelper jsonHelper = new FromJsonHelper();
    private final SavingsProductDataValidator validator = new SavingsProductDataValidator(jsonHelper,
            new SavingsProductAccountingDataValidator(jsonHelper));

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\n" })
    void rejectsEmptyCreateAndUpdateRequests(String json) {
        assertThrows(InvalidJsonException.class, () -> validator.validateForCreate(json));
        assertThrows(InvalidJsonException.class, () -> validator.validateForUpdate(json, product()));
    }

    @Test
    void acceptsZeroInterestAndMonetaryLimitsWithValidCalendarAndTaxSettings() {
        String json = validProduct().toString();
        assertDoesNotThrow(() -> validator.validateForCreate(json));
        assertDoesNotThrow(() -> validator.validateForUpdate(json, product()));
    }

    @ParameterizedTest
    @CsvSource({ "digitsAfterDecimal,-1", "digitsAfterDecimal,7", "inMultiplesOf,-1", "nominalAnnualInterestRate,-0.01",
            "interestCompoundingPeriodType,999", "interestCalculationType,999", "interestCalculationDaysInYearType,366",
            "minRequiredOpeningBalance,-1", "lockinPeriodFrequency,-1", "lockinPeriodFrequencyType,4", "feeAmount,-1", "overdraftLimit,-1",
            "nominalAnnualInterestRateOverdraft,-1", "minOverdraftForInterestCalculation,-1", "minBalanceForInterestCalculation,-1",
            "maxAllowedLienLimit,-1", "taxGroupId,0", "accountingRule,0" })
    void rejectsInvalidFinancialConfigurationOnBothCreateAndUpdate(String parameter, String value) {
        JsonObject json = validProduct();
        json.addProperty(parameter, new java.math.BigDecimal(value));
        PlatformApiDataValidationException createError = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(json.toString()));
        assertParameter(createError, parameter);
        PlatformApiDataValidationException updateError = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate(json.toString(), product()));
        assertParameter(updateError, parameter);
    }

    @ParameterizedTest
    @CsvSource({ "daysToInactive,0", "daysToDormancy,30", "daysToEscheat,60" })
    void enforcesPositiveAndStrictlyOrderedDormancyThresholds(String parameter, long value) {
        JsonObject json = validProduct();
        json.addProperty(parameter, value);
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(json.toString())),
                parameter);
        assertParameter(
                assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpdate(json.toString(), product())),
                parameter);
    }

    @Test
    void overdraftCannotExceedLienLimit() {
        JsonObject json = validProduct();
        json.addProperty("allowOverdraft", true);
        json.addProperty("lienAllowed", true);
        json.addProperty("overdraftLimit", 101);
        json.addProperty("maxAllowedLienLimit", 100);
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(json.toString()));
        assertTrue(error.getErrors().stream()
                .anyMatch(e -> e.getUserMessageGlobalisationCode().endsWith("Overdraft.limit.can.not.be.greater.than.lien.limit")));
        json.addProperty("overdraftLimit", 100);
        assertDoesNotThrow(() -> validator.validateForCreate(json.toString()));
    }

    @Test
    void taxWithholdingRequiresTaxGroupAtCreation() {
        JsonObject json = validProduct();
        json.remove("taxGroupId");
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(json.toString())),
                "taxGroupId");
    }

    @Test
    void lockinFrequencyAndTypeMustBeSuppliedTogetherAtCreation() {
        JsonObject json = validProduct();
        json.remove("lockinPeriodFrequencyType");
        String missingType = json.toString();
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(missingType)),
                "lockinPeriodFrequencyType");
        json = validProduct();
        json.remove("lockinPeriodFrequency");
        String request = json.toString();
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(request)),
                "lockinPeriodFrequency");
    }

    @Test
    void annualFeeRequiresCalendarDayAndCalendarDayRequiresAmount() {
        JsonObject json = validProduct();
        json.remove("feeOnMonthDay");
        String missingDay = json.toString();
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(missingDay)),
                "feeOnMonthDay");
        json = validProduct();
        json.remove("feeAmount");
        String missingAmount = json.toString();
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(missingAmount)),
                "feeAmount");
    }

    @Test
    void partialUpdateUsesExistingDormancyThresholds() {
        SavingsProduct product = product();
        when(product.isDormancyTrackingActive()).thenReturn(true);
        when(product.getDaysToInactive()).thenReturn(30L);
        when(product.getDaysToDormancy()).thenReturn(60L);
        when(product.getDaysToEscheat()).thenReturn(90L);
        assertDoesNotThrow(() -> validator.validateForUpdate("{\"locale\":\"en\",\"daysToDormancy\":45}", product));
        assertParameter(assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate("{\"locale\":\"en\",\"daysToDormancy\":95}", product)), "daysToEscheat");
    }

    @Test
    void missingMandatoryFieldsAreReportedTogether() {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate("{}"));
        assertParameter(error, "name");
        assertParameter(error, "currencyCode");
        assertParameter(error, "nominalAnnualInterestRate");
        assertParameter(error, "interestCalculationType");
        assertTrue(error.getErrors().size() >= 8);
    }

    @Test
    void currentlyRejectsUnknownPostingPeriodOnCreateButAcceptsItOnUpdate() {
        JsonObject json = validProduct();
        json.addProperty("interestPostingPeriodType", 999);
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(json.toString())),
                "interestPostingPeriodType");
        assertDoesNotThrow(() -> validator.validateForUpdate(json.toString(), product()));
    }

    private JsonObject validProduct() {
        return JsonParser.parseString("""
                {
                  "locale":"en", "monthDayFormat":"MM-dd",
                  "name":"Savings", "shortName":"SAVE", "description":"Savings product",
                  "currencyCode":"USD", "digitsAfterDecimal":2, "inMultiplesOf":1,
                  "nominalAnnualInterestRate":0, "interestCompoundingPeriodType":1,
                  "interestPostingPeriodType":4, "interestCalculationType":1,
                  "interestCalculationDaysInYearType":365, "minRequiredOpeningBalance":0,
                  "lockinPeriodFrequency":1, "lockinPeriodFrequencyType":2,
                  "withdrawalFeeForTransfers":false, "feeAmount":10, "feeOnMonthDay":"02-29",
                  "isDormancyTrackingActive":true, "daysToInactive":30, "daysToDormancy":60, "daysToEscheat":90,
                  "accountingRule":1, "allowOverdraft":false, "overdraftLimit":0,
                  "nominalAnnualInterestRateOverdraft":0, "minOverdraftForInterestCalculation":0,
                  "minBalanceForInterestCalculation":0, "lienAllowed":false, "maxAllowedLienLimit":0,
                  "withHoldTax":true, "taxGroupId":1
                }
                """).getAsJsonObject();
    }

    private SavingsProduct product() {
        SavingsProduct product = mock(SavingsProduct.class);
        when(product.getAccountingType()).thenReturn(1);
        return product;
    }

    private void assertParameter(PlatformApiDataValidationException error, String parameter) {
        assertTrue(error.getErrors().stream().anyMatch(e -> parameter.equals(e.getParameterName())),
                () -> "Expected error for " + parameter + ": " + error.getErrors());
    }
}
