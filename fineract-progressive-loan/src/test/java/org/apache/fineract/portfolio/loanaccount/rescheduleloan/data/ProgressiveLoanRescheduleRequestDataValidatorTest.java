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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.RescheduleLoansApiConstants;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgressiveLoanRescheduleRequestDataValidatorTest {

    @Mock
    private DataValidatorBuilder dataValidatorBuilder;

    @Mock
    private LoanRepaymentScheduleInstallment installment;

    @Mock
    private FromJsonHelper fromJsonHelper;

    @Mock
    private LoanRescheduleRequestRepository loanRescheduleRequestRepository;

    private ProgressiveLoanRescheduleRequestDataValidator progressiveLoanRescheduleRequestDataValidator;

    @BeforeEach
    void setUp() {
        progressiveLoanRescheduleRequestDataValidator = new ProgressiveLoanRescheduleRequestDataValidator(fromJsonHelper,
                loanRescheduleRequestRepository);
        when(dataValidatorBuilder.reset()).thenReturn(dataValidatorBuilder);
        when(dataValidatorBuilder.parameter(anyString())).thenReturn(dataValidatorBuilder);
    }

    @Test
    void shouldFailWhenInstallmentIsNull() {
        progressiveLoanRescheduleRequestDataValidator.validateReschedulingInstallment(dataValidatorBuilder, null);

        verify(dataValidatorBuilder).reset();
        verify(dataValidatorBuilder).parameter(RescheduleLoansApiConstants.rescheduleFromDateParamName);
        verify(dataValidatorBuilder).failWithCode("repayment.schedule.installment.does.not.exist",
                "Repayment schedule installment does not exist");
    }

    @Test
    void shouldNotFailWhenInstallmentIsNotNull() {
        assertDoesNotThrow(
                () -> progressiveLoanRescheduleRequestDataValidator.validateReschedulingInstallment(dataValidatorBuilder, installment));

        verify(dataValidatorBuilder, never()).failWithCode(anyString(), anyString());
    }

    @Test
    void rejectsUnsupportedRejectAction() {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> progressiveLoanRescheduleRequestDataValidator.validateForRejectAction(mock(JsonCommand.class), null));
        assertEquals("Nothing to override here", exception.getMessage());
    }

    @Test
    void createActionValidatesProgressiveParametersBeforeReportingMissingValues() {
        JsonCommand command = mock(JsonCommand.class);
        when(command.json()).thenReturn("{}");
        when(command.parsedJson()).thenReturn(JsonParser.parseString("{}"));
        Loan loan = mock(Loan.class);
        LoanProduct product = mock(LoanProduct.class);
        LoanProductRelatedDetail detail = mock(LoanProductRelatedDetail.class);
        LoanStatus status = mock(LoanStatus.class);
        when(loan.getDisbursementDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(loan.getLoanProduct()).thenReturn(product);
        when(loan.getLoanProductRelatedDetail()).thenReturn(detail);
        when(loan.getStatus()).thenReturn(status);
        when(status.isActive()).thenReturn(true);
        when(product.getMaxNumberOfRepayments()).thenReturn(null);

        assertThrows(org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException.class,
                () -> progressiveLoanRescheduleRequestDataValidator.validateForCreateAction(command, loan));
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"extraTerms\":2", "\"newInterestRate\":6", "\"adjustedDueDate\":\"2024-02-15\"" })
    void acceptsSingleProgressiveRescheduleOperation(String operation) {
        Loan loan = activeLoan();
        ProgressiveLoanRescheduleRequestDataValidator validator = realValidator();
        assertDoesNotThrow(() -> validator.validateForCreateAction(createCommand(operation), loan));
        if (operation.contains("adjustedDueDate")) {
            verify(loan).fetchLoanRepaymentScheduleInstallmentByDueDate(LocalDate.of(2024, 2, 1));
        } else {
            verify(loan).getRelatedRepaymentScheduleInstallment(LocalDate.of(2024, 2, 1));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"graceOnPrincipal\":1", "\"graceOnInterest\":1", "\"emi\":50", "\"extraTerms\":-1", "\"newInterestRate\":-1",
            "\"extraTerms\":2,\"newInterestRate\":6" })
    void rejectsUnsupportedOrInvalidRescheduleOperations(String operation) {
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> realValidator().validateForCreateAction(createCommand(operation), activeLoan()));
        assertTrue(!exception.getErrors().isEmpty());
    }

    @Test
    void rejectsInterestOutsideProductBoundsAndExtraTermsBeyondMaximum() {
        Loan loan = activeLoan();
        when(loan.getLoanProduct().getMinNominalInterestRatePerPeriod()).thenReturn(BigDecimal.valueOf(5));
        when(loan.getLoanProduct().getMaxNominalInterestRatePerPeriod()).thenReturn(BigDecimal.valueOf(10));
        PlatformApiDataValidationException below = assertThrows(PlatformApiDataValidationException.class,
                () -> realValidator().validateForCreateAction(createCommand("\"newInterestRate\":4"), loan));
        assertTrue(below.getErrors().stream().anyMatch(error -> "newInterestRate".equals(error.getParameterName())));
        assertThrows(PlatformApiDataValidationException.class,
                () -> realValidator().validateForCreateAction(createCommand("\"newInterestRate\":11"), loan));
        when(loan.getLoanProduct().getMaxNumberOfRepayments()).thenReturn(8);
        when(loan.getLoanProductRelatedDetail().getNumberOfRepayments()).thenReturn(6);
        assertThrows(PlatformApiDataValidationException.class,
                () -> realValidator().validateForCreateAction(createCommand("\"extraTerms\":3"), loan));
        assertDoesNotThrow(() -> realValidator().validateForCreateAction(createCommand("\"extraTerms\":2"), loan));
    }

    @Test
    void rejectsDuplicateInterestRateChangeAndMissingInstallments() {
        Loan loan = activeLoan();
        when(loanRescheduleRequestRepository
                .exists(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<LoanRescheduleRequest>>any()))
                .thenReturn(true);
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> realValidator().validateForCreateAction(createCommand("\"newInterestRate\":6"), loan));
        when(loan.fetchLoanRepaymentScheduleInstallmentByDueDate(LocalDate.of(2024, 2, 1))).thenReturn(null);
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> realValidator().validateForCreateAction(createCommand("\"adjustedDueDate\":\"2024-02-15\""), loan));
        assertTrue(exception.getErrors().stream()
                .anyMatch(error -> error.getUserMessageGlobalisationCode().endsWith("repayment.schedule.installment.does.not.exist")));
    }

    @Test
    void approvesPendingDateChangeAndRejectsInactiveLoan() {
        Loan loan = activeLoan();
        LoanRescheduleRequest request = LoanRescheduleRequest.instance(loan, LoanStatus.SUBMITTED_AND_PENDING_APPROVAL.getValue(), 1,
                LocalDate.of(2024, 2, 1), false, null, null, LocalDate.of(2024, 1, 15), null, null, null, null, null);
        JsonCommand command = jsonCommand("{\"approvedOnDate\":\"2024-01-16\",\"dateFormat\":\"yyyy-MM-dd\",\"locale\":\"en\"}");
        assertDoesNotThrow(() -> realValidator().validateForApproveAction(command, request));
        when(loan.getStatus()).thenReturn(LoanStatus.APPROVED);
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> realValidator().validateForApproveAction(command, request));
        assertTrue(
                exception.getErrors().stream().anyMatch(error -> error.getUserMessageGlobalisationCode().endsWith("loan.is.not.active")));
    }

    private ProgressiveLoanRescheduleRequestDataValidator realValidator() {
        return new ProgressiveLoanRescheduleRequestDataValidator(new FromJsonHelper(), loanRescheduleRequestRepository);
    }

    private Loan activeLoan() {
        Loan loan = mock(Loan.class, RETURNS_DEEP_STUBS);
        when(loan.getDisbursementDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getLoanProduct().getMaxNumberOfRepayments()).thenReturn(null);
        when(loan.getLoanProduct().getMinNominalInterestRatePerPeriod()).thenReturn(null);
        when(loan.getLoanProduct().getMaxNominalInterestRatePerPeriod()).thenReturn(null);
        when(loan.getLoanCharges()).thenReturn(Set.of());
        return loan;
    }

    private JsonCommand createCommand(String operation) {
        return jsonCommand("{\"submittedOnDate\":\"2024-01-15\",\"rescheduleFromDate\":\"2024-02-01\",\"rescheduleReasonId\":1,"
                + "\"dateFormat\":\"yyyy-MM-dd\",\"locale\":\"en\"," + operation + "}");
    }

    private JsonCommand jsonCommand(String json) {
        JsonCommand command = mock(JsonCommand.class);
        when(command.json()).thenReturn(json);
        when(command.parsedJson()).thenReturn(JsonParser.parseString(json));
        return command;
    }
}
