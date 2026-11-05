/*
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
package org.apache.fineract.portfolio.charge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.charge.domain.ChargeAppliesTo;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChargeDropdownReadPlatformServiceImplTest {

    private final ChargeDropdownReadPlatformServiceImpl service = new ChargeDropdownReadPlatformServiceImpl();

    @ParameterizedTest
    @MethodSource("calculationOptions")
    void calculationOptionsMatchProductInOrder(Function<ChargeDropdownReadPlatformServiceImpl, List<EnumOptionData>> retrieve,
            List<ChargeCalculationType> types) {
        assertEquals(types.stream().map(ChargeEnumerations::chargeCalculationType).toList(), retrieve.apply(service));
    }

    private static Stream<Arguments> calculationOptions() {
        List<ChargeCalculationType> loan = List.of(ChargeCalculationType.FLAT, ChargeCalculationType.PERCENT_OF_AMOUNT,
                ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST, ChargeCalculationType.PERCENT_OF_INTEREST,
                ChargeCalculationType.PERCENT_OF_DISBURSEMENT_AMOUNT);
        List<ChargeCalculationType> savingsAndShares = List.of(ChargeCalculationType.FLAT, ChargeCalculationType.PERCENT_OF_AMOUNT);
        return Stream.of(calculations(ChargeDropdownReadPlatformServiceImpl::retrieveCalculationTypes, loan),
                calculations(ChargeDropdownReadPlatformServiceImpl::retrieveLoanCalculationTypes, loan),
                calculations(ChargeDropdownReadPlatformServiceImpl::retrieveSavingsCalculationTypes, savingsAndShares),
                calculations(ChargeDropdownReadPlatformServiceImpl::retrieveClientCalculationTypes, List.of(ChargeCalculationType.FLAT)),
                calculations(ChargeDropdownReadPlatformServiceImpl::retrieveSharesCalculationTypes, savingsAndShares));
    }

    private static Arguments calculations(Function<ChargeDropdownReadPlatformServiceImpl, List<EnumOptionData>> retrieve,
            List<ChargeCalculationType> types) {
        return Arguments.of(retrieve, types);
    }

    @ParameterizedTest
    @MethodSource("collectionTimeOptions")
    void collectionTimeOptionsMatchProductInOrder(Function<ChargeDropdownReadPlatformServiceImpl, List<EnumOptionData>> retrieve,
            List<ChargeTimeType> types) {
        List<EnumOptionData> actual = retrieve.apply(service);
        assertEquals(types.stream().map(ChargeEnumerations::chargeTimeType).toList(), actual);
        assertFalse(actual.stream().anyMatch(option -> option.getId().equals(ChargeTimeType.INVALID.getValue().longValue())
                || option.getId().equals(ChargeTimeType.SAVINGS_CLOSURE.getValue().longValue())));
    }

    private static Stream<Arguments> collectionTimeOptions() {
        return Stream.of(
                times(ChargeDropdownReadPlatformServiceImpl::retrieveLoanCollectionTimeTypes,
                        List.of(ChargeTimeType.DISBURSEMENT, ChargeTimeType.SPECIFIED_DUE_DATE, ChargeTimeType.INSTALMENT_FEE,
                                ChargeTimeType.OVERDUE_INSTALLMENT, ChargeTimeType.TRANCHE_DISBURSEMENT)),
                times(ChargeDropdownReadPlatformServiceImpl::retrieveSavingsCollectionTimeTypes,
                        List.of(ChargeTimeType.SPECIFIED_DUE_DATE, ChargeTimeType.SAVINGS_ACTIVATION, ChargeTimeType.WITHDRAWAL_FEE,
                                ChargeTimeType.ANNUAL_FEE, ChargeTimeType.MONTHLY_FEE, ChargeTimeType.WEEKLY_FEE,
                                ChargeTimeType.OVERDRAFT_FEE, ChargeTimeType.SAVINGS_NOACTIVITY_FEE)),
                times(ChargeDropdownReadPlatformServiceImpl::retrieveClientCollectionTimeTypes, List.of(ChargeTimeType.SPECIFIED_DUE_DATE)),
                times(ChargeDropdownReadPlatformServiceImpl::retrieveSharesCollectionTimeTypes,
                        List.of(ChargeTimeType.SHAREACCOUNT_ACTIVATION, ChargeTimeType.SHARE_PURCHASE, ChargeTimeType.SHARE_REDEEM)));
    }

    private static Arguments times(Function<ChargeDropdownReadPlatformServiceImpl, List<EnumOptionData>> retrieve,
            List<ChargeTimeType> types) {
        return Arguments.of(retrieve, types);
    }

    @Test
    void allCollectionTimesExcludeInvalidAndUnsupportedSavingsClosure() {
        List<EnumOptionData> expected = Arrays.stream(ChargeTimeType.values())
                .filter(type -> type != ChargeTimeType.INVALID && type != ChargeTimeType.SAVINGS_CLOSURE)
                .map(ChargeEnumerations::chargeTimeType).toList();
        assertEquals(expected, service.retrieveCollectionTimeTypes());
    }

    @Test
    void applicableProductsExcludeInvalid() {
        assertEquals(List.of(ChargeAppliesTo.LOAN, ChargeAppliesTo.SAVINGS, ChargeAppliesTo.CLIENT, ChargeAppliesTo.SHARES).stream()
                .map(ChargeEnumerations::chargeAppliesTo).toList(), service.retrieveApplicableToTypes());
    }

    @Test
    void paymentModesIncludeRegularAndAccountTransferInOrder() {
        assertEquals(List.of(ChargePaymentMode.REGULAR, ChargePaymentMode.ACCOUNT_TRANSFER).stream()
                .map(ChargeEnumerations::chargePaymentMode).toList(), service.retrivePaymentModes());
    }
}
