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
package org.apache.fineract.portfolio.loanproduct.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.common.domain.DayOfWeekType;
import org.apache.fineract.portfolio.common.domain.NthDayType;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTermVariationType;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanproduct.domain.AmortizationMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestCalculationPeriodMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.apache.fineract.portfolio.loanproduct.domain.InterestRecalculationCompoundingMethod;
import org.apache.fineract.portfolio.loanproduct.domain.LoanPreCloseInterestCalculationStrategy;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductParamType;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductValueConditionType;
import org.apache.fineract.portfolio.loanproduct.domain.LoanRescheduleStrategyMethod;
import org.apache.fineract.portfolio.loanproduct.domain.RecalculationFrequencyType;
import org.apache.fineract.portfolio.loanproduct.domain.RepaymentStartDateType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoanEnumerationsTest {

    private void assertOption(int id, String code, EnumOptionData option) {
        assertEquals((long) id, option.getId());
        assertEquals(code, option.getCode());
        assertFalse(option.getValue().isBlank());
    }

    @ParameterizedTest
    @EnumSource(AmortizationMethod.class)
    void amortizationTypePreservesIdAndCode(AmortizationMethod value) {
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.amortizationType(value));
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.amortizationType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(InterestMethod.class)
    void interestTypePreservesIdAndCode(InterestMethod value) {
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.interestType(value));
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.interestType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(InterestCalculationPeriodMethod.class)
    void interestCalculationPeriodTypePreservesIdAndCode(InterestCalculationPeriodMethod value) {
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.interestCalculationPeriodType(value));
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.interestCalculationPeriodType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(RepaymentStartDateType.class)
    void repaymentStartDateTypePreservesIdAndCode(RepaymentStartDateType value) {
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.repaymentStartDateType(value));
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.repaymentStartDateType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(LoanProductParamType.class)
    void loanCycleParamTypePreservesIdAndCode(LoanProductParamType value) {
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.loanCycleParamType(value));
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.loanCycleParamType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(LoanProductValueConditionType.class)
    void loanCycleValueConditionTypePreservesIdAndCode(LoanProductValueConditionType value) {
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.loanCycleValueConditionType(value));
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.loanCycleValueConditionType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(value = LoanTermVariationType.class, names = "INSERT_INSTALLMENT", mode = EnumSource.Mode.EXCLUDE)
    void loanVariationTypePreservesIdAndCode(LoanTermVariationType value) {
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.loanVariationType(value));
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.loanVariationType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(InterestRecalculationCompoundingMethod.class)
    void interestRecalculationCompoundingTypePreservesIdAndCode(InterestRecalculationCompoundingMethod value) {
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.interestRecalculationCompoundingType(value));
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.interestRecalculationCompoundingType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(LoanRescheduleStrategyMethod.class)
    void rescheduleStrategyTypePreservesIdAndCode(LoanRescheduleStrategyMethod value) {
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.rescheduleStrategyType(value));
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.rescheduleStrategyType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(RecalculationFrequencyType.class)
    void interestRecalculationFrequencyTypePreservesIdAndCode(RecalculationFrequencyType value) {
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.interestRecalculationFrequencyType(value));
        assertOption(value.getValue(), value.getCode(), LoanEnumerations.interestRecalculationFrequencyType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(LoanTransactionType.class)
    void transactionTypesPreserveIdCodeAndDisplayName(LoanTransactionType type) {
        var option = LoanEnumerations.transactionType(type);
        assertEquals(type.getValue().longValue(), option.getId());
        assertEquals(type.getCode(), option.getCode());
        assertFalse(option.getValue().isBlank());
        var byId = LoanEnumerations.transactionType(type.getValue());
        assertEquals(option.getId(), byId.getId());
        assertEquals(option.getCode(), byId.getCode());
        assertEquals(option.getValue(), byId.getValue());
    }

    @ParameterizedTest
    @EnumSource(LoanStatus.class)
    void statusesPreserveIdCodeAndDisplayName(LoanStatus status) {
        var option = LoanEnumerations.status(status);
        assertEquals(status.getValue().longValue(), option.getId());
        assertEquals(status.getCode(), option.getCode());
        assertFalse(option.getValue().isBlank());
        assertOption(status.getValue(), status.getCode(), LoanEnumerations.status(option));
        var byId = LoanEnumerations.status(status.getValue());
        assertEquals(option.getId(), byId.getId());
        assertEquals(option.getCode(), byId.getCode());
        assertEquals(option.getValue(), byId.getValue());
    }

    @ParameterizedTest
    @EnumSource(PeriodFrequencyType.class)
    void frequencyMappingsUseTheirOwnCodePrefixes(PeriodFrequencyType type) {
        boolean regular = type != PeriodFrequencyType.INVALID && type != PeriodFrequencyType.WHOLE_TERM;
        int id = regular ? type.getValue() : PeriodFrequencyType.INVALID.getValue();
        String code = regular ? type.getCode() : PeriodFrequencyType.INVALID.getCode();
        assertOption(id, regular ? "loanTermFrequency." + code : code, LoanEnumerations.loanTermFrequencyType(type.getValue()));
        assertOption(id, regular ? "termFrequency." + code : code, LoanEnumerations.termFrequencyType(type.getValue()));
        assertOption(id, regular ? "repaymentFrequency." + code : code, LoanEnumerations.repaymentFrequencyType(type.getValue()));
        boolean rate = type == PeriodFrequencyType.MONTHS || type == PeriodFrequencyType.YEARS || type == PeriodFrequencyType.WHOLE_TERM;
        assertOption(rate ? type.getValue() : PeriodFrequencyType.INVALID.getValue(),
                rate ? "interestRateFrequency." + type.getCode() : PeriodFrequencyType.INVALID.getCode(),
                LoanEnumerations.interestRateFrequencyType(type.getValue()));
    }

    @ParameterizedTest
    @EnumSource(DayOfWeekType.class)
    void weekdaysRetainTheirCodes(DayOfWeekType value) {
        assertOption(value.getValue(), "repaymentFrequency." + value.getCode(),
                LoanEnumerations.repaymentFrequencyDayOfWeekType(value.getValue()));
        assertOption(value.getValue(), "interestRecalculationCompounding." + value.getCode(),
                LoanEnumerations.interestRecalculationCompoundingDayOfWeekType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(NthDayType.class)
    void nthDaysRetainTheirCodes(NthDayType value) {
        int id = value == NthDayType.ONDAY ? 0 : value.getValue();
        assertOption(id, "repaymentFrequency." + value.getCode(), LoanEnumerations.repaymentFrequencyNthDayType(value.getValue()));
        assertOption(id, "interestRecalculationCompounding." + value.getCode(),
                LoanEnumerations.interestRecalculationCompoundingNthDayType(value.getValue()));
    }

    @ParameterizedTest
    @EnumSource(LoanPreCloseInterestCalculationStrategy.class)
    void preClosureStrategyNoneHasNoOption(LoanPreCloseInterestCalculationStrategy value) {
        if (value == LoanPreCloseInterestCalculationStrategy.NONE) {
            assertNull(LoanEnumerations.preCloseInterestCalculationStrategy(value.getValue()));
        } else {
            assertOption(value.getValue(), value.getCode(), LoanEnumerations.preCloseInterestCalculationStrategy(value.getValue()));
        }
    }

    @Test
    void nullCalendarOptionsAndUnknownEnumerationAreAbsent() {
        assertNull(LoanEnumerations.repaymentFrequencyNthDayType((Integer) null));
        assertNull(LoanEnumerations.repaymentFrequencyDayOfWeekType((Integer) null));
        assertNull(LoanEnumerations.interestRecalculationCompoundingNthDayType((Integer) null));
        assertNull(LoanEnumerations.interestRecalculationCompoundingDayOfWeekType((Integer) null));
        assertNull(LoanEnumerations.loanEnumeration("unknown", 1));
        assertOption(InterestMethod.INVALID.getValue(), InterestMethod.INVALID.getCode(), LoanEnumerations.interestType(-999));
    }

    @Test
    void insertInstallmentHasDistinctIdAndDisplayLabel() {
        var option = LoanEnumerations.loanVariationType(LoanTermVariationType.INSERT_INSTALLMENT.getValue());
        assertEquals(5L, option.getId());
        assertEquals("insertInstallment", option.getValue());
    }
}
