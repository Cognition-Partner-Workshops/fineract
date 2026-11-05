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
package org.apache.fineract.organisation.teller.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccount;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.organisation.teller.data.CashierTransactionDataValidator;
import org.apache.fineract.organisation.teller.domain.Cashier;
import org.apache.fineract.organisation.teller.domain.CashierRepository;
import org.apache.fineract.organisation.teller.domain.CashierTransactionRepository;
import org.apache.fineract.organisation.teller.domain.Teller;
import org.apache.fineract.organisation.teller.domain.TellerRepositoryWrapper;
import org.apache.fineract.organisation.teller.exception.CashierNotFoundException;
import org.apache.fineract.organisation.teller.serialization.TellerCommandFromApiJsonDeserializer;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Regression tests: teller/cashier operations must stay within the user's office hierarchy and a cashier can only be
 * acted on through the teller it belongs to.
 */
class TellerWritePlatformServiceJpaImplOfficeScopeTest {

    private static final Long OWN_OFFICE_ID = 2L;
    private static final Long SIBLING_OFFICE_ID = 3L;
    private static final Long OWN_TELLER_ID = 11L;
    private static final Long SIBLING_TELLER_ID = 12L;
    private static final Long SIBLING_CASHIER_ID = 22L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private TellerCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    @Mock
    private TellerRepositoryWrapper tellerRepositoryWrapper;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private CashierRepository cashierRepository;
    @Mock
    private CashierTransactionRepository cashierTxnRepository;
    @Mock
    private JournalEntryRepository glJournalEntryRepository;
    @Mock
    private FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper;
    @Mock
    private CashierTransactionDataValidator cashierTransactionDataValidator;

    @InjectMocks
    private TellerWritePlatformServiceJpaImpl underTest;

    private AutoCloseable mocks;
    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();
    private Office ownOffice;
    private Office siblingOffice;
    private Cashier siblingCashier;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2024, 1, 1))));
        MoneyHelper.initializeTenantRoundingMode("default", 6);
        ownOffice = office(OWN_OFFICE_ID, ".1.2.");
        siblingOffice = office(SIBLING_OFFICE_ID, ".1.3.");
        when(ownOffice.doesNotHaveAnOfficeInHierarchyWithId(OWN_OFFICE_ID)).thenReturn(false);
        when(ownOffice.doesNotHaveAnOfficeInHierarchyWithId(SIBLING_OFFICE_ID)).thenReturn(true);
        when(officeRepositoryWrapper.findOfficeHierarchy(OWN_OFFICE_ID)).thenReturn(ownOffice);
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(OWN_OFFICE_ID)).thenReturn(ownOffice);
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(SIBLING_OFFICE_ID)).thenReturn(siblingOffice);

        final Teller ownTeller = teller(OWN_TELLER_ID, ownOffice);
        final Teller siblingTeller = teller(SIBLING_TELLER_ID, siblingOffice);
        when(tellerRepositoryWrapper.findOneWithNotFoundDetection(OWN_TELLER_ID)).thenReturn(ownTeller);
        when(tellerRepositoryWrapper.findOneWithNotFoundDetection(SIBLING_TELLER_ID)).thenReturn(siblingTeller);

        siblingCashier = mock(Cashier.class);
        when(siblingCashier.getId()).thenReturn(SIBLING_CASHIER_ID);
        when(siblingCashier.getTeller()).thenReturn(siblingTeller);
        when(siblingCashier.update(any())).thenReturn(new HashMap<>());
        when(cashierRepository.findById(SIBLING_CASHIER_ID)).thenReturn(Optional.of(siblingCashier));

        when(staffRepository.findById(anyLong())).thenReturn(Optional.of(mock(Staff.class)));
        final FinancialActivityAccount financialActivityAccount = mock(FinancialActivityAccount.class);
        when(financialActivityAccount.getGlAccount()).thenReturn(mock(GLAccount.class));
        when(financialActivityAccountRepositoryWrapper.findByFinancialActivityTypeWithNotFoundDetection(anyInt()))
                .thenReturn(financialActivityAccount);

        final AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(5L);
        when(user.getOffice()).thenReturn(ownOffice);
        when(context.authenticatedUser()).thenReturn(user);
        doAnswer(invocation -> {
            if (!invocation.<String>getArgument(0).startsWith(".1.2.")) {
                throw new NoAuthorizationException("The user doesn't have enough permissions to access the resource.");
            }
            return null;
        }).when(context).validateAccessRights(anyString());
    }

    @AfterEach
    void tearDown() throws Exception {
        ThreadLocalContextUtil.reset();
        mocks.close();
    }

    @Test
    void cashierOfAnotherTellerCannotBeUpdatedThroughOwnTeller() {
        final JsonCommand command = command("{\"staffId\": 1}", OWN_TELLER_ID, SIBLING_CASHIER_ID);

        assertThrows(CashierNotFoundException.class, () -> underTest.updateCashierAllocation(OWN_TELLER_ID, SIBLING_CASHIER_ID, command));
        verify(cashierRepository, never()).saveAndFlush(any());
    }

    @Test
    void cashierOfAnotherTellerCannotBeDeletedThroughOwnTeller() {
        final JsonCommand command = command("{}", OWN_TELLER_ID, SIBLING_CASHIER_ID);

        assertThrows(CashierNotFoundException.class, () -> underTest.deleteCashierAllocation(OWN_TELLER_ID, SIBLING_CASHIER_ID, command));
        verify(cashierRepository, never()).delete(any(Cashier.class));
    }

    @Test
    void cashFromSiblingBranchCashierCannotBeSettled() {
        final JsonCommand command = command(cashTxnJson(), SIBLING_TELLER_ID, SIBLING_CASHIER_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.settleCashFromCashier(SIBLING_CASHIER_ID, command));
        verify(cashierTxnRepository, never()).save(any());
        verify(glJournalEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void cashCannotBeAllocatedToSiblingBranchCashierThroughOwnTeller() {
        final JsonCommand command = command(cashTxnJson(), OWN_TELLER_ID, SIBLING_CASHIER_ID);

        assertThrows(CashierNotFoundException.class, () -> underTest.allocateCashToCashier(SIBLING_CASHIER_ID, command));
        verify(cashierTxnRepository, never()).save(any());
        verify(glJournalEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void tellerCannotBeCreatedInSiblingBranch() {
        final JsonCommand command = command("{\"officeId\": " + SIBLING_OFFICE_ID + ", \"name\": \"t\", \"status\": 300}", null, null);

        assertThrows(NoAuthorizationException.class, () -> underTest.createTeller(command));
        verify(tellerRepositoryWrapper, never()).saveAndFlush(any());
    }

    @Test
    void ownBranchCashierCanStillReceiveCash() {
        final Teller ownTeller = tellerRepositoryWrapper.findOneWithNotFoundDetection(OWN_TELLER_ID);
        final Cashier ownCashier = mock(Cashier.class);
        when(ownCashier.getId()).thenReturn(21L);
        when(ownCashier.getTeller()).thenReturn(ownTeller);
        when(cashierRepository.findById(21L)).thenReturn(Optional.of(ownCashier));
        final JsonCommand command = command(cashTxnJson(), OWN_TELLER_ID, 21L);

        assertDoesNotThrow(() -> underTest.allocateCashToCashier(21L, command));
        verify(cashierTxnRepository).save(any());
    }

    private JsonCommand command(final String json, final Long tellerId, final Long cashierId) {
        return JsonCommand.from(json, JsonParser.parseString(json), fromJsonHelper, null, tellerId, cashierId, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private static String cashTxnJson() {
        return "{\"txnAmount\": 1000, \"txnDate\": \"01 January 2024\", \"currencyCode\": \"USD\", \"txnNote\": \"n\","
                + " \"locale\": \"en\", \"dateFormat\": \"dd MMMM yyyy\"}";
    }

    private static Office office(final Long id, final String hierarchy) {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(id);
        when(office.getHierarchy()).thenReturn(hierarchy);
        return office;
    }

    private static Teller teller(final Long id, final Office office) {
        final Teller teller = mock(Teller.class);
        when(teller.getId()).thenReturn(id);
        when(teller.getOffice()).thenReturn(office);
        final Long officeId = office.getId();
        when(teller.officeId()).thenReturn(officeId);
        return teller;
    }
}
