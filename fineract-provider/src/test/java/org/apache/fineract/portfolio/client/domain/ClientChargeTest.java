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
package org.apache.fineract.portfolio.client.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.monetary.domain.OrganisationCurrency;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientChargeTest {

    private static final OrganisationCurrency USD = new OrganisationCurrency("USD", "US Dollar", 2, null, "USD", "$");
    private ClientCharge clientCharge;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "test", "Test", "UTC", null));
        MoneyHelper.initializeTenantRoundingMode("test", 6);
        Charge charge = mock(Charge.class);
        when(charge.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(charge.getChargeCalculation()).thenReturn(ChargeCalculationType.FLAT.getValue());
        when(charge.getChargeTimeType()).thenReturn(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        when(charge.isPenalty()).thenReturn(false);

        JsonCommand command = mock(JsonCommand.class);
        when(command.bigDecimalValueOfParameterNamed(ClientApiConstants.amountParamName)).thenReturn(new BigDecimal("100.00"));
        when(command.localDateValueOfParameterNamed(ClientApiConstants.dueAsOfDateParamName)).thenReturn(LocalDate.of(2025, 1, 15));
        clientCharge = ClientCharge.createNew(mock(Client.class), charge, command);
        clientCharge.setCurrency(USD);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
        MoneyHelper.clearCache();
    }

    @Test
    void createsActiveChargeWithAmountAndDueDate() {
        assertThat(clientCharge.getAmount().getAmount()).isEqualByComparingTo("100.00");
        assertThat(clientCharge.getAmountOutstanding().getAmount()).isEqualByComparingTo("100.00");
        assertThat(clientCharge.getDueDate()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(clientCharge.getDueLocalDate()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(clientCharge.isOnSpecifiedDueDate()).isTrue();
        assertThat(clientCharge.isNotActive()).isFalse();
        assertThat(clientCharge.getInactivationDate()).isNull();
        assertThat(clientCharge.isActive()).isTrue();
        assertThat(clientCharge.isPaid()).isFalse();
        assertThat(clientCharge.isWaived()).isFalse();
    }

    @Test
    void payAndUndoPaymentMaintainPaidAndOutstandingAmounts() {
        assertThat(clientCharge.pay(Money.of(USD.toMonetaryCurrency(), new BigDecimal("40.00"))).getAmount()).isEqualByComparingTo("60.00");
        assertThat(clientCharge.getAmountPaid().getAmount()).isEqualByComparingTo("40.00");
        assertThat(clientCharge.isPaid()).isFalse();

        clientCharge.pay(Money.of(USD.toMonetaryCurrency(), new BigDecimal("60.00")));
        assertThat(clientCharge.isPaid()).isTrue();
        assertThat(clientCharge.getAmountOutstanding().getAmount()).isEqualByComparingTo("0.00");

        clientCharge.undoPayment(Money.of(USD.toMonetaryCurrency(), new BigDecimal("60.00")));
        assertThat(clientCharge.isPaid()).isFalse();
        assertThat(clientCharge.isActive()).isTrue();
        assertThat(clientCharge.getAmountOutstanding().getAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    void waiveAndUndoWaiverMaintainWaivedAndOutstandingAmounts() {
        assertThat(clientCharge.waive().getAmount()).isEqualByComparingTo("100.00");
        assertThat(clientCharge.isWaived()).isTrue();
        assertThat(clientCharge.getAmountWaived().getAmount()).isEqualByComparingTo("100.00");
        assertThat(clientCharge.getAmountOutstanding().getAmount()).isEqualByComparingTo("0.00");

        clientCharge.undoWaiver(Money.of(USD.toMonetaryCurrency(), new BigDecimal("25.00")));
        assertThat(clientCharge.isWaived()).isFalse();
        assertThat(clientCharge.isActive()).isTrue();
        assertThat(clientCharge.getAmountWaived().getAmount()).isEqualByComparingTo("75.00");
        assertThat(clientCharge.getAmountOutstanding().getAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void monetaryOperationsRoundToCurrencyDigits() {
        clientCharge.pay(Money.of(USD.toMonetaryCurrency(), new BigDecimal("10.126")));
        assertThat(clientCharge.getAmountPaid().getAmount()).isEqualByComparingTo("10.13");
        assertThat(clientCharge.getAmountOutstanding().getAmount()).isEqualByComparingTo("89.87");
    }

    @Test
    void waiveOnlyRemainingBalanceAfterPartialPayment() {
        clientCharge.pay(Money.of(USD.toMonetaryCurrency(), new BigDecimal("40")));
        assertThat(clientCharge.waive().getAmount()).isEqualByComparingTo("60");
        assertThat(clientCharge.getAmountPaid().getAmount()).isEqualByComparingTo("40");
        assertThat(clientCharge.getAmountWaived().getAmount()).isEqualByComparingTo("60");
        assertThat(clientCharge.getAmountOutstanding().getAmount()).isZero();
        assertThat(clientCharge.isWaived()).isTrue();
        assertThat(clientCharge.isPaid()).isFalse();
        assertThat(clientCharge.isPaidOrPartiallyPaid(USD.toMonetaryCurrency())).isTrue();
    }

    @Test
    void omittedCommandAmountUsesChargeDefinitionAndAllowsMissingDueDate() {
        Charge charge = mock(Charge.class);
        when(charge.getAmount()).thenReturn(new BigDecimal("25.50"));
        when(charge.getChargeCalculation()).thenReturn(ChargeCalculationType.FLAT.getValue());
        when(charge.getChargeTimeType()).thenReturn(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        ClientCharge created = ClientCharge.createNew(mock(Client.class), charge, mock(JsonCommand.class));
        created.setCurrency(USD);
        assertThat(created.getAmount().getAmount()).isEqualByComparingTo("25.50");
        assertThat(created.getAmountOutstanding().getAmount()).isEqualByComparingTo("25.50");
        assertThat(created.getAmountPaid().getAmount()).isZero();
        assertThat(created.getAmountWaived().getAmount()).isZero();
        assertThat(created.getAmountWrittenOff().getAmount()).isZero();
        assertThat(created.getDueDate()).isNull();
        assertThat(created.isPaidOrPartiallyPaid(USD.toMonetaryCurrency())).isFalse();
    }
}
