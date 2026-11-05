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
package org.apache.fineract.organisation.teller.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.teller.exception.InvalidDateInputException;
import org.junit.jupiter.api.Test;

class TellerCommandFromApiJsonDeserializerTest {

    private final TellerCommandFromApiJsonDeserializer deserializer = new TellerCommandFromApiJsonDeserializer(new FromJsonHelper());

    private String teller(String start, String end) {
        return """
                {"officeId":1,"name":"Branch till","status":"300","startDate":"%s",
                 "endDate":"%s","dateFormat":"yyyy-MM-dd","locale":"en"}
                """.formatted(start, end);
    }

    @Test
    void acceptsTellerWithEqualStartAndEndDate() {
        assertDoesNotThrow(() -> deserializer.validateForCreateAndUpdateTeller(teller("2025-01-01", "2025-01-01")));
    }

    @Test
    void acceptsTellerWithOpenEndedDateRange() {
        assertDoesNotThrow(() -> deserializer.validateForCreateAndUpdateTeller("""
                {"officeId":1,"name":"Till","status":"300","startDate":"2025-01-01",
                 "dateFormat":"yyyy-MM-dd","locale":"en"}
                """));
    }

    @Test
    void rejectsReversedTellerDates() {
        assertThrows(InvalidDateInputException.class,
                () -> deserializer.validateForCreateAndUpdateTeller(teller("2025-01-02", "2025-01-01")));
    }

    @Test
    void rejectsMissingTellerRequiredFields() {
        assertThrows(PlatformApiDataValidationException.class, () -> deserializer.validateForCreateAndUpdateTeller("{}"));
    }

    @Test
    void rejectsInvalidOfficeAndExcessiveTextLengths() {
        String json = """
                {"officeId":0,"name":"%s","description":"%s","status":"%s","startDate":"2025-01-01",
                 "dateFormat":"yyyy-MM-dd","locale":"en"}
                """.formatted("n".repeat(51), "d".repeat(101), "s".repeat(51));
        assertThrows(PlatformApiDataValidationException.class, () -> deserializer.validateForCreateAndUpdateTeller(json));
    }

    @Test
    void rejectsUnsupportedFields() {
        assertThrows(UnsupportedParameterException.class, () -> deserializer.validateForCreateAndUpdateTeller("{\"unexpected\":true}"));
        assertThrows(UnsupportedParameterException.class, () -> deserializer.validateForAllocateCashier("{\"unexpected\":true}"));
        assertThrows(UnsupportedParameterException.class, () -> deserializer.validateForCashTxnForCashier("{\"unexpected\":true}"));
    }

    @Test
    void rejectsBlankInputForEveryCommand() {
        assertThrows(InvalidJsonException.class, () -> deserializer.validateForCreateAndUpdateTeller(null));
        assertThrows(InvalidJsonException.class, () -> deserializer.validateForCreateAndUpdateTeller(" "));
        assertThrows(InvalidJsonException.class, () -> deserializer.validateForAllocateCashier(""));
        assertThrows(InvalidJsonException.class, () -> deserializer.validateForCashTxnForCashier(" "));
    }

    @Test
    void acceptsFullDayAllocationWithoutShiftTimes() {
        assertDoesNotThrow(() -> deserializer.validateForAllocateCashier("""
                {"staffId":1,"startDate":"2025-01-01","endDate":"2025-01-02","isFullDay":true,
                 "dateFormat":"yyyy-MM-dd","locale":"en"}
                """));
    }

    @Test
    void acceptsPartialDayAllocationWithAllShiftComponents() {
        assertDoesNotThrow(() -> deserializer.validateForAllocateCashier("""
                {"staffId":1,"startDate":"2025-01-01","endDate":"2025-01-02","isFullDay":false,
                 "hourStartTime":"9","minStartTime":"0","hourEndTime":"17","minEndTime":"30",
                 "dateFormat":"yyyy-MM-dd","locale":"en"}
                """));
    }

    @Test
    void rejectsPartialDayAllocationWithoutShiftTimes() {
        assertThrows(PlatformApiDataValidationException.class, () -> deserializer.validateForAllocateCashier("""
                {"staffId":1,"startDate":"2025-01-01","endDate":"2025-01-02","isFullDay":false,
                 "dateFormat":"yyyy-MM-dd","locale":"en"}
                """));
    }

    @Test
    void rejectsAllocationWithoutRequiredStaffAndDates() {
        assertThrows(PlatformApiDataValidationException.class, () -> deserializer.validateForAllocateCashier("{\"isFullDay\":true}"));
    }

    @Test
    void missingFullDayFlagCurrentlyThrowsInsteadOfValidationError() {
        // Current behavior: nullable Boolean is unboxed before accumulated errors are thrown.
        assertThrows(NullPointerException.class, () -> deserializer.validateForAllocateCashier("{}"));
    }

    @Test
    void acceptsCashTransactionWithSixDecimalAmount() {
        assertDoesNotThrow(() -> deserializer.validateForCashTxnForCashier("""
                {"txnAmount":100.000001,"txnDate":"2025-01-01","currencyCode":"USD","txnNote":"Settlement",
                 "dateFormat":"yyyy-MM-dd","locale":"en"}
                """));
    }

    @Test
    void missingCashTransactionFieldsCurrentlyDoNotRaiseValidationErrors() {
        // Current behavior: cash-transaction validation never throws the accumulated validation errors.
        assertDoesNotThrow(() -> deserializer.validateForCashTxnForCashier("{}"));
    }
}
