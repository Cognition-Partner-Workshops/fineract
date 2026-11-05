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
package org.apache.fineract.portfolio.charge.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.apache.fineract.portfolio.charge.service.ChargeEnumerations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChargeEnumsTest {

    @ParameterizedTest
    @MethodSource("calculationTypes")
    void calculationTypeFromIntAndPredicates(Integer value, ChargeCalculationType expected, boolean percentage, boolean interest) {
        ChargeCalculationType actual = ChargeCalculationType.fromInt(value);

        assertEquals(expected, actual);
        assertEquals(percentage, actual.isPercentageBased());
        assertEquals(interest, actual.hasInterest());
        assertEquals(expected == ChargeCalculationType.FLAT, actual.isFlat());
        assertEquals(expected == ChargeCalculationType.PERCENT_OF_AMOUNT, actual.isPercentageOfAmount());
        assertEquals(expected == ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST, actual.isPercentageOfAmountAndInterest());
        assertEquals(expected == ChargeCalculationType.PERCENT_OF_INTEREST, actual.isPercentageOfInterest());
        assertEquals(expected == ChargeCalculationType.PERCENT_OF_DISBURSEMENT_AMOUNT, actual.isPercentageOfDisbursementAmount());
        assertEquals(expected == ChargeCalculationType.FLAT || expected == ChargeCalculationType.PERCENT_OF_AMOUNT,
                actual.isAllowedSavingsChargeCalculationType());
        assertEquals(expected == ChargeCalculationType.FLAT, actual.isAllowedClientChargeCalculationType());
    }

    private static Stream<Arguments> calculationTypes() {
        return Stream.of(Arguments.of(0, ChargeCalculationType.INVALID, false, false),
                Arguments.of(1, ChargeCalculationType.FLAT, false, false),
                Arguments.of(2, ChargeCalculationType.PERCENT_OF_AMOUNT, true, false),
                Arguments.of(3, ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST, true, true),
                Arguments.of(4, ChargeCalculationType.PERCENT_OF_INTEREST, true, true),
                Arguments.of(5, ChargeCalculationType.PERCENT_OF_DISBURSEMENT_AMOUNT, true, false),
                Arguments.of(99, ChargeCalculationType.INVALID, false, false));
    }

    @Test
    void calculationTypeNullIsNotHandledByCurrentImplementation() {
        assertThrows(NullPointerException.class, () -> ChargeCalculationType.fromInt(null));
    }

    @Test
    void calculationTypeApplicabilitySetsMatchChargeTypes() {
        assertArrayEquals(new Integer[] { 1, 2, 3, 4, 5 }, ChargeCalculationType.validValuesForLoan());
        assertArrayEquals(new Integer[] { 1, 2 }, ChargeCalculationType.validValuesForSavings());
        assertArrayEquals(new Integer[] { 1 }, ChargeCalculationType.validValuesForClients());
        assertArrayEquals(new Integer[] { 1, 2 }, ChargeCalculationType.validValuesForShares());
        assertArrayEquals(new Integer[] { 1 }, ChargeCalculationType.validValuesForShareAccountActivation());
        assertArrayEquals(new Integer[] { 1, 5 }, ChargeCalculationType.validValuesForTrancheDisbursement());
    }

    @ParameterizedTest
    @MethodSource("appliesToTypes")
    void appliesToFromIntAndPredicates(Integer value, ChargeAppliesTo expected) {
        ChargeAppliesTo actual = ChargeAppliesTo.fromInt(value);

        assertEquals(expected, actual);
        assertEquals(expected == ChargeAppliesTo.LOAN, actual.isLoanCharge());
        assertEquals(expected == ChargeAppliesTo.SAVINGS, actual.isSavingsCharge());
        assertEquals(expected == ChargeAppliesTo.CLIENT, actual.isClientCharge());
        assertEquals(expected == ChargeAppliesTo.SHARES, actual.isSharesCharge());
    }

    private static Stream<Arguments> appliesToTypes() {
        return Stream.of(Arguments.of(null, ChargeAppliesTo.INVALID), Arguments.of(0, ChargeAppliesTo.INVALID),
                Arguments.of(1, ChargeAppliesTo.LOAN), Arguments.of(2, ChargeAppliesTo.SAVINGS), Arguments.of(3, ChargeAppliesTo.CLIENT),
                Arguments.of(4, ChargeAppliesTo.SHARES), Arguments.of(99, ChargeAppliesTo.INVALID));
    }

    @Test
    void paymentModeMapsKnownAndUnknownValues() {
        assertEquals(ChargePaymentMode.REGULAR, ChargePaymentMode.fromInt(0));
        assertEquals(ChargePaymentMode.ACCOUNT_TRANSFER, ChargePaymentMode.fromInt(1));
        assertEquals(ChargePaymentMode.REGULAR, ChargePaymentMode.fromInt(99));
        assertFalse(ChargePaymentMode.REGULAR.isPaymentModeAccountTransfer());
        assertTrue(ChargePaymentMode.ACCOUNT_TRANSFER.isPaymentModeAccountTransfer());
        assertArrayEquals(new Integer[] { 0, 1 }, ChargePaymentMode.validValues());
        assertArrayEquals(new Object[] { 1, 2, 3, 4 }, ChargeAppliesTo.validValues());
    }

    @Test
    void paymentModeNullIsNotHandledByCurrentImplementation() {
        assertThrows(NullPointerException.class, () -> ChargePaymentMode.fromInt(null));
    }

    @Test
    void chargeEnumerationsMapInvalidAndKnownValues() {
        assertEquals(0L, ChargeEnumerations.chargeAppliesTo(99).getId());
        assertEquals("chargeAppliesTo.invalid", ChargeEnumerations.chargeAppliesTo(99).getCode());
        assertEquals(0L, ChargeEnumerations.chargeCalculationType(99).getId());
        assertEquals("chargeCalculationType.invalid", ChargeEnumerations.chargeCalculationType(99).getCode());
        assertEquals(0L, ChargeEnumerations.chargeTimeType(99).getId());
        assertEquals("chargeTimeType.invalid", ChargeEnumerations.chargeTimeType(99).getCode());
        assertEquals(0L, ChargeEnumerations.chargePaymentMode(99).getId());
        assertEquals("chargepaymentmode.regular", ChargeEnumerations.chargePaymentMode(99).getCode());
    }

    @Test
    void chargeEnumerationsMapEveryChargeTimeType() {
        for (ChargeTimeType type : ChargeTimeType.values()) {
            assertEquals(type.getValue().longValue(), ChargeEnumerations.chargeTimeType(type).getId());
            assertEquals(type.getCode(), ChargeEnumerations.chargeTimeType(type).getCode());
        }
    }
}
