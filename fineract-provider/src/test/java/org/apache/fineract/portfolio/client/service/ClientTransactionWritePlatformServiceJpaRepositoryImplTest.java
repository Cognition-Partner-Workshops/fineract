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
package org.apache.fineract.portfolio.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.monetary.domain.OrganisationCurrency;
import org.apache.fineract.organisation.monetary.domain.OrganisationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientCharge;
import org.apache.fineract.portfolio.client.domain.ClientChargePaidBy;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientTransaction;
import org.apache.fineract.portfolio.client.domain.ClientTransactionRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientTransactionCannotBeUndoneException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientTransactionWritePlatformServiceJpaRepositoryImplTest {

    private static final OrganisationCurrency USD = new OrganisationCurrency("USD", "US Dollar", 2, null, "USD", "$");
    private static final LocalDate TODAY = LocalDate.of(2025, 1, 20);
    @Mock
    private ClientRepositoryWrapper clients;
    @Mock
    private ClientTransactionRepositoryWrapper transactions;
    @Mock
    private OrganisationCurrencyRepositoryWrapper currencies;
    @Mock
    private JournalEntryWritePlatformService journals;
    @Mock
    private Client client;
    @Mock
    private Office office;
    @Mock
    private Charge definition;
    private ClientTransactionWritePlatformServiceJpaRepositoryImpl service;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "test", "Test", "UTC", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, TODAY)));
        MoneyHelper.initializeTenantRoundingMode("test", 6);
        service = new ClientTransactionWritePlatformServiceJpaRepositoryImpl(transactions, clients, currencies, journals);
        when(clients.getActiveClientInUserScope(1L)).thenReturn(client);
        lenient().when(client.getId()).thenReturn(1L);
        lenient().when(client.officeId()).thenReturn(2L);
        lenient().when(office.getId()).thenReturn(2L);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
        MoneyHelper.clearCache();
    }

    @Test
    void undoRejectsAlreadyReversedTransaction() {
        ClientTransaction transaction = transaction(false);
        transaction.reverse();
        when(transactions.findOneWithNotFoundDetection(1L, 5L)).thenReturn(transaction);

        ClientTransactionCannotBeUndoneException error = assertThrows(ClientTransactionCannotBeUndoneException.class,
                () -> service.undo(1L, 5L));

        assertThat(error).hasMessage("Client transaction with identifier 5 for client with identifier 1 has already been reversed");
        assertThat(error.getGlobalisationMessageCode()).isEqualTo("error.msg.clients.transaction.cannot.be.undone");
        verifyNoInteractions(currencies, journals);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void undoRestoresChargeAndPostsReversedAccountingMap(boolean waiver) {
        when(definition.getAmount()).thenReturn(new BigDecimal("100"));
        when(definition.getChargeCalculation()).thenReturn(1);
        when(definition.getChargeTimeType()).thenReturn(2);
        when(definition.getCurrencyCode()).thenReturn("USD");
        FromJsonHelper helper = new FromJsonHelper();
        ClientCharge charge = ClientCharge.createNew(client, definition, new JsonCommand(1L, helper.parse("{}"), helper));
        charge.setCurrency(USD);
        charge.setId(4L);
        ClientTransaction transaction = transaction(waiver);
        if (waiver) {
            charge.waive();
        } else {
            charge.pay(transaction.getAmount());
        }
        transaction.getClientChargePaidByCollection().add(ClientChargePaidBy.instance(transaction, charge, transaction.getAmount().getAmount()));
        when(transactions.findOneWithNotFoundDetection(1L, 5L)).thenReturn(transaction);
        when(currencies.findOneWithNotFoundDetection("USD")).thenReturn(USD);

        service.undo(1L, 5L);

        assertThat(transaction.isReversed()).isTrue();
        assertThat(charge.getAmountOutstanding().getAmount()).isEqualByComparingTo("100");
        assertThat(charge.getAmountPaid().getAmount()).isZero();
        assertThat(charge.getAmountWaived().getAmount()).isZero();
        assertThat(charge.isPaid()).isFalse();
        assertThat(charge.isWaived()).isFalse();
        assertThat(charge.isActive()).isTrue();
        verify(transactions).saveAndFlush(transaction);
        ArgumentCaptor<Map<String, Object>> bridge = ArgumentCaptor.forClass(Map.class);
        verify(journals).createJournalEntriesForClientTransactions(bridge.capture());
        assertThat(bridge.getValue()).containsEntry("id", 5L).containsEntry("reversed", true)
                .containsEntry("amount", new BigDecimal("100.00")).containsEntry("accountingEnabled", false);
    }

    private ClientTransaction transaction(boolean waiver) {
        Money amount = Money.of(USD.toMonetaryCurrency(), new BigDecimal("100"));
        ClientTransaction transaction = waiver ? ClientTransaction.waiver(client, office, TODAY, amount, "USD")
                : ClientTransaction.payCharge(client, office, null, TODAY, amount, "USD", ExternalId.empty());
        transaction.setId(5L);
        transaction.setCurrency(USD);
        return transaction;
    }
}
