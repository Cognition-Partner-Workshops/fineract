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
package org.apache.fineract.portfolio.floatingrates.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.floatingrates.domain.FloatingRate;
import org.apache.fineract.portfolio.floatingrates.domain.FloatingRateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FloatingRateDataValidatorTest {

    @Mock
    private FloatingRateRepository repository;

    private FloatingRateDataValidator validator;
    private MockedStatic<DateUtils> dateUtils;

    @BeforeEach
    void setUp() {
        validator = new FloatingRateDataValidator(new FromJsonHelper(), repository);
        dateUtils = Mockito.mockStatic(DateUtils.class, Mockito.CALLS_REAL_METHODS);
        dateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2025, 1, 1));
    }

    @AfterEach
    void tearDown() {
        dateUtils.close();
    }

    @Test
    void createAcceptsValidNameFlagsAndRatePeriods() {
        assertDoesNotThrow(() -> validator.validateForCreate("""
                {"name":"Prime","isBaseLendingRate":true,"isActive":true,"ratePeriods":[
                  {"fromDate":"2025-01-02","interestRate":0,"locale":"en","dateFormat":"yyyy-MM-dd"}
                ]}
                """));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"name\":\"\"}", "{\"name\":\"  \"}" })
    void createRejectsMissingOrBlankName(String json) {
        assertCode(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(json)), "name.cannot.be.blank");
    }

    @Test
    void createRejectsNameLongerThanTwoHundredCharacters() {
        String json = "{\"name\":\"" + "a".repeat(201) + "\"}";
        assertCode(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(json)),
                "name.exceeds.max.length");
    }

    @ParameterizedTest
    @ValueSource(strings = { "isBaseLendingRate" })
    void createRejectsNullFlags(String flag) {
        String json = "{\"name\":\"Prime\",\"" + flag + "\":null}";
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(json)), flag);
    }

    @Test
    void createRejectsNegativeInterestRate() {
        assertCode(
                assertThrows(PlatformApiDataValidationException.class,
                        () -> validator.validateForCreate(withPeriods(period("2025-01-02", "-0.01", "")))),
                "interestRate.not.zero.or.greater");
    }

    @Test
    void createRejectsDuplicatePeriodDates() {
        String periods = period("2025-01-02", "1", "") + "," + period("2025-01-02", "2", "");
        assertCode(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(withPeriods(periods))),
                "multiple.same.date");
    }

    @Test
    void createRejectsAnEmptyPeriodList() {
        assertParameter(assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate("{\"name\":\"Prime\",\"ratePeriods\":[]}")), "ratePeriods");
    }

    @Test
    void createRejectsSecondBaseLendingRate() {
        when(repository.retrieveBaseLendingRate()).thenReturn(mock(FloatingRate.class));

        assertCode(assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate("{\"name\":\"Prime\",\"isBaseLendingRate\":true}")), "baselendingrate.duplicate");
    }

    @Test
    void createRejectsDifferentialPeriodWithoutBaseLendingRate() {
        assertCode(
                assertThrows(PlatformApiDataValidationException.class,
                        () -> validator
                                .validateForCreate(withPeriods(period("2025-01-02", "1", ",\"isDifferentialToBaseLendingRate\":true")))),
                "no.baselending.rate.defined");
    }

    @Test
    void createRejectsDifferentialBaseLendingRatePeriod() {
        String json = "{\"name\":\"Prime\",\"isBaseLendingRate\":true,\"ratePeriods\":["
                + period("2025-01-02", "1", ",\"isDifferentialToBaseLendingRate\":true") + "]}";

        assertCode(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(json)),
                "cannot.be.true.for.baselendingrate");
    }

    @Test
    void createAcceptsDifferentialPeriodWhenBaseLendingRateExists() {
        when(repository.retrieveBaseLendingRate()).thenReturn(mock(FloatingRate.class));

        assertDoesNotThrow(() -> validator
                .validateForCreate(withPeriods(period("2025-01-02", "1", ",\"isDifferentialToBaseLendingRate\":true"))));
    }

    @Test
    void createCurrentlyAcceptsNullDifferentialFlag() {
        assertDoesNotThrow(
                () -> validator.validateForCreate(withPeriods(period("2025-01-02", "1", ",\"isDifferentialToBaseLendingRate\":null"))));
    }

    @Test
    void updateAcceptsAnEmptyChangesObject() {
        assertDoesNotThrow(() -> validator.validateForUpdate("{}", existingRate()));
    }

    @Test
    void updateRejectsPastPeriodDates() {
        assertCode(
                assertThrows(PlatformApiDataValidationException.class,
                        () -> validator.validateForUpdate(withPeriods(period("2024-12-31", "1", "")), existingRate())),
                "fromDate.is.less.than.date");
    }

    @ParameterizedTest
    @ValueSource(strings = { "isBaseLendingRate" })
    void updateRejectsNullFlags(String flag) {
        assertParameter(assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate("{\"" + flag + "\":null}", existingRate())), flag);
    }

    @Test
    void createAndUpdateCurrentlyAcceptNullActiveFlag() {
        assertDoesNotThrow(() -> validator.validateForCreate("{\"name\":\"Prime\",\"isActive\":null}"));
        assertDoesNotThrow(() -> validator.validateForUpdate("{\"isActive\":null}", existingRate()));
    }

    @Test
    void updateRejectsBlankOrLongName() {
        assertCode(assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate("{\"name\":\" \"}", existingRate())), "name.cannot.be.blank");
        assertCode(
                assertThrows(PlatformApiDataValidationException.class,
                        () -> validator.validateForUpdate("{\"name\":\"" + "a".repeat(201) + "\"}", existingRate())),
                "name.exceeds.max.length");
    }

    @Test
    void updateRejectsPromotingAnotherRateToBaseLendingRate() {
        FloatingRate baseRate = mock(FloatingRate.class);
        when(baseRate.getId()).thenReturn(1L);
        when(repository.retrieveBaseLendingRate()).thenReturn(baseRate);

        assertCode(assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate("{\"isBaseLendingRate\":true}", existingRate())), "baselendingrate.duplicate");
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"isBaseLendingRate\":false}", "{\"isActive\":false}" })
    void updateRejectsRemovingOrDeactivatingBaseRateWithLinkedRates(String json) {
        FloatingRate current = mock(FloatingRate.class);
        when(current.getId()).thenReturn(1L);
        if (json.contains("isActive")) {
            when(current.isBaseLendingRate()).thenReturn(true);
        } else {
            when(current.isActive()).thenReturn(true);
        }
        when(repository.retrieveBaseLendingRate()).thenReturn(current);
        when(repository.retrieveFloatingRatesLinkedToBLR()).thenReturn(List.of(existingRate()));

        assertCode(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpdate(json, current)),
                "cannot.be.marked.non.baselendingrate");
    }

    @Test
    void updateAllowsRemovingBaseRateWithoutLinkedRates() {
        FloatingRate current = mock(FloatingRate.class);
        when(current.getId()).thenReturn(1L);
        when(current.isActive()).thenReturn(true);
        when(repository.retrieveBaseLendingRate()).thenReturn(current);
        when(repository.retrieveFloatingRatesLinkedToBLR()).thenReturn(List.of());

        assertDoesNotThrow(() -> validator.validateForUpdate("{\"isBaseLendingRate\":false}", current));
    }

    private static FloatingRate existingRate() {
        return new FloatingRate("Existing", false, true, List.of());
    }

    private static String withPeriods(String periods) {
        return "{\"name\":\"Prime\",\"ratePeriods\":[" + periods + "]}";
    }

    private static String period(String date, String rate, String extra) {
        return "{\"fromDate\":\"" + date + "\",\"interestRate\":" + rate + ",\"locale\":\"en\",\"dateFormat\":\"yyyy-MM-dd\"" + extra + "}";
    }

    private static void assertCode(PlatformApiDataValidationException exception, String code) {
        assertTrue(exception.getErrors().stream().anyMatch(error -> error.getUserMessageGlobalisationCode().contains(code)),
                () -> exception.getErrors().toString());
    }

    private static void assertParameter(PlatformApiDataValidationException exception, String parameter) {
        assertTrue(exception.getErrors().stream().anyMatch(error -> error.getParameterName().contains(parameter)),
                () -> exception.getErrors().toString());
    }
}
