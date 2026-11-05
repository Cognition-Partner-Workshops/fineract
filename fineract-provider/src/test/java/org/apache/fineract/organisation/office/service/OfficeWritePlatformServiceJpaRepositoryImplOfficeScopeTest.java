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
package org.apache.fineract.organisation.office.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.office.domain.OfficeTransaction;
import org.apache.fineract.organisation.office.domain.OfficeTransactionRepository;
import org.apache.fineract.organisation.office.serialization.OfficeCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.office.serialization.OfficeTransactionCommandFromApiJsonDeserializer;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

/**
 * Regression tests: office money transfers can only be created or deleted for offices within the user's hierarchy.
 */
class OfficeWritePlatformServiceJpaRepositoryImplOfficeScopeTest {

    private static final Long OWN_OFFICE_ID = 2L;
    private static final Long SIBLING_OFFICE_ID = 3L;
    private static final Long TRANSACTION_ID = 9L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private OfficeCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    @Mock
    private OfficeTransactionCommandFromApiJsonDeserializer moneyTransferCommandFromApiJsonDeserializer;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private OfficeTransactionRepository officeTransactionRepository;
    @Mock
    private ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;

    @InjectMocks
    private OfficeWritePlatformServiceJpaRepositoryImpl underTest;

    private AutoCloseable mocks;
    private MockedStatic<MoneyHelper> moneyHelper;
    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();
    private Office ownOffice;
    private Office siblingOffice;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        moneyHelper = mockStatic(MoneyHelper.class);
        moneyHelper.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        moneyHelper.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        ownOffice = office(OWN_OFFICE_ID, ".1.2.");
        siblingOffice = office(SIBLING_OFFICE_ID, ".1.3.");
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(OWN_OFFICE_ID)).thenReturn(ownOffice);
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(SIBLING_OFFICE_ID)).thenReturn(siblingOffice);
        final ApplicationCurrency currency = mock(ApplicationCurrency.class);
        when(currency.getCode()).thenReturn("USD");
        when(currency.getDecimalPlaces()).thenReturn(2);
        when(applicationCurrencyRepository.findOneWithNotFoundDetection(anyString())).thenReturn(currency);
        loginWithHierarchy(".1.2.");
    }

    @AfterEach
    void tearDown() throws Exception {
        moneyHelper.close();
        mocks.close();
    }

    @Test
    void transferOutOfSiblingBranchIsRejected() {
        final JsonCommand command = transferCommand(SIBLING_OFFICE_ID, OWN_OFFICE_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.officeTransaction(command));
        verify(officeTransactionRepository, never()).save(any());
    }

    @Test
    void transferOutOfOwnBranchIsAllowed() {
        final JsonCommand command = transferCommand(OWN_OFFICE_ID, SIBLING_OFFICE_ID);

        assertDoesNotThrow(() -> underTest.officeTransaction(command));
        verify(officeTransactionRepository).save(any());
    }

    @Test
    void siblingBranchTransferCannotBeDeleted() {
        final OfficeTransaction transaction = transaction(siblingOffice, ownOffice);
        when(officeTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));

        assertThrows(NoAuthorizationException.class, () -> underTest.deleteOfficeTransaction(TRANSACTION_ID, JsonCommand.from("{}")));
        verify(officeTransactionRepository, never()).deleteById(anyLong());
        verify(officeTransactionRepository, never()).delete(any(OfficeTransaction.class));
    }

    @Test
    void headOfficeUserCanDeleteBranchTransfer() {
        loginWithHierarchy(".");
        final OfficeTransaction transaction = transaction(siblingOffice, ownOffice);
        when(officeTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));

        assertDoesNotThrow(() -> underTest.deleteOfficeTransaction(TRANSACTION_ID, JsonCommand.from("{}")));
    }

    private void loginWithHierarchy(final String hierarchy) {
        final Office userOffice = office(99L, hierarchy);
        final AppUser user = mock(AppUser.class);
        when(user.getOffice()).thenReturn(userOffice);
        when(context.authenticatedUser()).thenReturn(user);
        doAnswer(invocation -> {
            if (!invocation.<String>getArgument(0).startsWith(hierarchy)) {
                throw new NoAuthorizationException("The user doesn't have enough permissions to access the resource.");
            }
            return null;
        }).when(context).validateAccessRights(anyString());
    }

    private JsonCommand transferCommand(final Long fromOfficeId, final Long toOfficeId) {
        final String json = "{\"fromOfficeId\": " + fromOfficeId + ", \"toOfficeId\": " + toOfficeId
                + ", \"transactionDate\": \"01 January 2024\", \"currencyCode\": \"USD\", \"transactionAmount\": 500,"
                + " \"description\": \"d\", \"locale\": \"en\", \"dateFormat\": \"dd MMMM yyyy\"}";
        return JsonCommand.from(json, JsonParser.parseString(json), fromJsonHelper, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    private static OfficeTransaction transaction(final Office from, final Office to) {
        final OfficeTransaction transaction = mock(OfficeTransaction.class);
        when(transaction.getFrom()).thenReturn(from);
        when(transaction.getTo()).thenReturn(to);
        return transaction;
    }

    private static Office office(final Long id, final String hierarchy) {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(id);
        when(office.getHierarchy()).thenReturn(hierarchy);
        return office;
    }
}
