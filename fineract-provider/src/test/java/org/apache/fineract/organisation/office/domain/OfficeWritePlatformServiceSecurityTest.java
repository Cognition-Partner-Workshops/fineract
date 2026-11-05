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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fineract.organisation.office.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.exception.OfficeTransactionNotFoundException;
import org.apache.fineract.organisation.office.serialization.OfficeCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.office.serialization.OfficeTransactionCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.office.service.OfficeWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class OfficeWritePlatformServiceSecurityTest {

    private static final Long TRANSACTION_ID = 53L;
    private static final Long USER_OFFICE_ID = 7L;
    private static final Long FOREIGN_OFFICE_ID = 99L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private OfficeCommandFromApiJsonDeserializer officeCommandFromApiJsonDeserializer;
    @Mock
    private OfficeTransactionCommandFromApiJsonDeserializer officeTransactionCommandFromApiJsonDeserializer;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private OfficeTransactionRepository officeTransactionRepository;
    @Mock
    private ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;
    @Mock
    private OfficeScopeValidator officeScopeValidator;
    @InjectMocks
    private OfficeWritePlatformServiceJpaRepositoryImpl underTest;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        MoneyHelper.initializeTenantRoundingMode("default", 4);
        when(context.authenticatedUser()).thenReturn(Mockito.mock(AppUser.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ThreadLocalContextUtil.reset();
    }

    @Test
    void rejectsCreateWithForeignOfficeBeforeSaving() {
        final Office fromOffice = office(FOREIGN_OFFICE_ID);
        final Office toOffice = office(USER_OFFICE_ID);
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(FOREIGN_OFFICE_ID)).thenReturn(fromOffice);
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(USER_OFFICE_ID)).thenReturn(toOffice);
        when(applicationCurrencyRepository.findOneWithNotFoundDetection("USD"))
                .thenReturn(new ApplicationCurrency("USD", "US Dollar", 2, 1, "USD", "$"));
        doThrow(new NoAuthorizationException("outside office scope")).when(officeScopeValidator).validateOffice(FOREIGN_OFFICE_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.officeTransaction(createCommand(FOREIGN_OFFICE_ID, USER_OFFICE_ID)));

        verify(officeTransactionRepository, never()).save(any(OfficeTransaction.class));
    }

    @Test
    void permitsCreateWhenBothOfficesAreInScope() {
        final Office fromOffice = office(USER_OFFICE_ID);
        final Office toOffice = office(8L);
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(USER_OFFICE_ID)).thenReturn(fromOffice);
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(8L)).thenReturn(toOffice);
        when(applicationCurrencyRepository.findOneWithNotFoundDetection("USD"))
                .thenReturn(new ApplicationCurrency("USD", "US Dollar", 2, 1, "USD", "$"));

        assertDoesNotThrow(() -> underTest.officeTransaction(createCommand(USER_OFFICE_ID, 8L)));

        verify(officeScopeValidator).validateOffice(USER_OFFICE_ID);
        verify(officeScopeValidator).validateOffice(8L);
        verify(officeTransactionRepository).save(any(OfficeTransaction.class));
    }

    @Test
    void rejectsDeleteWhenEitherOfficeIsOutOfScope() {
        final OfficeTransaction transaction = officeTransaction(FOREIGN_OFFICE_ID, USER_OFFICE_ID);
        when(officeTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));
        doThrow(new NoAuthorizationException("outside office scope")).when(officeScopeValidator).validateOffice(FOREIGN_OFFICE_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.deleteOfficeTransaction(TRANSACTION_ID, JsonCommand.from("{}")));

        verify(officeTransactionRepository, never()).delete(transaction);
    }

    @Test
    void permitsDeleteWhenBothOfficesAreInScope() {
        final OfficeTransaction transaction = officeTransaction(USER_OFFICE_ID, 8L);
        when(officeTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));

        assertDoesNotThrow(() -> underTest.deleteOfficeTransaction(TRANSACTION_ID, JsonCommand.from("{}")));

        verify(officeScopeValidator).validateOffice(USER_OFFICE_ID);
        verify(officeScopeValidator).validateOffice(8L);
        verify(officeTransactionRepository).delete(transaction);
    }

    @Test
    void missingDeleteUsesOfficeTransactionNotFoundException() {
        when(officeTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

        assertThrows(OfficeTransactionNotFoundException.class,
                () -> underTest.deleteOfficeTransaction(TRANSACTION_ID, JsonCommand.from("{}")));
    }

    private Office office(final Long id) {
        final Office office = Mockito.mock(Office.class);
        when(office.getId()).thenReturn(id);
        return office;
    }

    private OfficeTransaction officeTransaction(final Long fromOfficeId, final Long toOfficeId) {
        final OfficeTransaction transaction = new OfficeTransaction();
        ReflectionTestUtils.setField(transaction, "from", office(fromOfficeId));
        ReflectionTestUtils.setField(transaction, "to", office(toOfficeId));
        return transaction;
    }

    private JsonCommand createCommand(final Long fromOfficeId, final Long toOfficeId) {
        final JsonCommand command = Mockito.mock(JsonCommand.class);
        when(command.json()).thenReturn("{\"fromOfficeId\":" + fromOfficeId + ",\"toOfficeId\":" + toOfficeId
                + ",\"currencyCode\":\"USD\",\"transactionAmount\":10.00}");
        when(command.longValueOfParameterNamed("fromOfficeId")).thenReturn(fromOfficeId);
        when(command.longValueOfParameterNamed("toOfficeId")).thenReturn(toOfficeId);
        when(command.stringValueOfParameterNamed("currencyCode")).thenReturn("USD");
        when(command.bigDecimalValueOfParameterNamed("transactionAmount")).thenReturn(new BigDecimal("10.00"));
        return command;
    }
}
