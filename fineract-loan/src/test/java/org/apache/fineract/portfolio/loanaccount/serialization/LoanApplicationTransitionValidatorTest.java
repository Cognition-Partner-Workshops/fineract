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
package org.apache.fineract.portfolio.loanaccount.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanEvent;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanMoneyTestSupport;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LoanApplicationTransitionValidatorTest extends LoanMoneyTestSupport {

    private final FromJsonHelper helper = new FromJsonHelper();
    private final LoanLifecycleStateMachine machine = mock(LoanLifecycleStateMachine.class);
    private final LoanApplicationTransitionValidator validator = new LoanApplicationTransitionValidator(helper, machine);

    static Stream<Arguments> approvals() {
        return Stream.of(Arguments.of("{}", "approvedOnDate", "cannot.be.blank"),
                Arguments.of("{\"approvedLoanAmount\":0,\"locale\":\"en\"}", "approvedLoanAmount", "not.greater.than.zero"),
                Arguments.of("{\"netDisbursalAmount\":-1,\"locale\":\"en\"}", "netDisbursalAmount", "not.greater.than.zero"),
                Arguments.of("{\"note\":\"" + "x".repeat(1001) + "\"}", "note", "exceeds.max.length"));
    }

    @ParameterizedTest
    @MethodSource("approvals")
    void invalidApprovalReportsExpectedCode(String json, String parameter, String code) {
        var exception = assertThrows(PlatformApiDataValidationException.class, () -> validator.validateApproval(json));
        assertTrue(
                exception.getErrors().stream().anyMatch(
                        error -> parameter.equals(error.getParameterName()) && error.getUserMessageGlobalisationCode().contains(code)),
                () -> exception.getErrors().toString());
    }

    @Test
    void approvalValidBlankAndUnsupportedRequests() {
        assertDoesNotThrow(() -> validator.validateApproval(
                "{\"approvedOnDate\":\"29 February 2024\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\",\"approvedLoanAmount\":1000}"));
        assertThrows(InvalidJsonException.class, () -> validator.validateApproval(" "));
        assertThrows(UnsupportedParameterException.class, () -> validator.validateApproval("{\"unsupported\":1}"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { true, false })
    void rejectionAndWithdrawalValidateRequiredDateAndAllowValidPendingLoan(boolean rejection) {
        Loan loan = mock(Loan.class);
        JsonCommand command = mock(JsonCommand.class);
        when(command.json()).thenReturn("{}");
        var exception = assertThrows(PlatformApiDataValidationException.class, () -> {
            if (rejection) {
                validator.validateRejection(command, loan);
            } else {
                validator.validateApplicantWithdrawal(command, loan);
            }
        });
        String dateParameter = rejection ? "rejectedOnDate" : "withdrawnOnDate";
        assertTrue(exception.getErrors().stream().anyMatch(error -> dateParameter.equals(error.getParameterName())
                && error.getUserMessageGlobalisationCode().endsWith("cannot.be.blank")));
        when(command.json())
                .thenReturn("{\"" + dateParameter + "\":\"29 February 2024\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}");
        when(command.localDateValueOfParameterNamed(dateParameter)).thenReturn(dueDate);
        when(loan.getSubmittedOnDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(loan.getLoanStatus()).thenReturn(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        when(loan.isSubmittedAndPendingApproval()).thenReturn(true);
        when(machine.dryTransition(rejection ? LoanEvent.LOAN_REJECTED : LoanEvent.LOAN_WITHDRAWN, loan))
                .thenReturn(rejection ? LoanStatus.REJECTED : LoanStatus.WITHDRAWN_BY_CLIENT);
        assertDoesNotThrow(() -> {
            if (rejection) {
                validator.validateRejection(command, loan);
            } else {
                validator.validateApplicantWithdrawal(command, loan);
            }
        });
    }

    @Test
    void rejectionRejectsNonPendingAccountWithSpecificError() {
        var exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateAccountStatus(mock(Loan.class), LoanEvent.LOAN_REJECTED));
        assertTrue(exception.getErrors().stream().anyMatch(error -> error.getUserMessageGlobalisationCode()
                .equals("error.msg.loan.reject.account.is.not.submitted.pending.approval.state")));
    }
}
