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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.RestAssured;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.junit.jupiter.api.Test;

public class ClientSummaryReportRegressionTest extends BaseLoanIntegrationTest {

    private static final String START = "01 January 2023";

    @Test
    public void closedLoanAndActiveSavingsStillReturnOneSummary() {
        runAt("01 February 2023", () -> {
            Long client = client();
            Long loan = activeLoan(client, product(), 1000);
            loanTransactionHelper.makeRepayment("01 February 2023", 1000f, loan.intValue());
            assertTrue(loanTransactionHelper.getLoanDetails(loan).getStatus().getClosedObligationsMet());
            SavingsAccountHelper savings = new SavingsAccountHelper(requestSpec, responseSpec);
            int savingsProduct = SavingsProductHelper.createSavingsProduct(
                    new SavingsProductHelper().withNominalAnnualInterestRate(BigDecimal.ZERO).build(), requestSpec, responseSpec);
            int account = savings.applyForSavingsApplicationOnDate(client.intValue(), savingsProduct, "INDIVIDUAL", null, false, START);
            savings.approveSavingsOnDate(account, START);
            savings.activateSavings(account, START);
            savings.depositToSavingsAccount(account, "380", START, "resourceId");
            assertSummary(client, 0, 0, 0, 1, 380);
        });
    }

    @Test
    public void oneActiveLoanReturnsItsPrincipal() {
        runAt("01 February 2023", () -> {
            Long client = client();
            activeLoan(client, product(), 1000);
            assertSummary(client, 1, 1, 1000, 0, 0);
        });
    }

    @Test
    public void sameDayActiveLoansReturnOneRowAndLatestLoanPrincipal() {
        runAt("01 February 2023", () -> {
            Long client = client();
            Long product = product();
            Long first = activeLoan(client, product, 1000);
            Long second = activeLoan(client, product, 2000);
            assertTrue(second > first);
            assertSummary(client, 2, 2, 2000, 0, 0);
        });
    }

    private Long client() {
        return ClientHelper.createClient(ClientHelper.defaultClientCreationRequest().activationDate(START)).getClientId();
    }

    private Long product() {
        PostLoanProductsRequest product = create4IProgressive().principal(1000.0).numberOfRepayments(1).interestRatePerPeriod(0.0)
                .includeInBorrowerCycle(true).isInterestRecalculationEnabled(false).loanScheduleType("CUMULATIVE")
                .transactionProcessingStrategyCode("mifos-standard-strategy").multiDisburseLoan(false).disallowExpectedDisbursements(false)
                .paymentAllocation(null).creditAllocation(null).loanScheduleProcessingType(null);
        return loanProductHelper.createLoanProduct(product).getResourceId();
    }

    private Long activeLoan(Long client, Long product, int principal) {
        Long loan = applyAndApproveCumulativeLoan(client, product, START, (double) principal, 0.0, 1,
                request -> request.transactionProcessingStrategyCode("mifos-standard-strategy"));
        disburseLoan(loan, BigDecimal.valueOf(principal), START);
        assertTrue(loanTransactionHelper.getLoanDetails(loan).getStatus().getActive());
        return loan;
    }

    private void assertSummary(Long client, int loanCycle, int activeLoans, int lastLoanAmount, int activeSavings, int totalSavings) {
        List<Map<String, Object>> rows = RestAssured.given().spec(requestSpec).queryParam("R_clientId", client)
                .queryParam("genericResultSet", false).get("/fineract-provider/api/v1/runreports/ClientSummary").then().spec(responseSpec)
                .extract().jsonPath().getList("");
        assertEquals(1, rows.size(), "ClientSummary must return exactly one row: " + rows);
        Map<String, Object> row = rows.get(0);
        assertNumber(loanCycle, row.get("loanCycle"), "loanCycle");
        assertNumber(activeLoans, row.get("activeLoans"), "activeLoans");
        assertNumber(lastLoanAmount, row.get("lastLoanAmount"), "lastLoanAmount");
        assertNumber(activeSavings, row.get("activeSavings"), "activeSavings");
        assertNumber(totalSavings, row.get("totalSavings"), "totalSavings");
    }

    private static void assertNumber(int expected, Object actual, String field) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(new BigDecimal(actual.toString())), field);
    }
}
