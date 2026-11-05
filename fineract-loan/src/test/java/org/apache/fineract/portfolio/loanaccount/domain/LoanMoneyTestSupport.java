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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

public abstract class LoanMoneyTestSupport {

    protected final MonetaryCurrency currency = new MonetaryCurrency("USD", 2, 1);
    protected final LocalDate dueDate = LocalDate.of(2024, 2, 29);
    private MockedStatic<MoneyHelper> moneyHelper;

    @BeforeEach
    void configureMoneyAndBusinessDate() {
        moneyHelper = mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, dueDate)));
    }

    @AfterEach
    void clearMoneyAndBusinessDate() {
        moneyHelper.close();
        ThreadLocalContextUtil.reset();
    }

    protected Money money(String value) {
        return Money.of(currency, new BigDecimal(value));
    }

    protected LoanRepaymentScheduleInstallment installment(int number, LocalDate from, LocalDate due) {
        return new LoanRepaymentScheduleInstallment(null, number, from, due, new BigDecimal("100"), new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal("5"), false, null, BigDecimal.ZERO);
    }

    protected void assertAmount(String expected, Money actual) {
        assertDecimal(expected, actual.getAmount());
    }

    protected void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "Expected " + expected + " but was " + actual);
    }
}
