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
package org.apache.fineract.integrationtests;

import static org.apache.fineract.integrationtests.common.BusinessDateHelper.runAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BranchSavingsCounterRegressionTest {

    private static final String ACTIVATION = "01 January 2023";
    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec;
    private SavingsAccountHelper savings;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON)
                .addHeader("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey())
                .addHeader("Fineract-Platform-TenantId", "default").build();
        responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        savings = new SavingsAccountHelper(requestSpec, responseSpec);
    }

    @Test
    public void minimumBalanceCounterTransactionsAndUndo() {
        runAt("31 January 2023", () -> {
            int account = createAccount(true, BigDecimal.ZERO);
            List<CounterTransaction> expected = new ArrayList<>();
            deposit(account, "1000", ACTIVATION, expected);
            assertCounter(account, "1000", "900", "1000", "0", expected);
            withdraw(account, "300", "02 January 2023", expected);
            assertCounter(account, "700", "600", "1000", "300", expected);
            rejectWithdrawal(account, "650", "03 January 2023");
            assertCounter(account, "700", "600", "1000", "300", expected);
            int withdrawal = withdraw(account, "600", "04 January 2023", expected);
            assertCounter(account, "100", "0", "1000", "900", expected);
            rejectWithdrawal(account, "0.01", "05 January 2023");
            assertCounter(account, "100", "0", "1000", "900", expected);
            int deposit = deposit(account, "250", "06 January 2023", expected);
            assertCounter(account, "350", "250", "1250", "900", expected);
            savings.undoSavingsAccountTransaction(account, deposit);
            reverse(expected, deposit);
            assertCounter(account, "100", "0", "1000", "900", expected);
            savings.undoSavingsAccountTransaction(account, withdrawal);
            reverse(expected, withdrawal);
            assertCounter(account, "700", "600", "1000", "300", expected);
        });
    }

    @Test
    public void fullBalanceWithdrawalWithoutOverdraft() {
        runAt("31 January 2023", () -> {
            int account = createAccount(false, BigDecimal.ZERO);
            List<CounterTransaction> expected = new ArrayList<>();
            deposit(account, "1000", ACTIVATION, expected);
            assertCounter(account, "1000", "1000", "1000", "0", expected);
            rejectWithdrawal(account, "1000.01", "02 January 2023");
            assertCounter(account, "1000", "1000", "1000", "0", expected);
            withdraw(account, "1000", "03 January 2023", expected);
            assertCounter(account, "0", "0", "1000", "1000", expected);
            rejectWithdrawal(account, "0.01", "04 January 2023");
            assertCounter(account, "0", "0", "1000", "1000", expected);
        });
    }

    @Test
    public void januaryInterestAndWithdrawalUndo() {
        runAt("01 February 2023", () -> {
            int account = createAccount(false, new BigDecimal("10"));
            int deposit = (Integer) savings.depositToSavingsAccount(account, "10000", ACTIVATION, "resourceId");
            int withdrawal = (Integer) savings.withdrawalFromSavingsAccount(account, "2000", "16 January 2023", "resourceId");
            BigDecimal januaryInterest = new BigDecimal("10000").multiply(new BigDecimal("0.10")).multiply(new BigDecimal("15"))
                    .add(new BigDecimal("8000").multiply(new BigDecimal("0.10")).multiply(new BigDecimal("16")))
                    .divide(new BigDecimal("365"), 2, RoundingMode.HALF_UP);
            savings.postInterestAsOnSavings(account, "31 January 2023");
            assertInterest(account, januaryInterest, new BigDecimal("8000").add(januaryInterest));
            savings.undoSavingsAccountTransaction(account, withdrawal);
            savings.postInterestAsOnSavings(account, "31 January 2023");
            BigDecimal recalculatedInterest = new BigDecimal("10000").multiply(new BigDecimal("0.10")).multiply(new BigDecimal("31"))
                    .divide(new BigDecimal("365"), 2, RoundingMode.HALF_UP);
            assertInterest(account, recalculatedInterest, new BigDecimal("10000").add(recalculatedInterest));
            JsonPath details = details(account);
            assertTrue(details.getBoolean("transactions.find { it.id == " + withdrawal + " }.reversed"));
            money(new BigDecimal("10000"), details.get("transactions.find { it.id == " + deposit + " }.runningBalance"),
                    "deposit running balance");
        });
    }

    private int createAccount(boolean minimumBalance, BigDecimal interestRate) {
        int client = ClientHelper.createClient(requestSpec, responseSpec, ACTIVATION);
        SavingsProductHelper product = new SavingsProductHelper().withNominalAnnualInterestRate(interestRate)
                .withInterestCompoundingPeriodTypeAsMonthly().withInterestPostingPeriodTypeAsMonthly()
                .withInterestCalculationPeriodTypeAsDailyBalance().withAccountingRuleAsNone();
        if (minimumBalance) {
            product.withMinRequiredBalance("100").withEnforceMinRequiredBalance("true");
        }
        JsonObject productJson = JsonParser.parseString(product.build()).getAsJsonObject();
        productJson.addProperty("digitsAfterDecimal", 2);
        int productId = SavingsProductHelper.createSavingsProduct(productJson.toString(), requestSpec, responseSpec);
        int account = savings.applyForSavingsApplicationOnDate(client, productId, "INDIVIDUAL", null, false, ACTIVATION);
        savings.approveSavingsOnDate(account, ACTIVATION);
        savings.activateSavings(account, ACTIVATION);
        assertTrue(details(account).getBoolean("status.active"));
        return account;
    }

    private int deposit(int account, String amount, String date, List<CounterTransaction> expected) {
        int id = (Integer) savings.depositToSavingsAccount(account, amount, date, "resourceId");
        expected.add(new CounterTransaction(id, new BigDecimal(amount), false));
        return id;
    }

    private int withdraw(int account, String amount, String date, List<CounterTransaction> expected) {
        int id = (Integer) savings.withdrawalFromSavingsAccount(account, amount, date, "resourceId");
        expected.add(new CounterTransaction(id, new BigDecimal(amount).negate(), false));
        return id;
    }

    private void rejectWithdrawal(int account, String amount, String date) {
        SavingsAccountHelper rejected = new SavingsAccountHelper(requestSpec, new ResponseSpecBuilder().expectStatusCode(403).build());
        List<Map<String, Object>> errors = (List<Map<String, Object>>) rejected.withdrawalFromSavingsAccount(account, amount, date,
                "errors");
        assertEquals("error.msg.savingsaccount.transaction.insufficient.account.balance",
                errors.get(0).get("userMessageGlobalisationCode"));
    }

    private void assertCounter(int account, String balance, String available, String deposits, String withdrawals,
            List<CounterTransaction> expected) {
        JsonPath details = details(account);
        assertNotNull(details.get("summary.accountBalance"));
        assertNotNull(details.get("summary.availableBalance"));
        money(new BigDecimal(balance), details.get("summary.accountBalance"), "account balance");
        money(new BigDecimal(available), details.get("summary.availableBalance"), "available balance");
        money(new BigDecimal(deposits), details.get("summary.totalDeposits"), "total deposits");
        money(new BigDecimal(withdrawals), details.get("summary.totalWithdrawals"), "total withdrawals");
        List<Map<String, Object>> transactions = details.getList("transactions");
        transactions.sort(Comparator.comparingLong(transaction -> ((Number) transaction.get("id")).longValue()));
        assertEquals(expected.size(), transactions.size());
        BigDecimal runningBalance = BigDecimal.ZERO;
        for (int i = 0; i < expected.size(); i++) {
            CounterTransaction transaction = expected.get(i);
            Map<String, Object> actual = transactions.get(i);
            assertEquals(transaction.id(), ((Number) actual.get("id")).intValue());
            assertEquals(transaction.reversed(), actual.get("reversed"));
            money(transaction.amount().abs(), actual.get("amount"), "transaction amount");
            if (!transaction.reversed()) {
                runningBalance = runningBalance.add(transaction.amount());
                money(runningBalance, actual.get("runningBalance"), "running balance for transaction " + transaction.id());
            }
        }
        money(new BigDecimal(balance), runningBalance, "independent ledger balance");
    }

    private void assertInterest(int account, BigDecimal interest, BigDecimal balance) {
        JsonPath details = details(account);
        List<Map<String, Object>> postings = details.getList("transactions.findAll { it.transactionType.interestPosting && !it.reversed }");
        assertEquals(1, postings.size());
        // January accrues inclusively through 31 January; monthly interest is booked on the following day.
        assertEquals(List.of(2023, 2, 1), postings.get(0).get("date"));
        money(interest, postings.get(0).get("amount"), "January interest posting");
        money(interest, details.get("summary.totalInterestPosted"), "total interest posted");
        money(balance, details.get("summary.accountBalance"), "interest-inclusive account balance");
        money(balance, details.get("summary.availableBalance"), "interest-inclusive available balance");
    }

    private JsonPath details(int account) {
        return RestAssured.given().spec(requestSpec).get("/fineract-provider/api/v1/savingsaccounts/" + account + "?associations=all")
                .then().spec(responseSpec).extract().jsonPath();
    }

    private static void money(BigDecimal expected, Object actual, String message) {
        BigDecimal value = actual == null ? BigDecimal.ZERO : new BigDecimal(actual.toString());
        assertEquals(0, expected.compareTo(value), message + ": expected " + expected + ", actual " + actual);
    }

    private static void reverse(List<CounterTransaction> expected, int id) {
        expected.replaceAll(transaction -> transaction.id() == id ? new CounterTransaction(id, transaction.amount(), true) : transaction);
    }

    private record CounterTransaction(int id, BigDecimal amount, boolean reversed) {
    }
}
