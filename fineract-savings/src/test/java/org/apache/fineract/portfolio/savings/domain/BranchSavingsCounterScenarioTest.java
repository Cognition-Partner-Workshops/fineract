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
package org.apache.fineract.portfolio.savings.domain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class BranchSavingsCounterScenarioTest {

    private final MonetaryCurrency currency = new MonetaryCurrency("USD", 2, 1);
    private MockedStatic<MoneyHelper> moneyHelper;

    @BeforeEach
    void configureBranchCurrency() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 1, 1))));
        moneyHelper = mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(new MathContext(19, RoundingMode.HALF_EVEN));
    }

    @AfterEach
    void releaseBranchCurrency() {
        moneyHelper.close();
        ThreadLocalContextUtil.reset();
    }

    private Money money(String amount) {
        return Money.of(currency, new BigDecimal(amount));
    }

    @ParameterizedTest(name = "pivot={0}")
    @ValueSource(booleans = { false, true })
    void tellerQuotesAvailableBalanceAfterCounterDepositsWithdrawalsAndFee(boolean pivot) {
        SavingsAccount account = mock(SavingsAccount.class);
        SavingsAccountSummary summary = new SavingsAccountSummary();
        SavingsAccountTransactionSummaryWrapper wrapper = new SavingsAccountTransactionSummaryWrapper();
        LocalDate date = LocalDate.of(2026, 1, 1);
        List<SavingsAccountTransaction> transactions = new ArrayList<>();
        SoftAssertions softly = new SoftAssertions();

        summary.updateSummary(currency, wrapper, transactions);
        // no transactions
        assertTotals(softly, "empty account", summary, "0.00", "0.00", "0.00", "0.00");
        add(summary, wrapper, transactions, SavingsAccountTransaction.deposit(account, null, null, date, money("500.00"), null), pivot);
        // 0 + 500
        assertTotals(softly, "first deposit", summary, "500.00", "500.00", "0.00", "0.00");
        add(summary, wrapper, transactions, SavingsAccountTransaction.deposit(account, null, null, date.plusDays(1), money("250.00"), null),
                pivot);
        // 500 + 250
        assertTotals(softly, "second deposit", summary, "750.00", "750.00", "0.00", "0.00");
        add(summary, wrapper, transactions,
                SavingsAccountTransaction.withdrawal(account, null, null, date.plusDays(2), money("200.00"), null), pivot);
        // 750 - 200
        assertTotals(softly, "first withdrawal", summary, "550.00", "750.00", "200.00", "0.00");
        add(summary, wrapper, transactions, SavingsAccountTransaction.withdrawalFee(account, null, date.plusDays(2), money("2.00"), null),
                pivot);
        // 750 - 200 - 2
        assertTotals(softly, "withdrawal fee", summary, "548.00", "750.00", "200.00", "2.00");
        add(summary, wrapper, transactions,
                SavingsAccountTransaction.withdrawal(account, null, null, date.plusDays(3), money("100.00"), null), pivot);
        // 750 - (200 + 100) - 2
        assertTotals(softly, "second withdrawal", summary, "448.00", "750.00", "300.00", "2.00");
        softly.assertAll();
    }

    private void add(SavingsAccountSummary summary, SavingsAccountTransactionSummaryWrapper wrapper,
            List<SavingsAccountTransaction> transactions, SavingsAccountTransaction transaction, boolean pivot) {
        transactions.add(transaction);
        if (pivot) {
            summary.updateSummaryWithPivotConfig(currency, wrapper, transaction, transactions);
        } else {
            summary.updateSummary(currency, wrapper, transactions);
        }
    }

    private void assertTotals(SoftAssertions softly, String stage, SavingsAccountSummary summary, String balance, String deposits,
            String withdrawals, String fees) {
        softly.assertThat(summary.getAccountBalance()).as(stage + " balance").isEqualByComparingTo(balance);
        softly.assertThat(Money.of(currency, summary.getTotalDeposits()).getAmount()).as(stage + " deposits")
                .isEqualByComparingTo(deposits);
        softly.assertThat(Money.of(currency, summary.getTotalWithdrawals()).getAmount()).as(stage + " withdrawals")
                .isEqualByComparingTo(withdrawals);
        softly.assertThat(Money.of(currency, summary.getTotalWithdrawalFees()).getAmount()).as(stage + " withdrawal fees")
                .isEqualByComparingTo(fees);
    }
}
