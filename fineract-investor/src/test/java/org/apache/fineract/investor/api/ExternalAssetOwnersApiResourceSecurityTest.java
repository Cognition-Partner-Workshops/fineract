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
package org.apache.fineract.investor.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.investor.api.search.ExternalAssetOwnersSearchApiDelegate;
import org.apache.fineract.investor.service.ExternalAssetOwnerLoanProductAttributesReadService;
import org.apache.fineract.investor.service.ExternalAssetOwnersReadService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformServiceCommon;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalAssetOwnersApiResourceSecurityTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private ExternalAssetOwnersReadService externalAssetOwnersReadService;
    @Mock
    private ExternalAssetOwnerLoanProductAttributesReadService loanProductAttributesReadService;
    @Mock
    private ExternalAssetOwnersSearchApiDelegate delegate;
    @Mock
    private LoanReadPlatformServiceCommon loanReadPlatformService;
    @Mock
    private UriInfo uriInfo;
    @InjectMocks
    private ExternalAssetOwnersApiResource underTest;
    @InjectMocks
    private ExternalAssetOwnerLoanProductAttributesApiResource attributesUnderTest;

    @BeforeEach
    void setUp() {
        lenient().when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void rejectsTransferListWithoutTransferReadPermission() {
        deny("EXTERNAL_ASSET_OWNER_TRANSFER");

        assertThrows(NoAuthorizationException.class, () -> underTest.getTransfers(null, 18L, null, 0, 10));
    }

    @Test
    void rejectsActiveTransferWithoutTransferReadPermission() {
        deny("EXTERNAL_ASSET_OWNER_TRANSFER");

        assertThrows(NoAuthorizationException.class, () -> underTest.getActiveTransfer(null, 18L, null));
    }

    @Test
    void rejectsTransferJournalEntriesWithoutTransferReadPermission() {
        deny("EXTERNAL_ASSET_OWNER_TRANSFER");

        assertThrows(NoAuthorizationException.class, () -> underTest.getJournalEntriesOfTransfer(7L, 0, 10));
    }

    @Test
    void rejectsOwnerJournalEntriesWithoutOwnerReadPermission() {
        deny("EXTERNAL_ASSET_OWNER");

        assertThrows(NoAuthorizationException.class, () -> underTest.getJournalEntriesOfOwner("owner", 0, 10));
    }

    @Test
    void rejectsTransferJournalEntriesWithoutJournalReadPermission() {
        deny("JOURNALENTRY");

        assertThrows(NoAuthorizationException.class, () -> underTest.getJournalEntriesOfTransfer(7L, 0, 10));
    }

    @Test
    void rejectsOwnerJournalEntriesWithoutJournalReadPermission() {
        deny("JOURNALENTRY");

        assertThrows(NoAuthorizationException.class, () -> underTest.getJournalEntriesOfOwner("owner", 0, 10));
    }

    @Test
    void rejectsTransferSearchWithoutTransferReadPermission() {
        deny("EXTERNAL_ASSET_OWNER_TRANSFER");

        assertThrows(NoAuthorizationException.class, () -> underTest.searchInvestorData(null));
    }

    @Test
    void permitsInvestorReadsWhenAuthorized() {
        assertDoesNotThrow(() -> {
            underTest.getTransfers(null, 18L, null, 0, 10);
            underTest.getActiveTransfer(null, 18L, null);
            underTest.getJournalEntriesOfTransfer(7L, 0, 10);
            underTest.getJournalEntriesOfOwner("owner", 0, 10);
            underTest.searchInvestorData(null);
            attributesUnderTest.getExternalAssetOwnerLoanProductAttributes(uriInfo, 11L, null);
        });

        verify(user, times(4)).validateHasReadPermission("EXTERNAL_ASSET_OWNER_TRANSFER");
        verify(user).validateHasReadPermission("EXTERNAL_ASSET_OWNER");
        verify(user, times(2)).validateHasReadPermission("JOURNALENTRY");
        verify(user).validateHasReadPermission("EXTERNAL_ASSET_OWNER_LOAN_PRODUCT_ATTRIBUTE");
    }

    @Test
    void rejectsLoanProductAttributeReadWithoutPermission() {
        deny("EXTERNAL_ASSET_OWNER_LOAN_PRODUCT_ATTRIBUTE");

        assertThrows(NoAuthorizationException.class,
                () -> attributesUnderTest.getExternalAssetOwnerLoanProductAttributes(uriInfo, 11L, null));
    }

    private void deny(final String resourceName) {
        lenient().doThrow(new NoAuthorizationException("read permission required")).when(user).validateHasReadPermission(resourceName);
    }
}
