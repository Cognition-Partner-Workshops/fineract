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
package org.apache.fineract.accounting.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import org.apache.fineract.accounting.AccountingTestSupport;
import org.apache.fineract.accounting.accrual.serialization.AccrualAccountingDataValidator;
import org.apache.fineract.accounting.common.AccountingConstants.FinancialActivity;
import org.apache.fineract.accounting.common.AccountingConstants.LoanProductAccountingParams;
import org.apache.fineract.accounting.common.AccountingConstants.SavingProductAccountingParams;
import org.apache.fineract.accounting.common.AccountingConstants.SharesProductAccountingParams;
import org.apache.fineract.accounting.financialactivityaccount.serialization.FinancialActivityAccountDataValidator;
import org.apache.fineract.accounting.journalentry.data.JournalEntryDataValidator;
import org.apache.fineract.accounting.producttoaccountmapping.serialization.ProductToGLAccountMappingFromApiJsonDeserializer;
import org.apache.fineract.accounting.rule.serialization.AccountingRuleCommandFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountingValidationTest extends AccountingTestSupport {

    private final ProductToGLAccountMappingFromApiJsonDeserializer mappings = new ProductToGLAccountMappingFromApiJsonDeserializer(
            jsonHelper);
    private final AccountingRuleCommandFromApiJsonDeserializer rules = new AccountingRuleCommandFromApiJsonDeserializer(jsonHelper);
    private final FinancialActivityAccountDataValidator activities = new FinancialActivityAccountDataValidator(jsonHelper);
    private final AccrualAccountingDataValidator accrual = new AccrualAccountingDataValidator(jsonHelper);

    private void assertParameter(PlatformApiDataValidationException error, String parameter) {
        assertTrue(error.getErrors().stream().anyMatch(e -> parameter.equals(e.getParameterName())), error.toString());
        assertEquals("validation.msg.validation.errors.exist", error.getGlobalisationMessageCode());
    }

    @Test
    void blankRequestsAreRejectedAcrossProductTypesAndValidators() {
        assertThrows(InvalidJsonException.class, () -> mappings.validateForLoanProductCreate(" "));
        assertThrows(InvalidJsonException.class, () -> mappings.validateForSavingsProductCreate("", DepositAccountType.SAVINGS_DEPOSIT));
        assertThrows(InvalidJsonException.class, () -> mappings.validateForShareProductCreate(""));
        assertThrows(InvalidJsonException.class, () -> rules.validateForCreate(""));
        assertThrows(InvalidJsonException.class, () -> activities.validateForCreate(""));
        assertThrows(InvalidJsonException.class, () -> accrual.validateLoanPeriodicAccrualData(""));
    }

    @Test
    void accountingNoneNeedsNoGlAccounts() {
        assertDoesNotThrow(() -> mappings.validateForLoanProductCreate("{\"accountingRule\":1}"));
        assertDoesNotThrow(() -> mappings.validateForSavingsProductCreate("{\"accountingRule\":1}", DepositAccountType.SAVINGS_DEPOSIT));
        assertDoesNotThrow(() -> mappings.validateForShareProductCreate("{\"accountingRule\":1}"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 2, 3, 4 })
    void loanAccountingRequiresPositiveGlMappings(int rule) {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> mappings.validateForLoanProductCreate("{\"accountingRule\":" + rule + "}"));
        assertParameter(error, "fundSourceAccountId");
        assertParameter(error, "loanPortfolioAccountId");
        if (rule != 2) {
            assertParameter(error, "receivableInterestAccountId");
        }
        JsonObject json = new JsonObject();
        json.addProperty("accountingRule", rule);
        for (LoanProductAccountingParams param : LoanProductAccountingParams.values()) {
            json.addProperty(param.getValue(), 12);
        }
        assertDoesNotThrow(() -> mappings.validateForLoanProductCreate(json.toString()));
        json.addProperty("fundSourceAccountId", 0);
        assertParameter(
                assertThrows(PlatformApiDataValidationException.class, () -> mappings.validateForLoanProductCreate(json.toString())),
                "fundSourceAccountId");
    }

    @ParameterizedTest
    @ValueSource(ints = { 2, 3 })
    void savingsAccountingValidatesCashAndAccrualAccounts(int rule) {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> mappings.validateForSavingsProductCreate("{\"accountingRule\":" + rule + "}", DepositAccountType.SAVINGS_DEPOSIT));
        assertParameter(error, rule == 2 ? "savingsControlAccountId" : SavingProductAccountingParams.FEES_RECEIVABLE.getValue());
        JsonObject json = new JsonObject();
        json.addProperty("accountingRule", rule);
        json.addProperty("isDormancyTrackingActive", true);
        for (SavingProductAccountingParams param : SavingProductAccountingParams.values()) {
            json.addProperty(param.getValue(), 12);
        }
        assertDoesNotThrow(() -> mappings.validateForSavingsProductCreate(json.toString(), DepositAccountType.SAVINGS_DEPOSIT));
        assertDoesNotThrow(() -> mappings.validateForSavingsProductCreate(json.toString(), DepositAccountType.FIXED_DEPOSIT));
        if (rule == 2) {
            json.remove(SavingProductAccountingParams.ESCHEAT_LIABILITY.getValue());
            assertParameter(
                    assertThrows(PlatformApiDataValidationException.class,
                            () -> mappings.validateForSavingsProductCreate(json.toString(), DepositAccountType.SAVINGS_DEPOSIT)),
                    SavingProductAccountingParams.ESCHEAT_LIABILITY.getValue());
        }
    }

    @Test
    void shareCashAccountingRequiresAllFourRoles() {
        assertParameter(assertThrows(PlatformApiDataValidationException.class,
                () -> mappings.validateForShareProductCreate("{\"accountingRule\":2}")), "shareReferenceId");
        JsonObject json = new JsonObject();
        json.addProperty("accountingRule", 2);
        for (SharesProductAccountingParams param : SharesProductAccountingParams.values()) {
            json.addProperty(param.getValue(), 12);
        }
        assertDoesNotThrow(() -> mappings.validateForShareProductCreate(json.toString()));
    }

    @Test
    void ruleCreateRequiresBothSidesAndValidTagIds() {
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> rules.validateForCreate("{\"officeId\":1,\"name\":\"Rule\"}"));
        assertTrue(error.getErrors().stream()
                .anyMatch(e -> "validation.msg.accountToCredit.or.creditTags.required".equals(e.getUserMessageGlobalisationCode())));
        assertTrue(error.getErrors().stream()
                .anyMatch(e -> "validation.msg.accountToDebit.or.debitTags.required".equals(e.getUserMessageGlobalisationCode())));
        assertDoesNotThrow(() -> rules.validateForCreate("{\"officeId\":1,\"name\":\"Rule\",\"debitTags\":[2],\"creditTags\":[3]}"));
        assertThrows(PlatformApiDataValidationException.class,
                () -> rules.validateForCreate("{\"officeId\":1,\"name\":\"Rule\",\"debitTags\":[0],\"creditTags\":[]}"));
    }

    @Test
    void ruleUpdateAcceptsPartialDataButValidatesLengthsAndIds() {
        assertDoesNotThrow(() -> rules.validateForUpdate("{\"description\":\"updated\"}"));
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> rules.validateForUpdate("{\"officeId\":0}")),
                "officeId");
        assertParameter(assertThrows(PlatformApiDataValidationException.class,
                () -> rules.validateForUpdate("{\"description\":\"" + "x".repeat(501) + "\"}")), "description");
    }

    @Test
    void financialActivitiesValidateEnumAndPositiveAccountWithPartialUpdates() {
        int activity = FinancialActivity.ASSET_TRANSFER.getValue();
        assertDoesNotThrow(() -> activities.validateForCreate("{\"financialActivityId\":" + activity + ",\"glAccountId\":12}"));
        assertDoesNotThrow(() -> activities.validateForUpdate("{\"glAccountId\":13}"));
        assertDoesNotThrow(() -> activities.validateForUpdate("{\"financialActivityId\":" + activity + "}"));
        assertParameter(assertThrows(PlatformApiDataValidationException.class, () -> activities.validateForCreate("{}")),
                "financialActivityId");
        assertParameter(assertThrows(PlatformApiDataValidationException.class,
                () -> activities.validateForUpdate("{\"financialActivityId\":-1,\"glAccountId\":0}")), "glAccountId");
    }

    @Test
    void accrualDateCannotExceedBusinessDate() {
        String prefix = "{\"locale\":\"en\",\"dateFormat\":\"yyyy-MM-dd\",\"tillDate\":\"";
        assertDoesNotThrow(() -> accrual.validateLoanPeriodicAccrualData(prefix + BUSINESS_DATE.minusDays(1) + "\"}"));
        assertDoesNotThrow(() -> accrual.validateLoanPeriodicAccrualData(prefix + BUSINESS_DATE + "\"}"));
        assertThrows(PlatformApiDataValidationException.class,
                () -> accrual.validateLoanPeriodicAccrualData(prefix + BUSINESS_DATE.plusDays(1) + "\"}"));
        assertThrows(PlatformApiDataValidationException.class, () -> accrual.validateLoanPeriodicAccrualData("{}"));
        assertDoesNotThrow(() -> accrual.throwExceptionIfValidationWarningsExist(List.of()));
    }

    @Test
    void runningBalanceOfficeIsOptionalButMustBePositiveIfSupplied() {
        JournalEntryDataValidator validator = new JournalEntryDataValidator(jsonHelper);
        assertDoesNotThrow(() -> validator.validateForUpdateRunningBalance(command("{}")));
        assertDoesNotThrow(() -> validator.validateForUpdateRunningBalance(command("{\"officeId\":12}")));
        assertParameter(assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdateRunningBalance(command("{\"officeId\":0}"))), "officeId");
    }

    @Test
    void unsupportedParametersAreRejectedInsteadOfSilentlyIgnored() {
        String input = "{\"unexpected\":1}";
        UnsupportedParameterException error = assertThrows(UnsupportedParameterException.class, () -> activities.validateForCreate(input));
        assertEquals(List.of("unexpected"), error.getUnsupportedParameters());
        assertThrows(UnsupportedParameterException.class, () -> rules.validateForCreate(input));
        assertThrows(UnsupportedParameterException.class, () -> accrual.validateLoanPeriodicAccrualData(input));
        assertThrows(UnsupportedParameterException.class,
                () -> new JournalEntryDataValidator(jsonHelper).validateForUpdateRunningBalance(command(input)));
    }
}
