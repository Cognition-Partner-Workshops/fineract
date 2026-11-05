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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.monetary.domain.OrganisationCurrency;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientTransactionTest {

    private static final OrganisationCurrency USD = new OrganisationCurrency("USD", "US Dollar", 2, null, "USD", "$");

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "test", "Test", "UTC", null));
        MoneyHelper.initializeTenantRoundingMode("test", 6);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2025, 1, 20))));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
        MoneyHelper.clearCache();
    }

    @Test
    void payChargeCreatesPaymentTransactionWithAmountAndCurrency() {
        LocalDate date = LocalDate.of(2025, 1, 15);
        ClientTransaction transaction = ClientTransaction.payCharge(mock(Client.class), mock(Office.class), mock(PaymentDetail.class), date,
                Money.of(USD.toMonetaryCurrency(), new BigDecimal("12.345")), "USD", ExternalId.empty());
        transaction.setCurrency(USD);

        assertThat(transaction.isPayChargeTransaction()).isTrue();
        assertThat(transaction.isWaiveChargeTransaction()).isFalse();
        assertThat(transaction.getTransactionDate()).isEqualTo(date);
        assertThat(transaction.getSubmittedOnDate()).isEqualTo(LocalDate.of(2025, 1, 20));
        assertThat(transaction.getCurrencyCode()).isEqualTo("USD");
        assertThat(transaction.getAmount().getAmount()).isEqualByComparingTo("12.34");
        assertThat(transaction.isReversed()).isFalse();
    }

    @Test
    void reverseMarksTransactionAsReversed() {
        ClientTransaction transaction = ClientTransaction.waiver(mock(Client.class), mock(Office.class), LocalDate.of(2025, 1, 15),
                Money.of(USD.toMonetaryCurrency(), new BigDecimal("4.00")), "USD");

        assertThat(transaction.isWaiveChargeTransaction()).isTrue();
        assertThat(transaction.isReversed()).isFalse();
        transaction.reverse();
        assertThat(transaction.isReversed()).isTrue();
    }

    @Test
    void paidByAssociationExposesAndAllowsItsRelationshipsAndAmount() {
        ClientTransaction transaction = mock(ClientTransaction.class);
        ClientCharge charge = mock(ClientCharge.class);
        ClientChargePaidBy paidBy = ClientChargePaidBy.instance(transaction, charge, new BigDecimal("7.50"));

        assertThat(paidBy.getClientTransaction()).isSameAs(transaction);
        assertThat(paidBy.getClientCharge()).isSameAs(charge);
        assertThat(paidBy.getAmount()).isEqualByComparingTo("7.50");
        paidBy.setClientTransaction(null);
        paidBy.setClientCharge(null);
        assertThat(paidBy.getClientTransaction()).isNull();
        assertThat(paidBy.getClientCharge()).isNull();
    }
}
