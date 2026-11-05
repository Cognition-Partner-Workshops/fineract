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
package org.apache.fineract.portfolio.savings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SavingsAccountAvailableBalanceTest {

    @ParameterizedTest
    @CsvSource(value = { "1000, null, null, true, 100, 900", "700, null, null, true, 100, 600", "100, null, null, true, 100, 0",
            "1000, 50, 25, true, 100, 825", "1000, 50, 25, false, 100, 925", "1000, null, null, false, 100, 1000",
            "1000, null, null, true, null, 1000", "0, null, null, false, null, 0", "0, null, null, true, 100, -100",
            "-50, null, null, false, null, -50" }, nullValues = "null")
    void availableBalanceRespectsOnlyEnforcedMinimumAndHolds(BigDecimal balance, BigDecimal onHoldFunds, BigDecimal onHoldAmount,
            boolean enforceMinimum, BigDecimal minimum, BigDecimal expected) {
        BigDecimal actual = SavingsAccountReadPlatformServiceImpl.calculateAvailableBalance(balance, onHoldFunds, onHoldAmount,
                enforceMinimum, minimum);
        assertEquals(0, expected.compareTo(actual));
    }

    @Test
    void missingAccountBalanceRemainsMissing() {
        assertNull(SavingsAccountReadPlatformServiceImpl.calculateAvailableBalance(null, BigDecimal.TEN, BigDecimal.TEN, true,
                BigDecimal.TEN));
    }
}
