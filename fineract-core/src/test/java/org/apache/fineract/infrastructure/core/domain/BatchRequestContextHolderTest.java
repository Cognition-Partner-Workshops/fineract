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
package org.apache.fineract.infrastructure.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;

class BatchRequestContextHolderTest {

    @Test
    void resetTransactionRemovesThreadLocalEntry() throws Exception {
        Field transactionField = BatchRequestContextHolder.class.getDeclaredField("batchTransaction");
        transactionField.setAccessible(true);
        ThreadLocal<?> transaction = (ThreadLocal<?>) transactionField.get(null);
        try {
            BatchRequestContextHolder.setTransaction(mock(TransactionStatus.class));
            assertThat(hasEntry(transaction)).isTrue();
            BatchRequestContextHolder.resetTransaction();
            assertThat(hasEntry(transaction)).isFalse();
            assertThat(BatchRequestContextHolder.getTransaction()).isEmpty();
        } finally {
            transaction.remove();
        }
    }

    private boolean hasEntry(ThreadLocal<?> threadLocal) throws Exception {
        Field localsField = Thread.class.getDeclaredField("threadLocals");
        localsField.setAccessible(true);
        Object locals = localsField.get(Thread.currentThread());
        Field tableField = locals.getClass().getDeclaredField("table");
        tableField.setAccessible(true);
        for (Object entry : (Object[]) tableField.get(locals)) {
            if (entry != null && ((Reference<?>) entry).get() == threadLocal) {
                return true;
            }
        }
        return false;
    }
}
