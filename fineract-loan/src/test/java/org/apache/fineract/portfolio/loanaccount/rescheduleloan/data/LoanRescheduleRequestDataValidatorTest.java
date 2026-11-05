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
package org.apache.fineract.portfolio.loanaccount.rescheduleloan.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanMoneyTestSupport;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LoanRescheduleRequestDataValidatorTest extends LoanMoneyTestSupport {

    private final FromJsonHelper helper = new FromJsonHelper();
    private final LoanRescheduleRequestDataValidatorImpl validator = new LoanRescheduleRequestDataValidatorImpl(helper,
            mock(LoanRescheduleRequestDataValidator.class));
    private static final String VALID = "\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\","
            + "\"submittedOnDate\":\"29 February 2024\",\"rescheduleFromDate\":\"29 February 2024\",\"rescheduleReasonId\":1";

    private Loan loan() {
        Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getLoanProductRelatedDetail().getLoanScheduleType()).thenReturn(LoanScheduleType.CUMULATIVE);
        when(loan.getLoanRepaymentScheduleDetail().getAnnualNominalInterestRate()).thenReturn(new BigDecimal("12"));
        when(loan.getDisbursementDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(loan.getLoanCharges()).thenReturn(Set.of());
        var installment = installment(1, dueDate.minusMonths(1), dueDate);
        when(loan.fetchLoanRepaymentScheduleInstallmentByDueDate(dueDate)).thenReturn(installment);
        return loan;
    }

    private JsonCommand command(String json) {
        JsonCommand command = mock(JsonCommand.class);
        when(command.json()).thenReturn(json);
        when(command.parsedJson()).thenReturn(helper.parse(json));
        return command;
    }

    static Stream<Arguments> invalidChanges() {
        return Stream.of(Arguments.of("\"graceOnPrincipal\":0", "graceOnPrincipal", "not.greater.than.zero"),
                Arguments.of("\"graceOnInterest\":-1", "graceOnInterest", "not.greater.than.zero"),
                Arguments.of("\"extraTerms\":0", "extraTerms", "not.greater.than.zero"),
                Arguments.of("\"newInterestRate\":-1", "newInterestRate", "not.greater.than.zero"),
                Arguments.of("\"adjustedDueDate\":\"28 February 2024\"", "rescheduleFromDate", "adjustedDueDate.before.rescheduleFromDate"),
                Arguments.of("\"emi\":0", "emi", "not.greater.than.zero"),
                Arguments.of("\"rescheduleReasonComment\":\"" + "x".repeat(501) + "\"", "rescheduleReasonComment", "exceeds.max.length"));
    }

    @ParameterizedTest
    @MethodSource("invalidChanges")
    void invalidChangesReportParameterAndCode(String change, String parameter, String code) {
        var exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreateAction(command("{" + VALID + "," + change + "}"), loan()));
        assertTrue(
                exception.getErrors().stream().anyMatch(
                        error -> parameter.equals(error.getParameterName()) && error.getUserMessageGlobalisationCode().contains(code)),
                () -> exception.getErrors().toString());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "\"graceOnPrincipal\":2", "\"graceOnInterest\":2", "\"extraTerms\":3",
            "\"newInterestRate\":10", "\"adjustedDueDate\":\"01 March 2024\"" })
    void validRescheduleChangesPass(String change) {
        assertDoesNotThrow(() -> validator.validateForCreateAction(command("{" + VALID + "," + change + "}"), loan()));
    }

    @Test
    void inactiveLoanAndMissingInstallmentHaveDomainErrors() {
        Loan loan = loan();
        when(loan.getStatus()).thenReturn(LoanStatus.APPROVED);
        when(loan.fetchLoanRepaymentScheduleInstallmentByDueDate(dueDate)).thenReturn(null);
        var exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreateAction(command("{" + VALID + ",\"extraTerms\":2}"), loan));
        assertTrue(
                exception.getErrors().stream().anyMatch(error -> error.getUserMessageGlobalisationCode().endsWith("loan.is.not.active")));
        assertTrue(exception.getErrors().stream()
                .anyMatch(error -> error.getUserMessageGlobalisationCode().endsWith("repayment.schedule.installment.does.not.exist")));
    }
}
