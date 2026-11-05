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
 * distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiGlobalErrorResponse;
import org.apache.fineract.infrastructure.core.exceptionmapper.NoAuthorizationExceptionMapper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.GLIMAccountInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.GroupLoanIndividualMonitoringAccount;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanApplicationWritePlatformServiceGlimMakerCheckerTest {

    private static final Long PARENT_LOAN_ID = 31L;
    private static final Long CHILD_LOAN_ID = 32L;

    @Mock
    private GLIMAccountInfoRepository glimRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser appUser;
    @Mock
    private Loan childLoan;
    @Mock
    private Office childOffice;
    @Mock
    private JsonCommand command;
    @InjectMocks
    private LoanApplicationWritePlatformServiceJpaRepositoryImpl underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
        when(childLoan.getId()).thenReturn(CHILD_LOAN_ID);
        when(childLoan.getOffice()).thenReturn(childOffice);
        when(childOffice.getHierarchy()).thenReturn("1.");
        when(glimRepository.findById(PARENT_LOAN_ID)).thenReturn(Optional.of(mock(GroupLoanIndividualMonitoringAccount.class)));
        when(loanRepository.findByGlimId(PARENT_LOAN_ID)).thenReturn(List.of(childLoan));
    }

    @Test
    void failsClosedWithUnauthorizedApiCodeWhenMakerCheckerBlocksGlimApproval() {
        JsonArray approvalData = new JsonArray();
        JsonObject childApproval = new JsonObject();
        childApproval.addProperty("loanId", CHILD_LOAN_ID);
        approvalData.add(childApproval);
        when(command.arrayOfParameterNamed("approvalFormData")).thenReturn(approvalData);
        when(command.bigDecimalValueOfParameterNamed("glimPrincipal")).thenReturn(BigDecimal.ONE);
        when(configurationDomainService.isMakerCheckerEnabledForTask("APPROVE_LOAN")).thenReturn(true);

        NoAuthorizationException exception = assertThrows(NoAuthorizationException.class,
                () -> underTest.approveGLIMLoanAppication(PARENT_LOAN_ID, command));

        assertUnauthorizedApiCode(exception);
    }

    private void assertUnauthorizedApiCode(final NoAuthorizationException exception) {
        Response response = new NoAuthorizationExceptionMapper().toResponse(exception);
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
        ApiGlobalErrorResponse error = (ApiGlobalErrorResponse) response.getEntity();
        assertEquals("error.msg.not.authorized", error.getUserMessageGlobalisationCode());
    }
}
