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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SavingsProductAccountingDataValidatorTest {

    private final SavingsProductAccountingDataValidator validator = new SavingsProductAccountingDataValidator(new FromJsonHelper());

    @ParameterizedTest
    @ValueSource(ints = { 2, 3 })
    void accountingCreationRequiresPositiveLedgerAccountsIncludingDormancyLiability(int rule) {
        List<ApiParameterError> errors = validate(rule, true, "{}", DepositAccountType.SAVINGS_DEPOSIT, true);
        for (String name : List.of("savingsControlAccountId", "savingsReferenceAccountId", "transfersInSuspenseAccountId",
                "interestOnSavingsAccountId", "incomeFromFeeAccountId", "incomeFromPenaltyAccountId", "overdraftPortfolioControlId",
                "incomeFromInterestId", "writeOffAccountId", "escheatLiabilityId")) {
            assertTrue(errors.stream().anyMatch(e -> name.equals(e.getParameterName())), name);
        }
        assertTrue(errors.stream().anyMatch(e -> "interestPayableAccountId".equals(e.getParameterName())) == (rule == 3));
        assertTrue(validate(rule, true, accounts().toString(), DepositAccountType.SAVINGS_DEPOSIT, true).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({ "FIXED_DEPOSIT,2", "FIXED_DEPOSIT,3", "RECURRING_DEPOSIT,2", "RECURRING_DEPOSIT,3" })
    void termDepositsDoNotRequireOverdraftLedgerAccounts(DepositAccountType type, int rule) {
        JsonObject json = accounts();
        json.remove("overdraftPortfolioControlId");
        json.remove("incomeFromInterestId");
        json.remove("writeOffAccountId");
        assertTrue(validate(rule, true, json.toString(), type, true).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = { "savingsControlAccountId", "savingsReferenceAccountId", "transfersInSuspenseAccountId",
            "interestOnSavingsAccountId", "incomeFromFeeAccountId", "incomeFromPenaltyAccountId", "overdraftPortfolioControlId",
            "incomeFromInterestId", "writeOffAccountId", "escheatLiabilityId", "feesReceivableAccountId", "penaltiesReceivableAccountId",
            "interestPayableAccountId" })
    void partialUpdateValidatesEachSuppliedLedgerAccountWithoutRequiringOthers(String name) {
        JsonObject json = new JsonObject();
        json.addProperty(name, 0);
        List<ApiParameterError> errors = validate(3, true, json.toString(), DepositAccountType.SAVINGS_DEPOSIT, false);
        assertTrue(errors.stream().anyMatch(e -> name.equals(e.getParameterName())));
        json.addProperty(name, 1);
        assertTrue(validate(3, true, json.toString(), DepositAccountType.SAVINGS_DEPOSIT, false).isEmpty());
    }

    @Test
    void disabledAccountingAndDormancyDoNotRequireTheirLedgerAccounts() {
        assertTrue(validate(1, false, "{}", DepositAccountType.SAVINGS_DEPOSIT, true).isEmpty());
        assertTrue(validate(2, false, "{}", DepositAccountType.SAVINGS_DEPOSIT, false).isEmpty());
        JsonObject json = accounts();
        json.remove("escheatLiabilityId");
        assertTrue(validate(2, false, json.toString(), DepositAccountType.SAVINGS_DEPOSIT, true).isEmpty());
    }

    @Test
    void validatesEveryPaymentChannelAndPenaltyMapping() {
        String invalid = """
                {"paymentChannelToFundSourceMappings":[
                  {"paymentTypeId":1,"fundSourceAccountId":2},{"paymentTypeId":0,"fundSourceAccountId":0}],
                 "penaltyToIncomeAccountMappings":[
                  {"chargeId":1,"incomeAccountId":2},{"chargeId":0,"incomeAccountId":0}]}
                """;
        List<ApiParameterError> errors = validate(2, false, invalid, DepositAccountType.SAVINGS_DEPOSIT, false);
        assertTrue(errors.stream().anyMatch(e -> e.getParameterName().startsWith("paymentChannelToFundSourceMappings[1]")));
        assertTrue(errors.stream().anyMatch(e -> "penaltyToIncomeAccountMappings[1].chargeId".equals(e.getParameterName())));
        assertTrue(errors.stream().anyMatch(e -> "penaltyToIncomeAccountMappings[1].incomeAccountId".equals(e.getParameterName())));
        assertFalse(errors.stream().anyMatch(e -> e.getParameterName().contains("[0]")));
        String valid = """
                {"paymentChannelToFundSourceMappings":[{"paymentTypeId":1,"fundSourceAccountId":2}],
                 "penaltyToIncomeAccountMappings":[{"chargeId":1,"incomeAccountId":2}]}
                """;
        assertTrue(validate(2, false, valid, DepositAccountType.SAVINGS_DEPOSIT, false).isEmpty());
    }

    @Test
    void currentlySkipsInvalidFeeIncomeMappingWhileRejectingEquivalentPenaltyMapping() {
        assertTrue(validate(2, false, """
                {"feeToIncomeAccountMappings":[{"chargeId":0,"incomeAccountId":0}]}
                """, DepositAccountType.SAVINGS_DEPOSIT, false).isEmpty());
        assertFalse(validate(2, false, """
                {"penaltyToIncomeAccountMappings":[{"chargeId":0,"incomeAccountId":0}]}
                """, DepositAccountType.SAVINGS_DEPOSIT, false).isEmpty());
    }

    private List<ApiParameterError> validate(int rule, boolean dormancy, String json, DepositAccountType type, boolean creation) {
        List<ApiParameterError> errors = new ArrayList<>();
        validator.evaluateProductAccountingData(rule, dormancy, JsonParser.parseString(json), new DataValidatorBuilder(errors), type,
                creation);
        return errors;
    }

    private JsonObject accounts() {
        return JsonParser.parseString("""
                {"savingsControlAccountId":1,"savingsReferenceAccountId":2,"transfersInSuspenseAccountId":3,
                 "interestOnSavingsAccountId":4,"incomeFromFeeAccountId":5,"incomeFromPenaltyAccountId":6,
                 "overdraftPortfolioControlId":7,"incomeFromInterestId":8,"writeOffAccountId":9,
                 "escheatLiabilityId":10,"feesReceivableAccountId":11,"penaltiesReceivableAccountId":12,
                 "interestPayableAccountId":13}
                """).getAsJsonObject();
    }
}
