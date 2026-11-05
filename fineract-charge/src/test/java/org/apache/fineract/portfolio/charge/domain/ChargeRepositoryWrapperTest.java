/*
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
package org.apache.fineract.portfolio.charge.domain;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.portfolio.charge.exception.ChargeIsNotActiveException;
import org.apache.fineract.portfolio.charge.exception.ChargeNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeRepositoryWrapperTest {

    @Mock
    private ChargeRepository repository;

    @Test
    void findOneWithNotFoundDetectionRejectsMissingCharge() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ChargeNotFoundException.class, () -> new ChargeRepositoryWrapper(repository).findOneWithNotFoundDetection(1L));
    }

    @Test
    void findOneWithNotFoundDetectionRejectsDeletedCharge() {
        Charge charge = mock(Charge.class);
        when(repository.findById(1L)).thenReturn(Optional.of(charge));
        when(charge.isDeleted()).thenReturn(true);

        assertThrows(ChargeNotFoundException.class, () -> new ChargeRepositoryWrapper(repository).findOneWithNotFoundDetection(1L));
    }

    @Test
    void findOneWithNotFoundDetectionRejectsInactiveCharge() {
        Charge charge = mock(Charge.class);
        when(repository.findById(1L)).thenReturn(Optional.of(charge));
        when(charge.isDeleted()).thenReturn(false);
        when(charge.isActive()).thenReturn(false);
        when(charge.getName()).thenReturn("Inactive fee");

        assertThrows(ChargeIsNotActiveException.class, () -> new ChargeRepositoryWrapper(repository).findOneWithNotFoundDetection(1L));
    }

    @Test
    void findOneWithNotFoundDetectionReturnsActiveCharge() {
        Charge charge = mock(Charge.class);
        when(repository.findById(1L)).thenReturn(Optional.of(charge));
        when(charge.isDeleted()).thenReturn(false);
        when(charge.isActive()).thenReturn(true);

        assertSame(charge, new ChargeRepositoryWrapper(repository).findOneWithNotFoundDetection(1L));
    }
}
