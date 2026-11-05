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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.charge.domain.ChargeAppliesTo;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChargeEnumerationsTest {

    @ParameterizedTest
    @CsvSource({ "0,Invalid", "1,Disbursement", "2,Specified due date", "3,Savings Activation", "4,Savings Closure", "5,Withdrawal Fee",
            "6,Annual Fee", "7,Monthly Fee", "8,Installment Fee", "9,Overdue Fees", "10,Overdraft Fee", "11,Weekly Fee",
            "12,Tranche Disbursement", "13,Share Account Activate", "14,Share Purchase", "15,Share Redeem", "16,Saving No Activity Fee" })
    void mapsChargeTimeType(int id, String label) {
        ChargeTimeType type = ChargeTimeType.fromInt(id);
        assertOption(ChargeEnumerations.chargeTimeType(id), id, type.getCode(), label);
        assertOption(ChargeEnumerations.chargeTimeType(type), id, type.getCode(), label);
    }

    @ParameterizedTest
    @CsvSource({ "0,Invalid", "1,Loan", "2,Savings", "3,Client", "4,Shares" })
    void mapsChargeAppliesTo(int id, String label) {
        ChargeAppliesTo type = ChargeAppliesTo.fromInt(id);
        assertOption(ChargeEnumerations.chargeAppliesTo(id), id, type.getCode(), label);
        assertOption(ChargeEnumerations.chargeAppliesTo(type), id, type.getCode(), label);
    }

    @ParameterizedTest
    @CsvSource({ "0,Invalid", "1,Flat", "2,% Amount", "3,% Loan Amount + Interest", "4,% Interest", "5,% Disbursement Amount" })
    void mapsChargeCalculationType(int id, String label) {
        ChargeCalculationType type = ChargeCalculationType.fromInt(id);
        assertOption(ChargeEnumerations.chargeCalculationType(id), id, type.getCode(), label);
        assertOption(ChargeEnumerations.chargeCalculationType(type), id, type.getCode(), label);
    }

    @ParameterizedTest
    @CsvSource({ "0,Regular", "1,Account transfer" })
    void mapsChargePaymentMode(int id, String label) {
        ChargePaymentMode type = ChargePaymentMode.fromInt(id);
        assertOption(ChargeEnumerations.chargePaymentMode(id), id, type.getCode(), label);
        assertOption(ChargeEnumerations.chargePaymentMode(type), id, type.getCode(), label);
    }

    @ParameterizedTest
    @CsvSource({ "0,Daily", "1,Weekly", "2,Monthly", "3,Yearly", "4,Whole term" })
    void mapsFeeFrequencyType(int id, String label) {
        PeriodFrequencyType type = PeriodFrequencyType.fromInt(id);
        assertOption(ChargeEnumerations.feeFrequencyType(id), id, type.getCode(), label);
        assertOption(ChargeEnumerations.feeFrequencyType(type), id, type.getCode(), label);
    }

    @Test
    void invalidIdsUseFallbackOptionsExceptForFeeFrequency() {
        assertOption(ChargeEnumerations.chargeTimeType(-1), 0, "chargeTimeType.invalid", "Invalid");
        assertOption(ChargeEnumerations.chargeAppliesTo(-1), 0, "chargeAppliesTo.invalid", "Invalid");
        assertOption(ChargeEnumerations.chargeCalculationType(-1), 0, "chargeCalculationType.invalid", "Invalid");
        assertOption(ChargeEnumerations.chargePaymentMode(-1), 0, "chargepaymentmode.regular", "Regular");
        assertThrows(UnsupportedOperationException.class, () -> ChargeEnumerations.feeFrequencyType(-1));
        assertThrows(UnsupportedOperationException.class, () -> ChargeEnumerations.feeFrequencyType(PeriodFrequencyType.INVALID));
    }

    @Test
    void nullEnumArgumentsCurrentlyThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> ChargeEnumerations.chargeTimeType((ChargeTimeType) null));
        assertThrows(NullPointerException.class, () -> ChargeEnumerations.chargeAppliesTo((ChargeAppliesTo) null));
        assertThrows(NullPointerException.class, () -> ChargeEnumerations.chargeCalculationType((ChargeCalculationType) null));
        assertThrows(NullPointerException.class, () -> ChargeEnumerations.chargePaymentMode((ChargePaymentMode) null));
        assertThrows(NullPointerException.class, () -> ChargeEnumerations.feeFrequencyType((PeriodFrequencyType) null));
    }

    private static void assertOption(EnumOptionData option, int id, String code, String label) {
        assertEquals((long) id, option.getId());
        assertEquals(code, option.getCode());
        assertEquals(label, option.getValue());
    }
}
