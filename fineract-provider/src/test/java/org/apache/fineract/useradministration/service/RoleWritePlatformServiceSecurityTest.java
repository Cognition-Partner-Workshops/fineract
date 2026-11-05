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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fineract.useradministration.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.command.PermissionsCommand;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.PermissionRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.serialization.PermissionsCommandFromApiJsonDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleWritePlatformServiceSecurityTest {

    private static final Long ROLE_ID = 17L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private RoleDataValidator roleDataValidator;
    @Mock
    private PermissionsCommandFromApiJsonDeserializer permissionsDeserializer;
    @Mock
    private AppUser caller;
    @Mock
    private Role targetRole;
    @Mock
    private Permission permission;
    @Mock
    private JsonCommand command;
    @InjectMocks
    private RoleWritePlatformServiceJpaRepositoryImpl underTest;

    @Test
    void rejectsEditingRoleHeldByCaller() {
        when(context.authenticatedUser()).thenReturn(caller);
        when(caller.getRoles()).thenReturn(Set.of(targetRole));
        when(targetRole.getId()).thenReturn(ROLE_ID);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(targetRole));
        lenient().when(command.json()).thenReturn("{}");
        lenient().when(permissionsDeserializer.commandFromApiJson("{}")).thenReturn(new PermissionsCommand(Map.of()));

        assertThrows(NoAuthorizationException.class, () -> underTest.updateRolePermissions(ROLE_ID, command));
    }

    @Test
    void rejectsGrantingSpecialPermissionWithoutAllFunctions() {
        when(context.authenticatedUser()).thenReturn(caller);
        when(caller.getRoles()).thenReturn(Set.of());
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(targetRole));
        when(permissionRepository.findAll()).thenReturn(List.of(permission));
        when(permission.hasCode("SPECIAL_PERMISSION")).thenReturn(true);
        when(permission.getGrouping()).thenReturn("special");
        when(command.json()).thenReturn("{\"permissions\":{}}");
        when(permissionsDeserializer.commandFromApiJson(command.json()))
                .thenReturn(new PermissionsCommand(Map.of("SPECIAL_PERMISSION", true)));

        assertThrows(NoAuthorizationException.class, () -> underTest.updateRolePermissions(ROLE_ID, command));
    }

    @Test
    void rejectsGrantingPermissionCallerDoesNotHave() {
        when(context.authenticatedUser()).thenReturn(caller);
        when(caller.getRoles()).thenReturn(Set.of());
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(targetRole));
        when(permissionRepository.findAll()).thenReturn(List.of(permission));
        when(permission.hasCode("READ_CLIENT")).thenReturn(true);
        when(permission.getGrouping()).thenReturn("client");
        when(permission.getCode()).thenReturn("READ_CLIENT");
        when(command.json()).thenReturn("{\"permissions\":{}}");
        when(permissionsDeserializer.commandFromApiJson(command.json()))
                .thenReturn(new PermissionsCommand(Map.of("READ_CLIENT", true)));
        doThrow(new NoAuthorizationException("permission not held")).when(caller)
                .validateHasPermissionTo("grant role permission", List.of("READ_CLIENT"));

        assertThrows(NoAuthorizationException.class, () -> underTest.updateRolePermissions(ROLE_ID, command));
    }

    @Test
    void permitsGrantingPermissionCallerHasToRoleTheyDoNotHold() {
        when(context.authenticatedUser()).thenReturn(caller);
        when(caller.getRoles()).thenReturn(Set.of());
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(targetRole));
        when(permissionRepository.findAll()).thenReturn(List.of(permission));
        when(permission.hasCode("READ_CLIENT")).thenReturn(true);
        when(permission.getGrouping()).thenReturn("client");
        when(permission.getCode()).thenReturn("READ_CLIENT");
        when(command.json()).thenReturn("{\"permissions\":{}}");
        when(command.commandId()).thenReturn(23L);
        when(permissionsDeserializer.commandFromApiJson(command.json()))
                .thenReturn(new PermissionsCommand(Map.of("READ_CLIENT", true)));
        when(targetRole.updatePermission(permission, true)).thenReturn(true);

        assertDoesNotThrow(() -> underTest.updateRolePermissions(ROLE_ID, command));

        verify(caller).validateHasPermissionTo("grant role permission", List.of("READ_CLIENT"));
        verify(roleRepository).saveAndFlush(targetRole);
    }
}
