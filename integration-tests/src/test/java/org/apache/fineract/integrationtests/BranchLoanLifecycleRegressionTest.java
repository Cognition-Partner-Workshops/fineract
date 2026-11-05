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

import io.restassured.path.json.JsonPath;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.client.models.AdvancedPaymentData;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.portfolio.loanproduct.domain.PaymentAllocationType;
import org.junit.jupiter.api.Test;

public class BranchLoanLifecycleRegressionTest extends BaseLoanIntegrationTest {

    private static final BigDecimal PRINCIPAL = new BigDecimal("12000.00");
    private static final BigDecimal RATE = new BigDecimal("0.01");
    private static final BigDecimal PARTIAL = new BigDecimal("500.00");
    private static final BigDecimal WAIVER = new BigDecimal("120.00");
    private static final String DISBURSEMENT = "01 January 2023";

    @Test
    public void flatCumulativeLifecycle() {
        lifecycle(true, false);
    }

    @Test
    public void decliningCumulativeLifecycle() {
        lifecycle(false, false);
    }

    @Test
    public void decliningProgressiveLifecycle() {
        lifecycle(false, true);
    }

    private void lifecycle(boolean flat, boolean progressive) {
        List<Instalment> expected = expectedSchedule(flat);
        BigDecimal totalInterest = expected.stream().map(Instalment::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal firstPayment = expected.get(0).total();
        BigDecimal principalOutstanding = PRINCIPAL.subtract(expected.get(0).principal())
                .subtract(PARTIAL.subtract(expected.get(1).interest()));
        BigDecimal interestOutstanding = totalInterest.subtract(expected.get(0).interest()).subtract(expected.get(1).interest());
        BigDecimal outstanding = principalOutstanding.add(interestOutstanding);
        AtomicReference<Long> loan = new AtomicReference<>();
        runAt(DISBURSEMENT, () -> {
            Long client = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest().activationDate(DISBURSEMENT)).getClientId();
            String strategy = progressive ? "advanced-payment-allocation-strategy" : "mifos-standard-strategy";
            PostLoanProductsRequest product = create4IProgressive().principal(12000.0).maxPrincipal(12000.0).numberOfRepayments(12)
                    .interestRatePerPeriod(12.0).daysInMonthType(30).daysInYearType(360).isInterestRecalculationEnabled(false)
                    .interestType(flat ? 1 : 0).interestCalculationPeriodType(1)
                    .loanScheduleType(progressive ? "PROGRESSIVE" : "CUMULATIVE").transactionProcessingStrategyCode(strategy)
                    .multiDisburseLoan(false).disallowExpectedDisbursements(false);
            if (!progressive) {
                product.paymentAllocation(null).creditAllocation(null).loanScheduleProcessingType(null);
            } else {
                AdvancedPaymentData allocation = createDefaultPaymentAllocation("NEXT_INSTALLMENT");
                allocation.setPaymentAllocationOrder(getPaymentAllocationOrder(PaymentAllocationType.PAST_DUE_PENALTY,
                        PaymentAllocationType.PAST_DUE_FEE, PaymentAllocationType.PAST_DUE_INTEREST,
                        PaymentAllocationType.PAST_DUE_PRINCIPAL, PaymentAllocationType.DUE_PENALTY, PaymentAllocationType.DUE_FEE,
                        PaymentAllocationType.DUE_INTEREST, PaymentAllocationType.DUE_PRINCIPAL, PaymentAllocationType.IN_ADVANCE_PENALTY,
                        PaymentAllocationType.IN_ADVANCE_FEE, PaymentAllocationType.IN_ADVANCE_INTEREST,
                        PaymentAllocationType.IN_ADVANCE_PRINCIPAL));
                product.paymentAllocation(List.of(allocation));
            }
            Long productId = loanProductHelper.createLoanProduct(product).getResourceId();
            Long id = applyAndApproveCumulativeLoan(client, productId, DISBURSEMENT, 12000.0, 12.0, 12, request -> request
                    .interestType(flat ? 1 : 0).interestCalculationPeriodType(1).transactionProcessingStrategyCode(strategy));
            loan.set(id);
            disburseLoan(id, PRINCIPAL, DISBURSEMENT);
            JsonPath details = details(id);
            List<Map<String, Object>> periods = details.getList("repaymentSchedule.periods.findAll { it.period != null }");
            assertEquals(12, periods.size());
            for (int i = 0; i < 12; i++) {
                Map<String, Object> period = periods.get(i);
                Instalment instalment = expected.get(i);
                LocalDate due = LocalDate.of(2023, 1, 1).plusMonths(i + 1);
                assertEquals(List.of(due.getYear(), due.getMonthValue(), due.getDayOfMonth()), period.get("dueDate"));
                money(instalment.principal(), period.get("principalDue"), "principal instalment " + (i + 1));
                money(instalment.interest(), period.get("interestDue"), "interest instalment " + (i + 1));
                money(instalment.total(), period.get("totalDueForPeriod"), "total instalment " + (i + 1));
            }
            money(totalInterest, details.get("summary.interestCharged"), "total interest");
            money(PRINCIPAL.add(totalInterest), details.get("repaymentSchedule.totalRepaymentExpected"), "total repayable");
        });
        runAt("01 February 2023",
                () -> loanTransactionHelper.makeRepayment("01 February 2023", firstPayment.floatValue(), loan.get().intValue()));
        runAt("01 March 2023", () -> loanTransactionHelper.makeRepayment("01 March 2023", PARTIAL.floatValue(), loan.get().intValue()));
        runAt("15 March 2023", () -> {
            Long id = loan.get();
            inlineLoanCOBHelper.executeInlineCOB(id);
            JsonPath before = details(id);
            assertOutstanding(before, principalOutstanding, interestOutstanding, outstanding);
            money(expected.get(1).total().subtract(PARTIAL), before.get("summary.totalOverdue"), "overdue");
            assertTrue(before.getBoolean("inArrears"));
            assertEquals(List.of(2023, 3, 1), before.getList("delinquent.nextPaymentDueDate"));
            money(expected.get(1).total().subtract(PARTIAL), before.get("delinquent.nextPaymentAmount"), "next payment");
            loanTransactionHelper.waiveInterest("15 March 2023", WAIVER.toPlainString(), id.intValue());
            JsonPath waived = details(id);
            money(WAIVER, waived.get("summary.interestWaived"), "interest waived");
            assertOutstanding(waived, principalOutstanding, interestOutstanding.subtract(WAIVER), outstanding.subtract(WAIVER));
            List<Map<String, Object>> transactions = waived.getList("transactions.findAll { it.type.waiveInterest }");
            assertEquals(1, transactions.size());
            money(WAIVER, transactions.get(0).get("amount"), "waiver transaction");
            List<Object> waivedAmounts = waived.getList("repaymentSchedule.periods.findAll { it.period != null }.interestWaived");
            BigDecimal sum = waivedAmounts.stream().map(value -> value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            money(WAIVER, sum, "schedule interest waived");
            loanTransactionHelper.makeRepayment("15 March 2023", outstanding.subtract(WAIVER).floatValue(), id.intValue());
            JsonPath closed = details(id);
            assertTrue(closed.getBoolean("status.closedObligationsMet"));
            assertOutstanding(closed, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            money(PRINCIPAL, closed.get("summary.principalPaid"), "principal paid");
            money(totalInterest.subtract(WAIVER), closed.get("summary.interestPaid"), "interest paid");
            money(PRINCIPAL.add(totalInterest).subtract(WAIVER), closed.get("summary.totalRepayment"), "total paid");
        });
    }

    private JsonPath details(Long loanId) {
        return JsonPath.from(loanTransactionHelper.getLoanDetails(requestSpec, responseSpec, loanId.intValue()));
    }

    private void assertOutstanding(JsonPath details, BigDecimal principal, BigDecimal interest, BigDecimal total) {
        money(principal, details.get("summary.principalOutstanding"), "principal outstanding");
        money(interest, details.get("summary.interestOutstanding"), "interest outstanding");
        money(total, details.get("summary.totalOutstanding"), "total outstanding");
    }

    private static void money(BigDecimal expected, Object actual, String message) {
        assertEquals(0, expected.compareTo(new BigDecimal(actual.toString())), message + ": expected " + expected + ", actual " + actual);
    }

    private static List<Instalment> expectedSchedule(boolean flat) {
        BigDecimal factor = BigDecimal.ONE.add(RATE).pow(12, MathContext.DECIMAL128);
        BigDecimal emi = PRINCIPAL.multiply(RATE).multiply(factor).divide(factor.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
        BigDecimal balance = PRINCIPAL;
        List<Instalment> result = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            BigDecimal interest = flat ? new BigDecimal("120.00") : balance.multiply(RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal = flat ? new BigDecimal("1000.00") : i == 11 ? balance : emi.subtract(interest);
            result.add(new Instalment(principal, interest));
            balance = balance.subtract(principal);
        }
        return result;
    }

    private record Instalment(BigDecimal principal, BigDecimal interest) {

        BigDecimal total() {
            return principal.add(interest);
        }
    }
}
