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
package org.apache.fineract.cob.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.junit.jupiter.api.Test;

class ReloaderServiceTest {

    @Test
    void reloadUsesFirstMatchingService() {
        AbstractPersistableCustom<Long> input = mock(AbstractPersistableCustom.class);
        AbstractPersistableCustom<Long> result = mock(AbstractPersistableCustom.class);
        ReloadService<AbstractPersistableCustom<Long>> first = mock(ReloadService.class);
        ReloadService<AbstractPersistableCustom<Long>> second = mock(ReloadService.class);
        when(first.canReload(input)).thenReturn(true);
        when(first.reload(input)).thenReturn(result);

        assertSame(result, new ReloaderService(List.of(first, second)).reload(input));
        verify(first).reload(input);
        verify(second, never()).canReload(input);
    }

    @Test
    void reloadReturnsInputWhenNoServiceMatches() {
        AbstractPersistableCustom<Long> input = mock(AbstractPersistableCustom.class);
        ReloadService<AbstractPersistableCustom<Long>> service = mock(ReloadService.class);
        when(service.canReload(input)).thenReturn(false);

        assertSame(input, new ReloaderService(List.of(service)).reload(input));
    }

    @Test
    void reloadPropagatesServiceFailure() {
        AbstractPersistableCustom<Long> input = mock(AbstractPersistableCustom.class);
        ReloadService<AbstractPersistableCustom<Long>> service = mock(ReloadService.class);
        when(service.canReload(input)).thenReturn(true);
        when(service.reload(input)).thenThrow(new IllegalStateException("reload failed"));

        assertThrows(IllegalStateException.class, () -> new ReloaderService(List.of(service)).reload(input));
    }
}
