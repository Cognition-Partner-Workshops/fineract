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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.monetary.domain.OrganisationCurrency;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.charge.exception.ChargeCannotBeAppliedToException;
import org.apache.fineract.portfolio.client.data.ClientChargeDataValidator;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientCharge;
import org.apache.fineract.portfolio.client.domain.ClientChargeRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientTransaction;
import org.apache.fineract.portfolio.client.domain.ClientTransactionRepository;
import org.apache.fineract.portfolio.client.exception.ClientNotActiveException;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientChargeWritePlatformServiceImplTest {

    private static final OrganisationCurrency USD = new OrganisationCurrency("USD", "US Dollar", 2, null, "USD", "$");
    private static final LocalDate TODAY = LocalDate.of(2025, 1, 20);
    @Mock
    private ChargeRepositoryWrapper chargeRepository;
    @Mock
    private ClientRepository clients;
    @Mock
    private PlatformSecurityContext securityContext;
    @Mock
    private ConfigurationDomainService configuration;
    @Mock
    private HolidayRepositoryWrapper holidays;
    @Mock
    private WorkingDaysRepositoryWrapper workingDays;
    @Mock
    private ClientChargeRepositoryWrapper chargeInstances;
    @Mock
    private ClientTransactionRepository transactions;
    @Mock
    private PaymentDetailWritePlatformService paymentDetails;
    @Mock
    private JournalEntryWritePlatformService journals;
    @Mock
    private Client client;
    @Mock
    private Charge definition;
    @Mock
    private Office office;

    private ClientChargeWritePlatformServiceImpl service;
    private ClientCharge charge;
    private final FromJsonHelper helper = new FromJsonHelper();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "test", "Test", "UTC", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, TODAY)));
        MoneyHelper.initializeTenantRoundingMode("test", 6);
        service = new ClientChargeWritePlatformServiceImpl(chargeRepository, new ClientRepositoryWrapper(clients, securityContext),
                new ClientChargeDataValidator(helper), configuration, holidays, workingDays, chargeInstances, transactions, paymentDetails,
                journals);
        lenient().when(clients.findById(1L)).thenReturn(Optional.of(client));
        lenient().when(client.getId()).thenReturn(1L);
        lenient().when(client.getOffice()).thenReturn(office);
        lenient().when(client.getActivationDate()).thenReturn(TODAY.minusDays(10));
        lenient().when(office.getId()).thenReturn(2L);
        lenient().when(definition.getId()).thenReturn(3L);
        lenient().when(definition.getAmount()).thenReturn(new BigDecimal("100"));
        lenient().when(definition.getChargeCalculation()).thenReturn(1);
        lenient().when(definition.getChargeTimeType()).thenReturn(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        lenient().when(definition.isClientCharge()).thenReturn(true);
        lenient().when(configuration.allowTransactionsOnNonWorkingDayEnabled()).thenReturn(true);
        charge = ClientCharge.createNew(client, definition, command("{\"amount\":100,\"dueDate\":\"20 January 2025\"}"));
        charge.setId(4L);
        charge.setCurrency(USD);
        lenient().when(chargeInstances.findOneWithNotFoundDetection(4L)).thenReturn(charge);
        lenient().when(chargeRepository.findOneWithNotFoundDetection(3L)).thenReturn(definition);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
        MoneyHelper.clearCache();
    }

    @ParameterizedTest
    @CsvSource({ "inactive,charge.is.not.active", "waived,transaction.invalid.account.charge.is.already.waived",
            "paid,transaction.invalid.account.charge.is.paid" })
    void paymentRejectsInvalidChargeState(String state, String errorCode) {
        ClientCharge invalid = mock(ClientCharge.class);
        when(invalid.getCurrency()).thenReturn(USD.toMonetaryCurrency());
        when(chargeInstances.findOneWithNotFoundDetection(4L)).thenReturn(invalid);
        switch (state) {
            case "inactive" -> when(invalid.isNotActive()).thenReturn(true);
            case "waived" -> when(invalid.isWaived()).thenReturn(true);
            case "paid" -> when(invalid.isPaid()).thenReturn(true);
            default -> throw new IllegalArgumentException(state);
        }
        assertValidation(() -> service.payCharge(1L, 4L, payment("20 January 2025", 10)), errorCode);
        verifyNoInteractions(transactions, journals);
    }

    @Test
    void paymentRejectsAmountGreaterThanOutstanding() {
        assertValidation(() -> service.payCharge(1L, 4L, payment("20 January 2025", 101)),
                "transaction.invalid.charge.amount.paid.in.access");
        assertThat(charge.getAmountOutstanding().getAmount()).isEqualByComparingTo("100");
        verifyNoInteractions(transactions, journals);
    }

    @ParameterizedTest
    @CsvSource({ "09 January 2025,transaction.before.activationDate", "21 January 2025,transaction.is.futureDate" })
    void paymentRejectsDateOutsideActivationAndBusinessDate(String date, String errorCode) {
        assertValidation(() -> service.payCharge(1L, 4L, payment(date, 10)), errorCode);
        verifyNoInteractions(transactions, journals);
    }

    @Test
    void paymentPersistsTransactionAndPostsAccountingMapWithChargeIncomeAccount() {
        GLAccount account = mock(GLAccount.class);
        when(account.getId()).thenReturn(9L);
        when(definition.getAccount()).thenReturn(account);
        when(transactions.saveAndFlush(any())).thenAnswer(invocation -> {
            ClientTransaction transaction = invocation.getArgument(0);
            transaction.setId(5L);
            return transaction;
        });

        service.payCharge(1L, 4L, payment("20 January 2025", 40));

        assertThat(charge.getAmountPaid().getAmount()).isEqualByComparingTo("40");
        assertThat(charge.getAmountOutstanding().getAmount()).isEqualByComparingTo("60");
        ArgumentCaptor<ClientTransaction> saved = ArgumentCaptor.forClass(ClientTransaction.class);
        verify(transactions).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isPayChargeTransaction()).isTrue();
        assertThat(saved.getValue().getClientChargePaidByCollection()).singleElement().satisfies(paidBy -> {
            assertThat(paidBy.getAmount()).isEqualByComparingTo("40");
            assertThat(paidBy.getClientCharge()).isSameAs(charge);
            assertThat(paidBy.getClientTransaction()).isSameAs(saved.getValue());
        });
        ArgumentCaptor<Map<String, Object>> bridge = ArgumentCaptor.forClass(Map.class);
        verify(journals).createJournalEntriesForClientTransactions(bridge.capture());
        assertThat(bridge.getValue()).containsEntry("amount", new BigDecimal("40.00")).containsEntry("reversed", false)
                .containsEntry("clientId", 1L).containsEntry("officeId", 2L).containsEntry("currencyCode", "USD")
                .containsEntry("accountingEnabled", true);
        assertThat((List<Map<String, Object>>) bridge.getValue().get("clientChargesPaid")).singleElement()
                .satisfies(line -> assertThat(line).containsEntry("chargeId", 3L).containsEntry("clientChargeId", 4L)
                        .containsEntry("incomeAccountId", 9L).containsEntry("amount", new BigDecimal("40")));
    }

    @ParameterizedTest
    @CsvSource({ "paid,transaction.invalid.account.charge.is.paid", "waived,transaction.invalid.account.charge.is.already.waived" })
    void waiverRejectsSettledCharge(String state, String errorCode) {
        if ("paid".equals(state)) {
            charge.pay(Money.of(USD.toMonetaryCurrency(), new BigDecimal("100")));
        } else {
            charge.waive();
        }
        assertValidation(() -> service.waiveCharge(1L, 4L), errorCode);
        verifyNoInteractions(transactions, journals);
    }

    @Test
    void waiverPersistsTransactionForRemainingAmount() {
        charge.pay(Money.of(USD.toMonetaryCurrency(), new BigDecimal("40")));
        when(transactions.saveAndFlush(any())).thenAnswer(invocation -> {
            ClientTransaction transaction = invocation.getArgument(0);
            transaction.setId(5L);
            transaction.setCurrency(USD);
            return transaction;
        });
        service.waiveCharge(1L, 4L);
        assertThat(charge.isWaived()).isTrue();
        assertThat(charge.getAmountWaived().getAmount()).isEqualByComparingTo("60");
        assertThat(charge.getAmountOutstanding().getAmount()).isZero();
        ArgumentCaptor<ClientTransaction> saved = ArgumentCaptor.forClass(ClientTransaction.class);
        verify(transactions).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isWaiveChargeTransaction()).isTrue();
        assertThat(saved.getValue().getAmount().getAmount()).isEqualByComparingTo("60");
        assertThat(saved.getValue().getClientChargePaidByCollection()).singleElement()
                .satisfies(paidBy -> assertThat(paidBy.getAmount()).isEqualByComparingTo("60"));
    }

    @Test
    void addRejectsChargeNotApplicableToClients() {
        when(definition.isClientCharge()).thenReturn(false);
        ChargeCannotBeAppliedToException error = assertThrows(ChargeCannotBeAppliedToException.class,
                () -> service.addCharge(1L, addCommand()));
        assertThat(error).hasMessage("Charge with identifier 3 cannot be applied to a Client");
        verify(chargeInstances, never()).saveAndFlush(any());
    }

    @Test
    void addRejectsDueDateBeforeActivation() {
        assertValidation(() -> service.addCharge(1L, command("{\"chargeId\":3,\"amount\":10,\"dueDate\":\"09 January 2025\"}")),
                "dueDate.before.activationDate");
        verify(chargeInstances, never()).saveAndFlush(any());
    }

    @Test
    void addRejectsHolidayDueDate() {
        when(holidays.isHoliday(2L, TODAY)).thenReturn(true);
        assertValidation(() -> service.addCharge(1L, addCommand()), "charge.due.date.is.on.holiday");
        verify(chargeInstances, never()).saveAndFlush(any());
    }

    @Test
    void addRejectsInactiveClientThroughRepositoryGuard() {
        when(client.isNotActive()).thenReturn(true);
        ClientNotActiveException error = assertThrows(ClientNotActiveException.class, () -> service.addCharge(1L, addCommand()));
        assertThat(error).hasMessage("The Client with id `1` is not active");
        verifyNoInteractions(chargeRepository);
    }

    @Test
    void addCurrentlyDoesNotRejectOtherChargeTimeOrCurrency() {
        when(definition.getChargeTimeType()).thenReturn(ChargeTimeType.DISBURSEMENT.getValue());
        when(definition.getCurrencyCode()).thenReturn("EUR");
        service.addCharge(1L, addCommand());
        ArgumentCaptor<ClientCharge> saved = ArgumentCaptor.forClass(ClientCharge.class);
        verify(chargeInstances).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getChargeTime()).isEqualTo(ChargeTimeType.DISBURSEMENT.getValue());
        assertThat(saved.getValue().getCharge().getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    void deleteRejectsPaidCharge() {
        charge.pay(Money.of(USD.toMonetaryCurrency(), new BigDecimal("100")));
        assertValidation(() -> service.deleteCharge(1L, 4L), "transaction.invalid.account.charge.is.paid");
        verify(chargeInstances, never()).delete(any());
    }

    private JsonCommand payment(String date, int amount) {
        return command("{\"transactionDate\":\"" + date + "\",\"amount\":" + amount + "}");
    }

    private JsonCommand addCommand() {
        return command("{\"chargeId\":3,\"amount\":10,\"dueDate\":\"20 January 2025\"}");
    }

    private JsonCommand command(String json) {
        return new JsonCommand(1L,
                helper.parse(json.substring(0, json.length() - 1) + ",\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\"}"), helper);
    }

    private void assertValidation(org.junit.jupiter.api.function.Executable action, String code) {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class, action);
        assertThat(error.getErrors()).singleElement()
                .satisfies(parameter -> assertThat(parameter.getUserMessageGlobalisationCode()).endsWith("." + code));
    }
}
