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
package org.apache.fineract.accounting.glaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.AccountingTestSupport;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.glaccount.domain.GLAccountType;
import org.apache.fineract.accounting.glaccount.exception.GLAccountDisableException;
import org.apache.fineract.accounting.glaccount.exception.GLAccountDuplicateException;
import org.apache.fineract.accounting.glaccount.exception.GLAccountInvalidDeleteException;
import org.apache.fineract.accounting.glaccount.exception.GLAccountInvalidParentException;
import org.apache.fineract.accounting.glaccount.exception.GLAccountInvalidUpdateException;
import org.apache.fineract.accounting.glaccount.exception.GLAccountNotFoundException;
import org.apache.fineract.accounting.glaccount.exception.InvalidParentGLAccountHeadException;
import org.apache.fineract.accounting.glaccount.serialization.GLAccountCommandFromApiJsonDeserializer;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class GLAccountWritePlatformServiceTest extends AccountingTestSupport {

    @Mock
    private GLAccountRepository accounts;
    @Mock
    private JournalEntryRepository entries;
    @Mock
    private ProductToGLAccountMappingRepository mappings;
    @Mock
    private CodeValueRepositoryWrapper codes;
    @Mock
    private JdbcTemplate jdbc;
    @Captor
    private ArgumentCaptor<GLAccount> saved;
    private GLAccountWritePlatformServiceJpaRepositoryImpl service;

    @BeforeEach
    void setUp() {
        service = new GLAccountWritePlatformServiceJpaRepositoryImpl(accounts, entries, mappings,
                new GLAccountCommandFromApiJsonDeserializer(jsonHelper), codes, jdbc);
    }

    private String createJson(String extra) {
        return "{\"name\":\"Cash\",\"glCode\":\"1000\",\"type\":1,\"usage\":1,\"manualEntriesAllowed\":true" + extra + "}";
    }

    @Test
    void createsAccountWithCodeClassificationAndGeneratedHierarchy() {
        service.createGLAccount(command(createJson("")));
        verify(accounts, times(2)).saveAndFlush(saved.capture());
        GLAccount account = saved.getValue();
        assertEquals("Cash", account.getName());
        assertEquals("1000", account.getGlCode());
        assertEquals(GLAccountType.ASSET.getValue(), account.getType());
        assertTrue(account.isDetailAccount());
        assertNull(account.getParent());
        assertNotNull(account.getHierarchy());
    }

    @Test
    void detailAccountCannotBeUsedAsParent() {
        GLAccount parent = mock(GLAccount.class);
        when(parent.isDetailAccount()).thenReturn(true);
        when(accounts.findById(7L)).thenReturn(Optional.of(parent));
        assertThrows(GLAccountInvalidParentException.class, () -> service.createGLAccount(command(createJson(",\"parentId\":7"))));
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void selfParentIsRejectedBeforeLoadingAccount() {
        assertThrows(InvalidParentGLAccountHeadException.class, () -> service.updateGLAccount(7L, command("{\"parentId\":7}")));
        verifyNoInteractions(accounts);
    }

    @Test
    void cannotDisableAccountAttachedToProduct() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);
        assertThrows(GLAccountDisableException.class, () -> service.updateGLAccount(7L, command("{\"disabled\":true}")));
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void missingAccountIsRejectedForUpdateAndDelete() {
        assertThrows(GLAccountNotFoundException.class, () -> service.updateGLAccount(7L, command("{\"name\":\"new\"}")));
        assertThrows(GLAccountNotFoundException.class, () -> service.deleteGLAccount(7L));
        verify(accounts, never()).delete(any(GLAccount.class));
    }

    @Test
    void unchangedUpdateLeavesEntityIntactAndDoesNotSave() {
        GLAccount account = new GLAccount().setName("Cash").setGlCode("1000").setType(GLAccountType.ASSET.getValue()).setUsage(1)
                .setDescription("Opening cash");
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        CommandProcessingResult result = service.updateGLAccount(7L, command("{\"name\":\"Cash\",\"glCode\":\"1000\"}"));
        assertEquals("Cash", account.getName());
        assertEquals("1000", account.getGlCode());
        assertEquals("Opening cash", account.getDescription());
        assertEquals(GLAccountType.ASSET.getValue(), account.getType());
        assertTrue(account.isDetailAccount());
        assertTrue(account.isManualEntriesAllowed());
        assertNull(result.getChanges());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void updateChangesEntityFieldsAndReturnsOnlyChangedProperties() {
        GLAccount account = new GLAccount().setName("Cash").setGlCode("1000").setType(GLAccountType.ASSET.getValue()).setUsage(1)
                .setDescription("Opening cash");
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        CommandProcessingResult result = service.updateGLAccount(7L, command(
                "{\"name\":\"renamed\",\"glCode\":\"2000\",\"description\":\"Updated cash\",\"manualEntriesAllowed\":false,\"type\":1}"));
        assertEquals("renamed", account.getName());
        assertEquals("2000", account.getGlCode());
        assertEquals("Updated cash", account.getDescription());
        assertFalse(account.isManualEntriesAllowed());
        assertEquals(GLAccountType.ASSET.getValue(), account.getType());
        assertTrue(account.isDetailAccount());
        assertEquals(Map.of("name", "renamed", "glCode", "2000", "description", "Updated cash", "manualEntriesAllowed", false),
                result.getChanges());
        verify(accounts).saveAndFlush(account);
    }

    @Test
    void detailToHeaderTransitionIsBlockedByExistingEntries() {
        GLAccount account = new GLAccount().setName("Cash").setUsage(1);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(entries.exists(ArgumentMatchers.<Specification<org.apache.fineract.accounting.journalentry.domain.JournalEntry>>any()))
                .thenReturn(true);
        GLAccountInvalidUpdateException error = assertThrows(GLAccountInvalidUpdateException.class,
                () -> service.updateGLAccount(7L, command("{\"name\":\"Cash\",\"usage\":2}")));
        assertEquals("error.msg.glaccount.glcode.invalid.update.transactions.logged", error.getGlobalisationMessageCode());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void headerWithChildrenCannotBeDeleted() {
        GLAccount account = mock(GLAccount.class);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(account.isHeaderAccount()).thenReturn(true);
        when(account.getChildren()).thenReturn(List.of(mock(GLAccount.class)));
        GLAccountInvalidDeleteException error = assertThrows(GLAccountInvalidDeleteException.class, () -> service.deleteGLAccount(7L));
        assertTrue(error.getGlobalisationMessageCode().contains("children"));
        verify(accounts, never()).delete(any(GLAccount.class));
    }

    @Test
    void postedOrMappedAccountsCannotBeDeletedAndUnusedAccountCan() {
        GLAccount account = mock(GLAccount.class);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(entries.exists(ArgumentMatchers.<Specification<org.apache.fineract.accounting.journalentry.domain.JournalEntry>>any()))
                .thenReturn(true, false, false);
        assertThrows(GLAccountInvalidDeleteException.class, () -> service.deleteGLAccount(7L));
        when(mappings.exists(
                ArgumentMatchers.<Specification<org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMapping>>any()))
                .thenReturn(true, false);
        assertThrows(GLAccountInvalidDeleteException.class, () -> service.deleteGLAccount(7L));
        verify(accounts, never()).delete(any(GLAccount.class));
        service.deleteGLAccount(7L);
        verify(accounts).delete(account);
    }

    @Test
    void duplicateCodeIsTranslated() {
        when(accounts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("acc_gl_code"));
        assertThrows(GLAccountDuplicateException.class, () -> service.createGLAccount(command(createJson(""))));
    }
}
