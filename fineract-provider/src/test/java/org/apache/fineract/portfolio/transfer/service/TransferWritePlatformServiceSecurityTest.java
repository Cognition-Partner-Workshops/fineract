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
package org.apache.fineract.portfolio.transfer.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientTransferDetailsRepositoryWrapper;
import org.apache.fineract.portfolio.group.domain.GroupRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanOfficerService;
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformService;
import org.apache.fineract.portfolio.note.service.NoteWritePlatformService;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.portfolio.transfer.data.TransfersDataValidator;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class TransferWritePlatformServiceSecurityTest {

    private static final Long CLIENT_ID = 23L;
    private static final Long SOURCE_OFFICE_ID = 7L;
    private static final Long DESTINATION_OFFICE_ID = 8L;

    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private OfficeRepositoryWrapper officeRepository;
    @Mock
    private CalendarInstanceRepository calendarInstanceRepository;
    @Mock
    private GroupRepositoryWrapper groupRepository;
    @Mock
    private LoanWritePlatformService loanWritePlatformService;
    @Mock
    private SavingsAccountWritePlatformService savingsAccountWritePlatformService;
    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper;
    @Mock
    private TransfersDataValidator transfersDataValidator;
    @Mock
    private NoteWritePlatformService noteWritePlatformService;
    @Mock
    private StaffRepositoryWrapper staffRepositoryWrapper;
    @Mock
    private ClientTransferDetailsRepositoryWrapper clientTransferDetailsRepositoryWrapper;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private LoanOfficerService loanOfficerService;
    @Mock
    private OfficeScopeValidator officeScopeValidator;
    @InjectMocks
    private TransferWritePlatformServiceJpaRepositoryImpl underTest;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.now())));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ThreadLocalContextUtil.reset();
    }

    @Test
    void rejectsClientTransferToOfficeOutsideUserScope() {
        setupTransfer(DESTINATION_OFFICE_ID);
        doThrow(new NoAuthorizationException("outside office scope")).when(officeScopeValidator).validateOffice(DESTINATION_OFFICE_ID);

        assertThrows(NoAuthorizationException.class,
                () -> underTest.proposeAndAcceptClientTransfer(CLIENT_ID, transferCommand(DESTINATION_OFFICE_ID)));
    }

    @Test
    void permitsClientTransferToOfficeWithinUserScope() {
        setupTransfer(SOURCE_OFFICE_ID);

        assertDoesNotThrow(() -> underTest.proposeAndAcceptClientTransfer(CLIENT_ID, transferCommand(SOURCE_OFFICE_ID)));

        verify(officeScopeValidator).validateOffice(SOURCE_OFFICE_ID);
    }

    private void setupTransfer(final Long destinationOfficeId) {
        final Office sourceOffice = office(SOURCE_OFFICE_ID);
        final Office destinationOffice = office(destinationOfficeId);
        final Client client = Mockito.mock(Client.class);
        final AppUser appUser = Mockito.mock(AppUser.class);
        when(officeRepository.findOneWithNotFoundDetection(destinationOfficeId)).thenReturn(destinationOffice);
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(CLIENT_ID, true)).thenReturn(client);
        when(client.getId()).thenReturn(CLIENT_ID);
        when(client.getOffice()).thenReturn(sourceOffice);
        when(client.getTransferToOffice()).thenReturn(destinationOffice);
        when(client.getGroups()).thenReturn(Set.of());
        when(context.authenticatedUser()).thenReturn(appUser);
        when(appUser.getId()).thenReturn(19L);
    }

    private Office office(final Long id) {
        final Office office = Mockito.mock(Office.class);
        when(office.getId()).thenReturn(id);
        return office;
    }

    private JsonCommand transferCommand(final Long destinationOfficeId) {
        final JsonCommand command = Mockito.mock(JsonCommand.class);
        when(command.json()).thenReturn("{\"destinationOfficeId\":" + destinationOfficeId + "}");
        when(command.longValueOfParameterNamed("destinationOfficeId")).thenReturn(destinationOfficeId);
        return command;
    }
}
