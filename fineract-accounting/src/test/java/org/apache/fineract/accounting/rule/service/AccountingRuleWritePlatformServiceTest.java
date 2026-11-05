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
package org.apache.fineract.accounting.rule.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.AccountingTestSupport;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.rule.domain.AccountingRule;
import org.apache.fineract.accounting.rule.domain.AccountingRuleRepository;
import org.apache.fineract.accounting.rule.domain.AccountingRuleRepositoryWrapper;
import org.apache.fineract.accounting.rule.exception.AccountingRuleDataException;
import org.apache.fineract.accounting.rule.exception.AccountingRuleDuplicateException;
import org.apache.fineract.accounting.rule.serialization.AccountingRuleCommandFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepository;
import org.apache.fineract.infrastructure.codes.exception.CodeValueNotFoundException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AccountingRuleWritePlatformServiceTest extends AccountingTestSupport {

    @Mock
    private AccountingRuleRepositoryWrapper wrapper;
    @Mock
    private AccountingRuleRepository repository;
    @Mock
    private GLAccountRepositoryWrapper accounts;
    @Mock
    private OfficeRepositoryWrapper offices;
    @Mock
    private CodeValueRepository codes;
    @Captor
    private ArgumentCaptor<AccountingRule> saved;
    private AccountingRuleWritePlatformServiceJpaRepositoryImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountingRuleWritePlatformServiceJpaRepositoryImpl(wrapper, repository, accounts, offices,
                new AccountingRuleCommandFromApiJsonDeserializer(jsonHelper), codes);
    }

    @Test
    void savesRuleWithResolvedOfficeAndDebitCreditAccounts() {
        Office office = mock(Office.class);
        GLAccount debit = mock(GLAccount.class);
        GLAccount credit = mock(GLAccount.class);
        when(offices.findOneWithNotFoundDetection(1L)).thenReturn(office);
        when(accounts.findOneWithNotFoundDetection(2L)).thenReturn(debit);
        when(accounts.findOneWithNotFoundDetection(3L)).thenReturn(credit);
        service.createAccountingRule(command(
                "{\"officeId\":1,\"name\":\"Cash transfer\",\"accountToDebit\":2,\"accountToCredit\":3,\"description\":\"transfer\"}"));
        verify(repository).saveAndFlush(saved.capture());
        AccountingRule rule = saved.getValue();
        assertEquals("Cash transfer", rule.getName());
        assertEquals("transfer", rule.getDescription());
        assertSame(office, rule.getOffice());
        assertSame(debit, rule.getAccountToDebit());
        assertSame(credit, rule.getAccountToCredit());
    }

    @Test
    void tagsAreDeduplicatedAndAssociatedWithCorrectSide() {
        CodeValue debit = mock(CodeValue.class);
        CodeValue credit = mock(CodeValue.class);
        when(codes.findById(2L)).thenReturn(Optional.of(debit));
        when(codes.findById(3L)).thenReturn(Optional.of(credit));
        service.createAccountingRule(command(
                "{\"officeId\":1,\"name\":\"Tagged\",\"debitTags\":[2,2],\"creditTags\":[3],\"allowMultipleDebitEntries\":true,\"allowMultipleCreditEntries\":true}"));
        verify(repository).saveAndFlush(saved.capture());
        AccountingRule rule = saved.getValue();
        assertEquals(1, rule.getAccountingTagRulesByType(JournalEntryType.DEBIT).size());
        assertEquals(1, rule.getAccountingTagRulesByType(JournalEntryType.CREDIT).size());
        assertSame(debit, rule.getAccountingTagRulesByType(JournalEntryType.DEBIT).iterator().next().getTagId());
        assertSame(credit, rule.getAccountingTagRulesByType(JournalEntryType.CREDIT).iterator().next().getTagId());
        assertTrue(rule.isAllowMultipleDebitEntries());
        assertTrue(rule.isAllowMultipleCreditEntries());
        verify(codes).findById(2L);
    }

    @Test
    void cannotCombineExplicitDebitAccountAndDebitTags() {
        assertThrows(AccountingRuleDataException.class, () -> service.createAccountingRule(
                command("{\"officeId\":1,\"name\":\"Invalid\",\"accountToDebit\":2,\"debitTags\":[2],\"accountToCredit\":3}")));
        verifyNoInteractions(repository, accounts);
    }

    @Test
    void nonexistentTagIsRejectedBeforeSaving() {
        assertThrows(CodeValueNotFoundException.class, () -> service
                .createAccountingRule(command("{\"officeId\":1,\"name\":\"Invalid\",\"debitTags\":[2],\"accountToCredit\":3}")));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateSavesOnlyChangedRule() {
        AccountingRule rule = mock(AccountingRule.class);
        when(wrapper.findOneWithNotFoundDetection(7L)).thenReturn(rule);
        when(rule.update(any())).thenReturn(new HashMap<>(), new HashMap<>(Map.of("name", "new")));
        service.updateAccountingRule(7L, command("{\"officeId\":1,\"name\":\"old\"}"));
        verify(repository, never()).saveAndFlush(any());
        service.updateAccountingRule(7L, command("{\"officeId\":1,\"name\":\"new\"}"));
        verify(repository).saveAndFlush(rule);
    }

    @Test
    void deleteUsesResolvedEntity() {
        AccountingRule rule = mock(AccountingRule.class);
        when(wrapper.findOneWithNotFoundDetection(7L)).thenReturn(rule);
        service.deleteAccountingRule(7L);
        verify(repository).delete(rule);
    }

    @Test
    void duplicateRuleNameIsTranslated() {
        when(accounts.findOneWithNotFoundDetection(anyLong())).thenReturn(mock(GLAccount.class));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("accounting_rule_name_unique"));
        assertThrows(AccountingRuleDuplicateException.class,
                () -> service.createAccountingRule(command("{\"officeId\":1,\"name\":\"duplicate\",\"accountToDebit\":2,\"accountToCredit\":3}")));
    }
}
