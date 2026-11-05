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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.monetary.mapper.CurrencyMapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDays;
import org.apache.fineract.portfolio.common.domain.DaysInMonthType;
import org.apache.fineract.portfolio.common.domain.DaysInYearType;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsDataWrapper;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.CumulativeDecliningBalanceInterestLoanScheduleGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultPaymentPeriodsInOneYearCalculator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanRepaymentScheduleModelData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.apache.fineract.portfolio.loanproduct.domain.AmortizationMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestCalculationPeriodMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.apache.fineract.portfolio.loanproduct.domain.RepaymentStartDateType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

class CumulativeLoanScheduleOutstandingTest {

    @Test
    void generatedScheduleOutstandingEqualsUnpaidRepaymentExpected() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2023, 1, 1))));
        try (MockedStatic<MoneyHelper> helper = mockStatic(MoneyHelper.class)) {
            helper.when(MoneyHelper::getMathContext).thenReturn(MathContext.DECIMAL128);
            helper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_UP);
            var currency = new ApplicationCurrency("USD", "USD", 2, 1, "USD", "$").toData();
            var date = LocalDate.of(2023, 1, 1);
            var model = new LoanRepaymentScheduleModelData(date, currency, new BigDecimal("1200"), date, 3, 1, "MONTHS",
                    new BigDecimal("12"), false, DaysInMonthType.DAYS_30, DaysInYearType.DAYS_360, null, null, null, false, null,
                    InterestMethod.DECLINING_BALANCE, true, false);
            var terms = LoanApplicationTerms.assembleFrom(model, MathContext.DECIMAL128);
            var holidays = new HolidayDetailDTO(false, List.of(),
                    new WorkingDays("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR,SA,SU", 1, false, false));
            ReflectionTestUtils.setField(terms, "amortizationMethod", AmortizationMethod.EQUAL_PRINCIPAL);
            ReflectionTestUtils.setField(terms, "interestCalculationPeriodMethod",
                    InterestCalculationPeriodMethod.SAME_AS_REPAYMENT_PERIOD);
            ReflectionTestUtils.setField(terms, "variationsDataWrapper", new LoanTermVariationsDataWrapper(new ArrayList<>()));
            ReflectionTestUtils.setField(terms, "actualNumberOfRepayments", 3);
            ReflectionTestUtils.setField(terms, "repaymentsStartingFromDate", null);
            ReflectionTestUtils.setField(terms, "repaymentStartDateType", RepaymentStartDateType.DISBURSEMENT_DATE);
            ReflectionTestUtils.setField(terms, "loanScheduleType", LoanScheduleType.CUMULATIVE);
            ReflectionTestUtils.setField(terms, "holidayDetailDTO", holidays);
            ReflectionTestUtils.setField(terms, "isInterestChargedFromDateSameAsDisbursalDateEnabled", true);
            ReflectionTestUtils.setField(terms, "approvedPrincipal", terms.getPrincipal());
            ReflectionTestUtils.setField(terms, "disbursedPrincipal", terms.getPrincipal());
            ReflectionTestUtils.setField(terms, "adjustPrincipalForFlatLoans", Money.zero(currency));
            var generator = new CumulativeDecliningBalanceInterestLoanScheduleGenerator(new DefaultScheduledDateGenerator(),
                    new DefaultPaymentPeriodsInOneYearCalculator(), mock(LoanTransactionRepository.class), mock(CurrencyMapper.class));

            LoanScheduleModel schedule = generator.generate(MathContext.DECIMAL128, terms, new HashSet<>(), holidays);

            assertEquals(0, new BigDecimal("1224").compareTo(schedule.getTotalRepaymentExpected()));
            assertEquals(0, schedule.getTotalRepaymentExpected().compareTo(schedule.getTotalOutstanding()));
            assertEquals(0, new BigDecimal("1224").compareTo(schedule.toData().getTotalOutstanding()));
        } finally {
            ThreadLocalContextUtil.reset();
        }
    }
}
