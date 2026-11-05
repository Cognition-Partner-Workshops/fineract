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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.accounting.AccountingTestSupport;
import org.apache.fineract.accounting.common.AccountingConstants.SavingProductAccountingParams;
import org.apache.fineract.accounting.common.AccountingConstants.SharesProductAccountingParams;
import org.apache.fineract.accounting.common.AccountingRuleType;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.domain.GLAccountType;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMapping;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepository;
import org.apache.fineract.portfolio.PortfolioProductType;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductMappingSpecializationsTest extends AccountingTestSupport {

    @Mock
    private GLAccountRepository accounts;
    @Mock
    private ProductToGLAccountMappingRepository mappings;
    @Mock
    private ChargeRepositoryWrapper charges;
    @Mock
    private GLAccountRepositoryWrapper wrapper;
    @Mock
    private PaymentTypeRepositoryWrapper payments;
    @Mock
    private CodeValueRepository codes;
    @Captor
    private ArgumentCaptor<ProductToGLAccountMapping> saved;
    private SavingsProductToGLAccountMappingHelper savings;
    private ShareProductToGLAccountMappingHelper shares;

    @BeforeEach
    void setUp() {
        savings = new SavingsProductToGLAccountMappingHelper(accounts, mappings, jsonHelper, charges, wrapper, payments, codes);
        shares = new ShareProductToGLAccountMappingHelper(accounts, mappings, jsonHelper, charges, wrapper, payments, codes);
    }

    @Test
    void shareCashChangesContainExactlyTheFourAccountRoles() {
        JsonObject json = new JsonObject();
        json.addProperty(SharesProductAccountingParams.SHARES_REFERENCE.getValue(), 11);
        json.addProperty(SharesProductAccountingParams.SHARES_SUSPENSE.getValue(), 12);
        json.addProperty(SharesProductAccountingParams.INCOME_FROM_FEES.getValue(), 13);
        json.addProperty(SharesProductAccountingParams.SHARES_EQUITY.getValue(), 14);
        assertEquals(Map.of("shareReferenceId", 11L, "shareSuspenseId", 12L, "incomeFromFeeAccountId", 13L, "shareEquityId", 14L),
                shares.populateChangesForNewSharesProductToGLAccountMappingCreation(json, AccountingRuleType.CASH_BASED));
        assertTrue(shares.populateChangesForNewSharesProductToGLAccountMappingCreation(json, AccountingRuleType.NONE).isEmpty());
        assertTrue(
                shares.populateChangesForNewSharesProductToGLAccountMappingCreation(json, AccountingRuleType.ACCRUAL_PERIODIC).isEmpty());
    }

    @Test
    void savingsAccrualChangesIncludeReceivablesAndInterestPayableButCashDoesNot() {
        JsonObject json = new JsonObject();
        for (SavingProductAccountingParams param : SavingProductAccountingParams.values()) {
            json.addProperty(param.getValue(), 17);
        }
        Map<String, Object> cash = savings.populateChangesForNewSavingsProductToGLAccountMappingCreation(json,
                AccountingRuleType.CASH_BASED);
        Map<String, Object> accrual = savings.populateChangesForNewSavingsProductToGLAccountMappingCreation(json,
                AccountingRuleType.ACCRUAL_PERIODIC);
        assertEquals(9, cash.size());
        assertEquals(12, accrual.size());
        assertEquals(17L, accrual.get(SavingProductAccountingParams.FEES_RECEIVABLE.getValue()));
        assertEquals(17L, accrual.get(SavingProductAccountingParams.PENALTIES_RECEIVABLE.getValue()));
        assertEquals(17L, accrual.get(SavingProductAccountingParams.INTEREST_PAYABLE.getValue()));
        assertFalse(cash.containsKey(SavingProductAccountingParams.FEES_RECEIVABLE.getValue()));
        assertTrue(accrual.entrySet().containsAll(cash.entrySet()));
        assertTrue(savings.populateChangesForNewSavingsProductToGLAccountMappingCreation(json, AccountingRuleType.NONE).isEmpty());
    }

    @Test
    void shareCashUpdatesValidateEachRoleAndSaveShareProductMappings() {
        JsonObject json = new JsonObject();
        String[] names = { "shareReferenceId", "incomeFromFeeAccountId", "shareSuspenseId", "shareEquityId" };
        GLAccountType[] types = { GLAccountType.ASSET, GLAccountType.INCOME, GLAccountType.LIABILITY, GLAccountType.EQUITY };
        for (int i = 0; i < names.length; i++) {
            long id = 20L + i;
            json.addProperty(names[i], id);
            GLAccount account = mock(GLAccount.class);
            when(account.getType()).thenReturn(types[i].getValue());
            when(wrapper.findOneWithNotFoundDetection(id)).thenReturn(account);
        }
        when(mappings.findCoreProductToFinAccountMapping(eq(9L), eq(PortfolioProductType.SHARES.getValue()), anyInt()))
                .thenAnswer(invocation -> new ProductToGLAccountMapping().setGlAccount(new GLAccount()));
        Map<String, Object> changes = new HashMap<>();
        shares.handleChangesToSharesProductToGLAccountMappings(9L, changes, json, AccountingRuleType.CASH_BASED);
        assertEquals(Map.of("shareReferenceId", 20L, "incomeFromFeeAccountId", 21L, "shareSuspenseId", 22L, "shareEquityId", 23L), changes);
        verify(mappings, times(4)).saveAndFlush(saved.capture());
        assertEquals(4, saved.getAllValues().stream().map(ProductToGLAccountMapping::getGlAccount).distinct().count());
    }

    @Test
    void accountingDisabledDoesNotChangeOrPersistMappings() {
        Map<String, Object> changes = new HashMap<>();
        shares.handleChangesToSharesProductToGLAccountMappings(9L, changes, new JsonObject(), AccountingRuleType.NONE);
        savings.handleChangesToSavingsProductToGLAccountMappings(9L, changes, new JsonObject(), AccountingRuleType.NONE);
        assertTrue(changes.isEmpty());
        verifyNoInteractions(mappings, wrapper);
    }
}
