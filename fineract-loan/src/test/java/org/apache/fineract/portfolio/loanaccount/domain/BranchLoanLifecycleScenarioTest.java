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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BranchLoanLifecycleScenarioTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("USD", 2, 1);
    private MockedStatic<MoneyHelper> moneyHelper;

    @BeforeEach
    void configureBranchCurrency() {
        moneyHelper = mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(new MathContext(19, RoundingMode.HALF_EVEN));
    }

    @AfterEach
    void releaseBranchCurrency() {
        moneyHelper.close();
    }

    private Money money(String amount) {
        return Money.of(currency, new BigDecimal(amount));
    }

    @Test
    void loanOfficerQuotesBalancesAfterInterestWaivedOnUpheldComplaint() {
        Loan loan = mock(Loan.class);
        LocalDate disbursement = LocalDate.of(2026, 1, 1);
        List<LoanRepaymentScheduleInstallment> schedule = List.of(installment(loan, 1, disbursement, "15.00"),
                installment(loan, 2, disbursement.plusMonths(1), "0.00"), installment(loan, 3, disbursement.plusMonths(2), "0.00"));
        LoanSummary summary = LoanSummary.create(BigDecimal.ZERO);
        SoftAssertions softly = new SoftAssertions();

        recompute(summary, schedule);
        // 3 * 400
        softly.assertThat(summary.getTotalPrincipalDisbursed()).as("disbursed principal").isEqualByComparingTo("1200.00");
        // 1200 + 90 + 15
        softly.assertThat(summary.getTotalExpectedRepayment()).as("contractual repayment").isEqualByComparingTo("1305.00");
        // 1200 - 0
        softly.assertThat(summary.getTotalPrincipalOutstanding()).as("initial principal").isEqualByComparingTo("1200.00");
        // 3 * 30
        softly.assertThat(summary.getTotalInterestOutstanding()).as("initial interest").isEqualByComparingTo("90.00");
        // 15 - 0
        softly.assertThat(summary.getTotalFeeChargesOutstanding()).as("initial fee").isEqualByComparingTo("15.00");
        // 1200 + 90 + 15
        softly.assertThat(summary.getTotalOutstanding()).as("initial balance").isEqualByComparingTo("1305.00");
        // no payments
        softly.assertThat(summary.getTotalRepayment()).as("initial repaid").isEqualByComparingTo("0.00");

        LocalDate firstDue = disbursement.plusMonths(1);
        schedule.get(0).payFeeChargesComponent(firstDue, money("15.00"));
        schedule.get(0).payInterestComponent(firstDue, money("30.00"));
        schedule.get(0).payPrincipalComponent(firstDue, money("400.00"));
        recompute(summary, schedule);
        // 400
        softly.assertThat(summary.getTotalPrincipalRepaid()).as("first principal repaid").isEqualByComparingTo("400.00");
        // 30
        softly.assertThat(summary.getTotalInterestRepaid()).as("first interest repaid").isEqualByComparingTo("30.00");
        // 15
        softly.assertThat(summary.getTotalFeeChargesRepaid()).as("fee repaid").isEqualByComparingTo("15.00");
        // 1200 - 400
        softly.assertThat(summary.getTotalPrincipalOutstanding()).as("first principal balance").isEqualByComparingTo("800.00");
        // 90 - 30
        softly.assertThat(summary.getTotalInterestOutstanding()).as("first interest balance").isEqualByComparingTo("60.00");
        // 15 - 15
        softly.assertThat(summary.getTotalFeeChargesOutstanding()).as("fee balance").isEqualByComparingTo("0.00");
        // 400 + 30 + 15
        softly.assertThat(summary.getTotalRepayment()).as("first payment").isEqualByComparingTo("445.00");
        // 1305 - 445
        softly.assertThat(summary.getTotalOutstanding()).as("first balance").isEqualByComparingTo("860.00");

        LocalDate secondDue = disbursement.plusMonths(2);
        schedule.get(1).payInterestComponent(secondDue, money("30.00"));
        schedule.get(1).payPrincipalComponent(secondDue, money("70.00"));
        recompute(summary, schedule);
        // 400 + 70
        softly.assertThat(summary.getTotalPrincipalRepaid()).as("partial principal repaid").isEqualByComparingTo("470.00");
        // 30 + 30
        softly.assertThat(summary.getTotalInterestRepaid()).as("partial interest repaid").isEqualByComparingTo("60.00");
        // 15 + 0
        softly.assertThat(summary.getTotalFeeChargesRepaid()).as("partial fee repaid").isEqualByComparingTo("15.00");
        // 1200 - 470
        softly.assertThat(summary.getTotalPrincipalOutstanding()).as("partial principal balance").isEqualByComparingTo("730.00");
        // 90 - 60
        softly.assertThat(summary.getTotalInterestOutstanding()).as("partial interest balance").isEqualByComparingTo("30.00");
        // 15 - 15
        softly.assertThat(summary.getTotalFeeChargesOutstanding()).as("partial fee balance").isEqualByComparingTo("0.00");
        // 445 + 100
        softly.assertThat(summary.getTotalRepayment()).as("partial total repaid").isEqualByComparingTo("545.00");
        // 730 + 30
        softly.assertThat(summary.getTotalOutstanding()).as("partial balance").isEqualByComparingTo("760.00");

        schedule.get(2).waiveInterestComponent(secondDue, money("30.00"));
        recompute(summary, schedule);
        // last installment's 30
        softly.assertThat(summary.getTotalInterestWaived()).as("complaint interest waived").isEqualByComparingTo("30.00");
        // 90 - 60 - 30
        softly.assertThat(summary.getTotalInterestOutstanding()).as("complaint interest balance").isEqualByComparingTo("0.00");
        // 30 + 0 fees
        softly.assertThat(summary.getTotalWaived()).as("complaint total waived").isEqualByComparingTo("30.00");
        // 760 - 30
        softly.assertThat(summary.getTotalOutstanding()).as("complaint balance").isEqualByComparingTo("730.00");
        // 445 + 100
        softly.assertThat(summary.getTotalRepayment()).as("complaint repaid unchanged").isEqualByComparingTo("545.00");
        softly.assertAll();
    }

    private LoanRepaymentScheduleInstallment installment(Loan loan, int number, LocalDate from, String fee) {
        return new LoanRepaymentScheduleInstallment(loan, number, from, from.plusMonths(1), new BigDecimal("400.00"),
                new BigDecimal("30.00"), new BigDecimal(fee), BigDecimal.ZERO, false, Set.of(), BigDecimal.ZERO);
    }

    private void recompute(LoanSummary summary, List<LoanRepaymentScheduleInstallment> schedule) {
        summary.updateSummary(currency, money("1200.00"), schedule, Set.of(), money("0.00"), money("0.00"));
    }
}
