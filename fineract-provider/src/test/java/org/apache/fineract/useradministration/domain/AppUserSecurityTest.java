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
package org.apache.fineract.useradministration.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class AppUserSecurityTest {

    @Test
    void allFunctionsReadAllowsReadButNotCreateUpdateOrDelete() {
        final AppUser user = userWithPermission("ALL_FUNCTIONS_READ");

        assertDoesNotThrow(() -> user.validateHasReadPermission("USER"));
        assertThrows(NoAuthorizationException.class, () -> user.validateHasCreatePermission("USER"));
        assertThrows(NoAuthorizationException.class, () -> user.validateHasUpdatePermission("USER"));
        assertThrows(NoAuthorizationException.class, () -> user.validateHasDeletePermission("USER"));
    }

    private AppUser userWithPermission(final String permission) {
        final AppUser user = new AppUser();
        final Role role = Mockito.mock(Role.class);
        when(role.hasPermissionTo(permission)).thenReturn(true);
        ReflectionTestUtils.setField(user, "roles", Set.of(role));
        return user;
    }
}
