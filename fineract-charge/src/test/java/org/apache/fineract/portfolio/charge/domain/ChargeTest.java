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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.MonthDay;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.charge.exception.ChargeDueAtDisbursementCannotBePenaltyException;
import org.apache.fineract.portfolio.charge.exception.ChargeMustBePenaltyException;
import org.apache.fineract.portfolio.charge.exception.ChargeParameterUpdateNotSupportedException;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.tax.domain.TaxGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeTest {

    @BeforeEach
    void setUpTenant() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "test", "Test tenant", "Pacific/Auckland", null));
    }

    @AfterEach
    void clearTenant() {
        ThreadLocalContextUtil.clearTenant();
    }

    @ParameterizedTest
    @CsvSource({ "1,2,1", "1,2,2", "1,2,3", "1,2,4", "1,12,5", "2,5,1", "2,5,2", "2,16,2", "3,2,1", "4,13,1", "4,14,2", "4,15,2" })
    void fromJsonPreservesProductAndCalculationDetails(int appliesTo, int time, int calculation) {
        Charge charge = create(appliesTo, time, calculation, "\"minCap\":1,\"maxCap\":20");

        assertEquals("Charge", charge.getName());
        assertEquals(new BigDecimal("10"), charge.getAmount());
        assertEquals("USD", charge.getCurrencyCode());
        assertEquals(time, charge.getChargeTimeType());
        assertEquals(calculation, charge.getChargeCalculation());
        assertEquals(0, charge.getChargePaymentMode());
        assertTrue(charge.isActive());
        assertFalse(charge.isDeleted());
        assertFalse(charge.isPenalty());
        assertEquals(appliesTo == 1, charge.isLoanCharge());
        assertEquals(appliesTo == 2, charge.isSavingsCharge());
        assertEquals(appliesTo == 3, charge.isClientCharge());
        assertEquals(appliesTo, charge.toData().getChargeAppliesTo().getId().intValue());
        assertEquals(time == 1 || time == 12, charge.isDisbursementCharge());
        assertEquals(calculation == 2, charge.isPercentageOfApprovedAmount());
        assertEquals(calculation == 5, charge.isPercentageOfDisbursementAmount());
        if (calculation == 2 || calculation == 5) {
            assertEquals(BigDecimal.ONE, charge.getMinCap());
            assertEquals(new BigDecimal("20"), charge.getMaxCap());
        } else {
            assertNull(charge.getMinCap());
            assertNull(charge.getMaxCap());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 6, 7 })
    void fromJsonPreservesFeeMonthDayAndInterval(int time) {
        Charge charge = create(2, time, 1,
                "\"monthDayFormat\":\"MM-dd\",\"feeOnMonthDay\":\"02-29\",\"feeInterval\":3," + "\"feeFrequency\":2");

        assertEquals(MonthDay.of(2, 29), charge.getFeeOnMonthDay());
        assertEquals(3, charge.getFeeInterval());
        assertEquals(3, charge.feeInterval());
        assertEquals(2, charge.feeFrequency());
        assertEquals(time == 7, charge.isMonthlyFee());
        assertEquals(time == 6, charge.isAnnualFee());
        ChargeData data = charge.toData();
        assertEquals(MonthDay.of(2, 29), data.getFeeOnMonthDay());
        assertEquals(3, data.getFeeInterval());
        assertEquals(2L, data.getFeeFrequency().getId());
    }

    @Test
    void fromJsonAndToDataPreserveAssociationsAndSavingsSettings() {
        GLAccount account = mock(GLAccount.class);
        when(account.getId()).thenReturn(7L);
        when(account.getName()).thenReturn("Income");
        when(account.getGlCode()).thenReturn("100");
        TaxGroup taxGroup = mock(TaxGroup.class);
        when(taxGroup.getId()).thenReturn(8L);
        when(taxGroup.getName()).thenReturn("Tax");
        PaymentType paymentType = mock(PaymentType.class);
        when(paymentType.getId()).thenReturn(9L);
        when(paymentType.getName()).thenReturn("Cash");
        Charge charge = Charge.fromJson(command(json(2, 5, 2,
                "\"minCap\":1,\"maxCap\":20,\"feeInterval\":1,\"feeFrequency\":0,"
                        + "\"enableFreeWithdrawalCharge\":true,\"freeWithdrawalFrequency\":2,\"restartCountFrequency\":3,"
                        + "\"countFrequencyType\":2,\"enablePaymentType\":true")),
                account, taxGroup, paymentType);
        charge.setId(42L);

        assertSame(account, charge.getAccount());
        assertSame(taxGroup, charge.getTaxGroup());
        assertSame(paymentType, charge.getPaymentType());
        assertEquals(7L, charge.getIncomeAccountId());
        assertTrue(charge.isEnableFreeWithdrawal());
        assertTrue(charge.isEnablePaymentType());
        assertEquals(2, charge.getFrequencyFreeWithdrawalCharge());
        assertEquals(3, charge.getRestartFrequency());
        assertEquals(2, charge.getRestartFrequencyEnum());
        ChargeData data = charge.toData();
        assertEquals(42L, data.getId());
        assertEquals("Charge", data.getName());
        assertEquals(new BigDecimal("10"), data.getAmount());
        assertEquals("USD", data.getCurrency().getCode());
        assertEquals(2L, data.getChargeAppliesTo().getId());
        assertEquals(5L, data.getChargeTimeType().getId());
        assertEquals(2L, data.getChargeCalculationType().getId());
        assertEquals(0L, data.getChargePaymentMode().getId());
        assertEquals(7L, data.getIncomeOrLiabilityAccount().getId());
        assertEquals("Income", data.getIncomeOrLiabilityAccount().getName());
        assertEquals("100", data.getIncomeOrLiabilityAccount().getGlCode());
        assertEquals(8L, data.getTaxGroup().getId());
        assertEquals("Tax", data.getTaxGroup().getName());
        assertEquals(9L, data.getPaymentTypeOptions().getId());
        assertEquals("Cash", data.getPaymentTypeOptions().getName());
        assertTrue(data.isActive());
        assertFalse(data.isPenalty());
        assertTrue(data.isFreeWithdrawal());
        assertTrue(data.isIsPaymentType());
        assertEquals(2, data.getFreeWithdrawalChargeFrequency());
        assertEquals(3, data.getRestartFrequency());
        assertEquals(2, data.getRestartFrequencyEnum());
        assertEquals(BigDecimal.ONE, data.getMinCap());
        assertEquals(new BigDecimal("20"), data.getMaxCap());
        assertEquals(0L, data.getFeeFrequency().getId());
    }

    @Test
    void absentOptionalSettingsRemainAbsentAndMissingPaymentTypeIsNotEnabled() {
        Charge charge = create(2, 5, 1, "\"enablePaymentType\":true");
        assertFalse(charge.isEnablePaymentType());
        assertFalse(charge.isEnableFreeWithdrawal());
        assertNull(charge.getFrequencyFreeWithdrawalCharge());
        assertNull(charge.getRestartFrequency());
        assertNull(charge.getRestartFrequencyEnum());
        assertNull(charge.getPaymentType());
        assertNull(charge.getFeeOnMonthDay());
        ChargeData data = charge.toData();
        assertNull(data.getPaymentTypeOptions());
        assertNull(data.getTaxGroup());
        assertNull(data.getIncomeOrLiabilityAccount());
        assertNull(data.getFeeFrequency());
    }

    @ParameterizedTest
    @CsvSource({ "2,1,1,chargeTimeType,not.allowed.charge.time.for.savings",
            "2,5,3,chargeCalculationType,not.allowed.charge.calculation.type.for.savings",
            "2,3,2,chargeCalculationType,savings.charge.calculation.type.percentage.allowed.only.for.withdrawal.or.NoActivity",
            "1,5,1,chargeTimeType,not.allowed.charge.time.for.loan" })
    void constructorRejectsIncompatibleDomainValues(int appliesTo, int time, int calculation, String parameter, String suffix) {
        assertError(assertThrows(PlatformApiDataValidationException.class, () -> create(appliesTo, time, calculation, "")), parameter,
                "validation.msg.charges." + suffix);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 12 })
    void constructorRejectsPenaltyAtDisbursement(int time) {
        String json = json(1, time, 1, "").replace("\"penalty\":false", "\"penalty\":true");
        ChargeDueAtDisbursementCannotBePenaltyException exception = assertThrows(ChargeDueAtDisbursementCannotBePenaltyException.class,
                () -> Charge.fromJson(command(json), null, null, null));
        assertEquals("error.msg.charge.due.at.disbursement.cannot.be.penalty", exception.getGlobalisationMessageCode());
    }

    @Test
    void overdueChargeMustBePenalty() {
        ChargeMustBePenaltyException exception = assertThrows(ChargeMustBePenaltyException.class, () -> create(1, 9, 1, ""));
        assertEquals("error.msg.charge.must.be.penalty", exception.getGlobalisationMessageCode());
        Charge charge = Charge.fromJson(command(json(1, 9, 1, "").replace("\"penalty\":false", "\"penalty\":true")), null, null, null);
        assertTrue(charge.isOverdueInstallment());
        assertTrue(charge.toData().isPenalty());
    }

    @ParameterizedTest
    @MethodSource("mutableFields")
    void updateTracksAndMutatesEachField(String field, String valueJson, Object expected, boolean includesLocale,
            Function<Charge, Object> readState) {
        Charge charge = create(1, 2, 2, "\"minCap\":1,\"maxCap\":20,\"feeInterval\":1,\"feeFrequency\":2");
        Map<String, Object> expectedChanges = new LinkedHashMap<>();
        expectedChanges.put(field, expected);
        if (includesLocale) {
            expectedChanges.put("locale", "en");
        }
        JsonCommand update = command("{\"locale\":\"en\",\"" + field + "\":" + valueJson + "}");

        assertEquals(expectedChanges, charge.update(update));
        assertEquals(expected, readState.apply(charge));
        assertEquals(Map.of(), charge.update(update));
    }

    private static Stream<Arguments> mutableFields() {
        return Stream.of(field("name", "\"Renamed\"", "Renamed", false, Charge::getName),
                field("currencyCode", "\"EUR\"", "EUR", false, Charge::getCurrencyCode),
                field("amount", "12.5", new BigDecimal("12.5"), true, Charge::getAmount),
                field("chargeTimeType", "1", 1, true, Charge::getChargeTimeType),
                field("chargeCalculationType", "1", 1, true, Charge::getChargeCalculation),
                field("chargePaymentMode", "1", 1, true, Charge::getChargePaymentMode),
                field("penalty", "true", true, false, Charge::isPenalty), field("active", "false", false, false, Charge::isActive),
                field("minCap", "2", new BigDecimal("2"), true, Charge::getMinCap),
                field("maxCap", "30", new BigDecimal("30"), true, Charge::getMaxCap),
                field("feeInterval", "3", 3, true, Charge::getFeeInterval), field("feeFrequency", "3", 3, true, Charge::feeFrequency),
                field("freeWithdrawalFrequency", "2", 2, false, Charge::getFrequencyFreeWithdrawalCharge),
                field("restartCountFrequency", "3", 3, false, Charge::getRestartFrequency),
                field("countFrequencyType", "2", 2, false, Charge::getRestartFrequencyEnum),
                field("enableFreeWithdrawalCharge", "true", true, false, Charge::isEnableFreeWithdrawal),
                field("enablePaymentType", "true", true, false, Charge::isEnablePaymentType));
    }

    private static Arguments field(String field, String valueJson, Object expected, boolean includesLocale,
            Function<Charge, Object> readState) {
        return Arguments.of(field, valueJson, expected, includesLocale, readState);
    }

    @Test
    void emptyAndUnchangedUpdatesHaveNoChanges() {
        Charge charge = create(1, 2, 1, "");
        Charge same = create(1, 2, 1, "");
        assertEquals(Map.of(), charge.update(command("{}")));
        assertEquals(Map.of(), charge.update(command(json(1, 2, 1, ""))));
        assertEquals(same, charge);
    }

    @Test
    void updateFeeDateTracksOriginalTextAndOnlyChangedDates() {
        Charge charge = create(2, 7, 1, "\"monthDayFormat\":\"MM-dd\",\"feeOnMonthDay\":\"01-15\",\"feeInterval\":1");
        JsonCommand update = command("{\"locale\":\"en\",\"monthDayFormat\":\"MM-dd\",\"feeOnMonthDay\":\"03-20\"}");
        assertEquals(Map.of("feeOnMonthDay", "03-20", "locale", "en"), charge.update(update));
        assertEquals(MonthDay.of(3, 20), charge.getFeeOnMonthDay());
        assertEquals(Map.of(), charge.update(update));
    }

    @Test
    void updateRejectsApplicabilityChangeWithoutChangingApplicability() {
        Charge charge = create(1, 2, 1, "");
        ChargeParameterUpdateNotSupportedException exception = assertThrows(ChargeParameterUpdateNotSupportedException.class,
                () -> charge.update(command("{\"locale\":\"en\",\"chargeAppliesTo\":2}")));
        assertEquals("error.msg.charge.update.of.charge.applies.to.is.not.supported", exception.getGlobalisationMessageCode());
        assertTrue(charge.isLoanCharge());
    }

    @ParameterizedTest
    @CsvSource({ "1,2,1,chargeTimeType,5,chargeTimeType,not.allowed.charge.time.for.loan",
            "2,5,1,chargeTimeType,1,chargeTimeType,not.allowed.charge.time.for.savings",
            "2,5,1,chargeCalculationType,3,chargeCalculationType,not.allowed.charge.calculation.type.for.savings",
            "2,3,1,chargeCalculationType,2,chargeCalculationType,charge.calculation.type.percentage.allowed.only.for.withdrawal.or.noactivity",
            "3,2,1,chargeCalculationType,2,chargeCalculationType,not.allowed.charge.calculation.type.for.client",
            "3,2,1,chargeTimeType,5,chargeTimeType,not.allowed.charge.time.for.client" })
    void updateRejectsIncompatibleDomainValues(int appliesTo, int time, int calculation, String field, int value, String parameter,
            String suffix) {
        Charge charge = create(appliesTo, time, calculation, "");
        assertError(
                assertThrows(PlatformApiDataValidationException.class,
                        () -> charge.update(command("{\"locale\":\"en\",\"" + field + "\":" + value + "}"))),
                parameter, "validation.msg.charges." + suffix);
    }

    @Test
    void updateRequiresMonthlyDateAndIntervalAndFrequencyInterval() {
        Charge charge = create(2, 5, 1, "");
        PlatformApiDataValidationException monthly = assertThrows(PlatformApiDataValidationException.class,
                () -> charge.update(command("{\"locale\":\"en\",\"chargeTimeType\":7}")));
        assertError(monthly, "feeOnMonthDay", "validation.msg.charges.feeOnMonthDay.cannot.be.blank");
        assertError(monthly, "feeInterval", "validation.msg.charges.feeInterval.cannot.be.blank");
        Charge other = create(1, 2, 1, "");
        assertError(
                assertThrows(PlatformApiDataValidationException.class,
                        () -> other.update(command("{\"locale\":\"en\",\"feeFrequency\":2}"))),
                "feeInterval", "validation.msg.charges.feeInterval.cannot.be.blank");
    }

    @Test
    void updateRejectsDisbursementPenaltyAndNonPenaltyOverdue() {
        Charge disbursement = create(1, 1, 1, "");
        assertEquals("error.msg.charge.due.at.disbursement.cannot.be.penalty",
                assertThrows(ChargeDueAtDisbursementCannotBePenaltyException.class,
                        () -> disbursement.update(command("{\"penalty\":true}"))).getGlobalisationMessageCode());
        Charge overdue = Charge.fromJson(command(json(1, 9, 1, "").replace("\"penalty\":false", "\"penalty\":true")), null, null, null);
        assertEquals("error.msg.charge.must.be.penalty",
                assertThrows(ChargeMustBePenaltyException.class, () -> overdue.update(command("{\"penalty\":false}")))
                        .getGlobalisationMessageCode());
    }

    @Test
    void associationUpdatesTrackIdsButRequireSettersToMutateReferences() {
        Charge charge = create(2, 5, 1, "");
        assertEquals(Map.of("incomeAccountId", 7L, "taxGroupId", 8L, "paymentTypeId", 9L),
                charge.update(command("{\"locale\":\"en\",\"incomeAccountId\":7,\"taxGroupId\":8,\"paymentTypeId\":9}")));
        assertNull(charge.getAccount());
        assertNull(charge.getTaxGroup());
        assertNull(charge.getPaymentType());
        GLAccount account = mock(GLAccount.class);
        when(account.getId()).thenReturn(7L);
        TaxGroup tax = mock(TaxGroup.class);
        when(tax.getId()).thenReturn(8L);
        PaymentType payment = mock(PaymentType.class);
        when(payment.getId()).thenReturn(9L);
        charge.setAccount(account);
        charge.setTaxGroup(tax);
        charge.setPaymentType(payment);
        assertSame(account, charge.getAccount());
        assertSame(tax, charge.getTaxGroup());
        assertSame(payment, charge.getPaymentType());
        assertEquals(Map.of(), charge.update(command("{\"locale\":\"en\",\"incomeAccountId\":7,\"taxGroupId\":8,\"paymentTypeId\":9}")));
        assertError(
                assertThrows(PlatformApiDataValidationException.class,
                        () -> charge.update(command("{\"locale\":\"en\",\"taxGroupId\":10}"))),
                "taxGroupId", "validation.msg.charges.taxGroupId.modification.not.supported");
        assertSame(tax, charge.getTaxGroup());
    }

    @Test
    void deleteManglesNameButCurrentlyLeavesActiveFlagUnchanged() {
        Charge charge = create(1, 2, 1, "");
        charge.setId(42L);
        charge.delete();
        assertTrue(charge.isDeleted());
        assertEquals("42_Charge", charge.getName());
        assertTrue(charge.isActive());
        assertTrue(charge.toData().isActive());
    }

    @Test
    void equalityAndHashCodeUseDefinitionFieldsRatherThanId() {
        Charge first = create(1, 2, 2, "\"minCap\":1,\"maxCap\":20");
        Charge second = create(1, 2, 2, "\"minCap\":1,\"maxCap\":20");
        first.setId(1L);
        second.setId(2L);
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(null, first);
        assertNotEquals("Charge", first);
        second.update(command("{\"name\":\"Different\"}"));
        assertNotEquals(first, second);
    }

    @Test
    void equalityCurrentlyIgnoresFreeWithdrawalAndPaymentSettings() {
        Charge first = create(2, 5, 1, "");
        Charge second = create(2, 5, 1, "\"enableFreeWithdrawalCharge\":true,\"freeWithdrawalFrequency\":2,"
                + "\"restartCountFrequency\":3,\"countFrequencyType\":2");
        assertFalse(first.isEnableFreeWithdrawal());
        assertTrue(second.isEnableFreeWithdrawal());
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        second.update(command("{\"enablePaymentType\":true}"));
        assertTrue(second.isEnablePaymentType());
        assertEquals(first, second);
    }

    @Test
    void changingToMonthlyFeeCurrentlyThrowsWhenStoredDateIsNull() {
        Charge charge = create(2, 5, 1, "");
        assertThrows(NullPointerException.class, () -> charge.update(command("{\"locale\":\"en\",\"chargeTimeType\":7,"
                + "\"monthDayFormat\":\"MM-dd\",\"feeOnMonthDay\":\"03-20\",\"feeInterval\":1}")));
    }

    @Test
    void tranchePenaltyUpdateCurrentlyDiffersFromConstructorValidation() {
        Charge charge = create(1, 12, 5, "\"minCap\":1,\"maxCap\":20");
        assertEquals(Map.of("penalty", true), charge.update(command("{\"penalty\":true}")));
        assertTrue(charge.isPenalty());
    }

    @Test
    void trancheCapsCurrentlyCannotBeUpdatedDespiteBeingSetOnCreation() {
        Charge charge = create(1, 12, 5, "\"minCap\":1,\"maxCap\":20");
        assertEquals(Map.of(), charge.update(command("{\"locale\":\"en\",\"minCap\":2,\"maxCap\":30}")));
        assertEquals(BigDecimal.ONE, charge.getMinCap());
        assertEquals(new BigDecimal("20"), charge.getMaxCap());
    }

    @Test
    void omittedPaymentModeCurrentlyAllowsCreationButBreaksToData() {
        Charge charge = Charge.fromJson(command(json(2, 5, 1, "").replace(",\"chargePaymentMode\":0", "")), null, null, null);
        assertNull(charge.getChargePaymentMode());
        assertThrows(NullPointerException.class, charge::toData);
    }

    private static Charge create(int appliesTo, int time, int calculation, String extra) {
        return Charge.fromJson(command(json(appliesTo, time, calculation, extra)), null, null, null);
    }

    private static String json(int appliesTo, int time, int calculation, String extra) {
        return "{\"locale\":\"en\",\"name\":\"Charge\",\"amount\":10,\"currencyCode\":\"USD\",\"chargeAppliesTo\":" + appliesTo
                + ",\"chargeTimeType\":" + time + ",\"chargeCalculationType\":" + calculation
                + ",\"chargePaymentMode\":0,\"penalty\":false,\"active\":true" + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private static JsonCommand command(String json) {
        FromJsonHelper helper = new FromJsonHelper();
        return new JsonCommand(null, helper.parse(json), helper);
    }

    private static void assertError(PlatformApiDataValidationException exception, String parameter, String code) {
        assertTrue(
                exception.getErrors().stream().anyMatch(
                        error -> parameter.equals(error.getParameterName()) && code.equals(error.getUserMessageGlobalisationCode())),
                () -> "Missing " + parameter + ": " + code + " in " + exception);
    }
}
