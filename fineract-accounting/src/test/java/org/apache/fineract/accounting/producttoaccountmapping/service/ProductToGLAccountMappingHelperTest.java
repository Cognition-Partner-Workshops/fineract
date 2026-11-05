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
package org.apache.fineract.accounting.producttoaccountmapping.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.AccountingTestSupport;
import org.apache.fineract.accounting.common.AccountingConstants.CashAccountsForLoan;
import org.apache.fineract.accounting.common.AccountingConstants.LoanProductAccountingParams;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccountType;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMapping;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.accounting.producttoaccountmapping.exception.ProductToGLAccountMappingInvalidException;
import org.apache.fineract.accounting.producttoaccountmapping.exception.ProductToGLAccountMappingNotFoundException;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepository;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.PortfolioProductType;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductToGLAccountMappingHelperTest extends AccountingTestSupport {

    @Mock
    private GLAccountRepository accounts;
    @Mock
    private ProductToGLAccountMappingRepository mappings;
    @Mock
    private ChargeRepositoryWrapper charges;
    @Mock
    private GLAccountRepositoryWrapper accountWrapper;
    @Mock
    private PaymentTypeRepositoryWrapper paymentTypes;
    @Mock
    private CodeValueRepository codes;
    @Captor
    private ArgumentCaptor<ProductToGLAccountMapping> saved;
    private ProductToGLAccountMappingHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ProductToGLAccountMappingHelper(accounts, mappings, jsonHelper, charges, accountWrapper, paymentTypes, codes);
    }

    private GLAccount account(long id, GLAccountType type) {
        GLAccount account = mock(GLAccount.class);
        lenient().when(account.getId()).thenReturn(id);
        lenient().when(account.getType()).thenReturn(type.getValue());
        return account;
    }

    private JsonElement json(String value) {
        return JsonParser.parseString(value);
    }

    @Test
    void savesValidatedCoreMappingWithProductAndPlaceholder() {
        GLAccount asset = account(12, GLAccountType.ASSET);
        when(accountWrapper.findOneWithNotFoundDetection(12L)).thenReturn(asset);
        helper.saveProductToAccountMapping(json("{\"fund\":12}"), "fund", 9L, 1, GLAccountType.ASSET, PortfolioProductType.LOAN);
        verify(mappings).saveAndFlush(saved.capture());
        assertSame(asset, saved.getValue().getGlAccount());
        assertEquals(9L, saved.getValue().getProductId());
        assertEquals(PortfolioProductType.LOAN.getValue(), saved.getValue().getProductType());
        assertEquals(1, saved.getValue().getFinancialAccountType());
    }

    @Test
    void absentOptionalAccountDoesNotQueryOrSave() {
        helper.saveProductToAccountMapping(json("{}"), "fund", 9L, 1, GLAccountType.ASSET, PortfolioProductType.LOAN);
        helper.mergeProductToAccountMappingChanges(json("{}"), "fund", 9L, 1, "fund", new HashMap<>(), GLAccountType.ASSET,
                PortfolioProductType.LOAN);
        verifyNoInteractions(accounts, mappings, accountWrapper);
    }

    @Test
    void rejectsWrongTypeBeforePersisting() {
        GLAccount liability = account(12, GLAccountType.LIABILITY);
        when(accountWrapper.findOneWithNotFoundDetection(12L)).thenReturn(liability);
        ProductToGLAccountMappingInvalidException error = assertThrows(ProductToGLAccountMappingInvalidException.class,
                () -> helper.getAccountByIdAndType("fund", GLAccountType.ASSET, 12L));
        assertEquals("error.msg.fund.invalid.account.type", error.getGlobalisationMessageCode());
        verifyNoInteractions(mappings);
    }

    @Test
    void acceptsAnyAllowedFeeTypeAndRejectsUnlistedType() {
        GLAccount liability = account(12, GLAccountType.LIABILITY);
        when(accountWrapper.findOneWithNotFoundDetection(12L)).thenReturn(liability);
        assertSame(liability, helper.getAccountByIdAndType("fee", List.of(GLAccountType.INCOME, GLAccountType.LIABILITY), 12L));
        assertThrows(ProductToGLAccountMappingInvalidException.class,
                () -> helper.getAccountByIdAndType("fee", List.of(GLAccountType.ASSET), 12L));
    }

    @Test
    void mergeChangesOnlyDifferentAccountAndRecordsChange() {
        GLAccount old = account(11, GLAccountType.ASSET);
        GLAccount replacement = account(12, GLAccountType.ASSET);
        ProductToGLAccountMapping existing = new ProductToGLAccountMapping().setGlAccount(old);
        when(mappings.findCoreProductToFinAccountMapping(9L, PortfolioProductType.LOAN.getValue(), 1)).thenReturn(existing);
        when(accountWrapper.findOneWithNotFoundDetection(12L)).thenReturn(replacement);
        Map<String, Object> changes = new HashMap<>();
        helper.mergeProductToAccountMappingChanges(json("{\"fund\":11}"), "fund", 9L, 1, "fund", changes, GLAccountType.ASSET,
                PortfolioProductType.LOAN);
        verify(mappings, never()).saveAndFlush(any());
        assertTrue(changes.isEmpty());
        helper.mergeProductToAccountMappingChanges(json("{\"fund\":12}"), "fund", 9L, 1, "fund", changes, GLAccountType.ASSET,
                PortfolioProductType.LOAN);
        verify(mappings).saveAndFlush(existing);
        assertSame(replacement, existing.getGlAccount());
        assertEquals(Map.of("fund", 12L), changes);
    }

    @Test
    void missingRequiredMappingFailsButOptionalMappingIsCreated() {
        GLAccount asset = account(12, GLAccountType.ASSET);
        when(accountWrapper.findOneWithNotFoundDetection(12L)).thenReturn(asset);
        assertThrows(ProductToGLAccountMappingNotFoundException.class,
                () -> helper.mergeProductToAccountMappingChanges(json("{\"fund\":12}"), "fund", 9L, 1, "fund", new HashMap<>(),
                        GLAccountType.ASSET, PortfolioProductType.LOAN));
        helper.mergeProductToAccountMappingChanges(json("{\"interestReceivableAccountId\":12}"), "interestReceivableAccountId", 9L, 1,
                "interest", new HashMap<>(), GLAccountType.ASSET, PortfolioProductType.LOAN);
        verify(mappings).saveAndFlush(saved.capture());
        assertEquals(9L, saved.getValue().getProductId());
        assertEquals(12L, saved.getValue().getGlAccount().getId());
    }

    @Test
    void createOrMergePersistsNewAndChangedMappingsButNotIdenticalOnes() {
        GLAccount replacement = account(12, GLAccountType.ASSET);
        when(accountWrapper.findOneWithNotFoundDetection(12L)).thenReturn(replacement);
        Map<String, Object> changes = new HashMap<>();
        helper.createOrmergeProductToAccountMappingChanges(json("{\"fund\":12}"), "fund", 9L, 1, changes, GLAccountType.ASSET,
                PortfolioProductType.LOAN);
        verify(mappings).saveAndFlush(saved.capture());
        assertEquals(Map.of("fund", 12L), changes);
        assertEquals(1, saved.getValue().getFinancialAccountType());
        ProductToGLAccountMapping existing = new ProductToGLAccountMapping().setGlAccount(account(11, GLAccountType.ASSET));
        when(mappings.findCoreProductToFinAccountMapping(9L, PortfolioProductType.LOAN.getValue(), 1)).thenReturn(existing);
        helper.createOrmergeProductToAccountMappingChanges(json("{\"fund\":12}"), "fund", 9L, 1, changes, GLAccountType.ASSET,
                PortfolioProductType.LOAN);
        verify(mappings).saveAndFlush(existing);
        changes.clear();
        helper.createOrmergeProductToAccountMappingChanges(json("{\"fund\":12}"), "fund", 9L, 1, changes, GLAccountType.ASSET,
                PortfolioProductType.LOAN);
        assertTrue(changes.isEmpty());
        verify(mappings, times(2)).saveAndFlush(any());
    }

    @Test
    void paymentChannelUpdateReplacesRemovesAndAddsMappings() {
        PaymentType first = mock(PaymentType.class);
        PaymentType removed = mock(PaymentType.class);
        PaymentType added = mock(PaymentType.class);
        when(first.getId()).thenReturn(1L);
        when(removed.getId()).thenReturn(2L);
        GLAccount replacement = account(12, GLAccountType.ASSET);
        ProductToGLAccountMapping retained = new ProductToGLAccountMapping().setPaymentType(first)
                .setGlAccount(account(11, GLAccountType.ASSET));
        ProductToGLAccountMapping deleted = new ProductToGLAccountMapping().setPaymentType(removed);
        when(mappings.findAllPaymentTypeToFundSourceMappings(9L, PortfolioProductType.LOAN.getValue()))
                .thenReturn(List.of(retained, deleted));
        when(paymentTypes.findOneWithNotFoundDetection(3L)).thenReturn(added);
        when(accountWrapper.findOneWithNotFoundDetection(12L)).thenReturn(replacement);
        String input = "{\"paymentChannelToFundSourceMappings\":[{\"paymentTypeId\":1,\"fundSourceAccountId\":12},{\"paymentTypeId\":3,\"fundSourceAccountId\":12}]}";
        Map<String, Object> changes = new HashMap<>();
        helper.updatePaymentChannelToFundSourceMappings(command(input), json(input), 9L, changes, PortfolioProductType.LOAN);
        verify(mappings).delete(deleted);
        verify(mappings, times(2)).saveAndFlush(saved.capture());
        assertSame(replacement, retained.getGlAccount());
        ProductToGLAccountMapping created = saved.getAllValues().get(1);
        assertSame(added, created.getPaymentType());
        assertSame(replacement, created.getGlAccount());
        assertEquals(9L, created.getProductId());
        assertTrue(changes.containsKey("paymentChannelToFundSourceMappings"));
    }

    @Test
    void emptyPaymentArrayClearsMappingsButMissingArrayDoesNot() {
        List<ProductToGLAccountMapping> existing = List.of(new ProductToGLAccountMapping());
        when(mappings.findAllPaymentTypeToFundSourceMappings(9L, PortfolioProductType.LOAN.getValue())).thenReturn(existing);
        helper.updatePaymentChannelToFundSourceMappings(command("{}"), json("{}"), 9L, null, PortfolioProductType.LOAN);
        verify(mappings, never()).deleteAll(any());
        String input = "{\"paymentChannelToFundSourceMappings\":[]}";
        helper.updatePaymentChannelToFundSourceMappings(command(input), json(input), 9L, null, PortfolioProductType.LOAN);
        verify(mappings).deleteAll(existing);
    }

    @Test
    void feeCanMapToLiabilityAndPenaltyMustMapToIncome() {
        Charge fee = mock(Charge.class);
        Charge penalty = mock(Charge.class);
        when(charges.findOneWithNotFoundDetection(1L)).thenReturn(fee);
        when(charges.findOneWithNotFoundDetection(2L)).thenReturn(penalty);
        GLAccount liability = account(12, GLAccountType.LIABILITY);
        GLAccount income = account(13, GLAccountType.INCOME);
        when(accountWrapper.findOneWithNotFoundDetection(12L)).thenReturn(liability);
        when(accountWrapper.findOneWithNotFoundDetection(13L)).thenReturn(income);
        String input = "{\"feeToIncomeAccountMappings\":[{\"chargeId\":1,\"incomeAccountId\":12}],\"penaltyToIncomeAccountMappings\":[{\"chargeId\":2,\"incomeAccountId\":13}]}";
        helper.saveChargesToGLAccountMappings(command(input), json(input), 9L, null, PortfolioProductType.LOAN, false);
        helper.saveChargesToGLAccountMappings(command(input), json(input), 9L, null, PortfolioProductType.LOAN, true);
        verify(mappings, times(2)).saveAndFlush(saved.capture());
        assertSame(fee, saved.getAllValues().get(0).getCharge());
        assertSame(liability, saved.getAllValues().get(0).getGlAccount());
        assertEquals(CashAccountsForLoan.INCOME_FROM_FEES.getValue(), saved.getAllValues().get(0).getFinancialAccountType());
        assertSame(penalty, saved.getAllValues().get(1).getCharge());
        assertEquals(CashAccountsForLoan.INCOME_FROM_PENALTIES.getValue(), saved.getAllValues().get(1).getFinancialAccountType());
    }

    @ParameterizedTest
    @EnumSource(value = CashAccountsForLoan.class, names = { "CHARGE_OFF_EXPENSE", "LOSSES_WRITTEN_OFF" })
    void savesReasonMappingsWithCorrectAssociation(CashAccountsForLoan type) {
        GLAccount expense = account(12, GLAccountType.EXPENSE);
        CodeValue reason = mock(CodeValue.class);
        when(accounts.findById(12L)).thenReturn(Optional.of(expense));
        when(codes.findById(7L)).thenReturn(Optional.of(reason));
        LoanProductAccountingParams array = type == CashAccountsForLoan.CHARGE_OFF_EXPENSE
                ? LoanProductAccountingParams.CHARGE_OFF_REASON_TO_EXPENSE_ACCOUNT_MAPPINGS
                : LoanProductAccountingParams.WRITE_OFF_REASON_TO_EXPENSE_ACCOUNT_MAPPINGS;
        LoanProductAccountingParams id = type == CashAccountsForLoan.CHARGE_OFF_EXPENSE
                ? LoanProductAccountingParams.CHARGE_OFF_REASON_CODE_VALUE_ID
                : LoanProductAccountingParams.WRITE_OFF_REASON_CODE_VALUE_ID;
        JsonElement element = json("{\"" + array.getValue() + "\":[{\"" + id.getValue() + "\":7,\""
                + LoanProductAccountingParams.EXPENSE_GL_ACCOUNT_ID.getValue() + "\":12}]}");
        helper.saveReasonToGLAccountMappings(command(element.toString()), element, 9L, new HashMap<>(), PortfolioProductType.LOAN, array,
                id, type);
        verify(mappings).saveAndFlush(saved.capture());
        assertSame(expense, saved.getValue().getGlAccount());
        assertEquals(type.getValue(), saved.getValue().getFinancialAccountType());
        assertSame(reason, type == CashAccountsForLoan.CHARGE_OFF_EXPENSE ? saved.getValue().getChargeOffReason()
                : saved.getValue().getWriteOffReason());
    }

    @Test
    void databaseValidationCollectsMissingReasonsAndWrongAccountTypes() {
        List<ApiParameterError> errors = new ArrayList<>();
        var objects = List.of(json("{\"writeOffReasonCodeValueId\":7,\"expenseAccountId\":12}").getAsJsonObject());
        helper.validateWriteOffMappingsInDatabase(errors, objects);
        helper.validateGLAccountInDatabase(errors, objects);
        assertEquals(2, errors.size());
        assertEquals("validation.msg.writeoffreason.invalid", errors.get(0).getUserMessageGlobalisationCode());
        assertEquals("validation.msg.glaccount.not.found", errors.get(1).getUserMessageGlobalisationCode());
    }

    @Test
    void missingChargeOffAndClassificationAreRejectedWithCodes() {
        var missingReason = List.of(json("{\"chargeOffReasonCodeValueId\":7}").getAsJsonObject());
        PlatformApiDataValidationException error = assertThrows(PlatformApiDataValidationException.class,
                () -> helper.validateChargeOffMappingsInDatabase(new ArrayList<>(), missingReason));
        assertEquals("validation.msg.chargeoffreason.invalid", error.getErrors().get(0).getUserMessageGlobalisationCode());
        var classification = List.of(json("{\"classificationCodeValueId\":7,\"incomeAccountId\":12}").getAsJsonObject());
        error = assertThrows(PlatformApiDataValidationException.class,
                () -> helper.validateClassificationMappingsInDatabase(classification, "CapitalizedIncome"));
        assertEquals(2, error.getErrors().size());
        assertEquals("validation.msg.classification.invalid", error.getErrors().get(0).getUserMessageGlobalisationCode());
    }
}
