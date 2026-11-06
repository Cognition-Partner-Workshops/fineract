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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.useradministration.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserPreviousPasswordRepository;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.domain.UserDomainService;
import org.apache.fineract.useradministration.exception.RoleNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AppUserWritePlatformServiceSecurityTest {

    private static final Long OFFICE_ID = 27L;
    private static final Long ROLE_ID = 41L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private UserDomainService userDomainService;
    @Mock
    private PlatformPasswordEncoder platformPasswordEncoder;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserDataValidator fromApiJsonDeserializer;
    @Mock
    private AppUserPreviousPasswordRepository appUserPreviewPasswordRepository;
    @Mock
    private StaffRepositoryWrapper staffRepositoryWrapper;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private OfficeScopeValidator officeScopeValidator;
    @Mock
    private AppUser caller;
    @Mock
    private AppUser userToUpdate;
    @Mock
    private Office existingOffice;
    @Mock
    private Role targetRole;
    @Mock
    private Permission targetPermission;
    @Mock
    private JsonCommand command;
    @InjectMocks
    private AppUserWritePlatformServiceJpaRepositoryImpl underTest;

    @Test
    void rejectsAssigningRoleWithPermissionCallerDoesNotHave() {
        when(context.authenticatedUser()).thenReturn(caller);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(targetRole));
        when(targetRole.getPermissions()).thenReturn(List.of(targetPermission));
        when(targetPermission.getCode()).thenReturn("ALL_FUNCTIONS");
        doThrow(new NoAuthorizationException("permission not held")).when(caller)
                .validateHasPermissionTo("assign role permissions", List.of("ALL_FUNCTIONS"));

        assertThrows(NoAuthorizationException.class,
                () -> ReflectionTestUtils.invokeMethod(underTest, "assembleSetOfRoles", (Object) new String[] { ROLE_ID.toString() }));
    }

    @Test
    void permitsAssigningRoleWhosePermissionsCallerHas() {
        when(context.authenticatedUser()).thenReturn(caller);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(targetRole));
        when(targetRole.getPermissions()).thenReturn(List.of(targetPermission));
        when(targetPermission.getCode()).thenReturn("READ_CLIENT");

        assertDoesNotThrow(
                () -> ReflectionTestUtils.invokeMethod(underTest, "assembleSetOfRoles", (Object) new String[] { ROLE_ID.toString() }));

        verify(caller).validateHasPermissionTo("assign role permissions", List.of("READ_CLIENT"));
    }

    @Test
    void rejectsCreatingUserInOfficeOutsideCallersHierarchy() {
        when(context.authenticatedUser()).thenReturn(caller);
        when(command.json()).thenReturn("{}");
        when(command.longValueOfParameterNamed("officeId")).thenReturn(OFFICE_ID);
        lenient().when(officeRepositoryWrapper.findOneWithNotFoundDetection(OFFICE_ID))
                .thenThrow(new IllegalStateException("office lookup reached before scope validation"));
        doThrow(new NoAuthorizationException("office outside hierarchy")).when(officeScopeValidator).validateOffice(OFFICE_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.createUser(command));

        verify(officeRepositoryWrapper, org.mockito.Mockito.never()).findOneWithNotFoundDetection(OFFICE_ID);
    }

    @Test
    void permitsCreatingUserInOfficeWithinCallersHierarchy() {
        when(context.authenticatedUser()).thenReturn(caller);
        when(command.json()).thenReturn("{}");
        when(command.longValueOfParameterNamed("officeId")).thenReturn(OFFICE_ID);
        when(command.arrayValueOfParameterNamed("roles")).thenReturn(new String[] { ROLE_ID.toString() });
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class, () -> underTest.createUser(command));

        verify(officeScopeValidator).validateOffice(OFFICE_ID);
    }

    @Test
    void rejectsUpdatingUserWhoseExistingOfficeIsOutsideCallersHierarchy() {
        when(context.authenticatedUser(any(CommandWrapper.class))).thenReturn(caller);
        when(context.authenticatedUser()).thenReturn(caller);
        when(command.json()).thenReturn("{}");
        when(appUserRepository.findById(ROLE_ID)).thenReturn(Optional.of(userToUpdate));
        when(userToUpdate.getOffice()).thenReturn(existingOffice);
        when(existingOffice.getId()).thenReturn(OFFICE_ID);
        doThrow(new NoAuthorizationException("office outside hierarchy")).when(officeScopeValidator).validateOffice(OFFICE_ID);

        assertThrows(NoAuthorizationException.class, () -> underTest.updateUser(ROLE_ID, command));
    }

    @Test
    void rejectsUpdatingUserToOfficeOutsideCallersHierarchy() {
        final Long targetOfficeId = 28L;
        when(context.authenticatedUser(any(CommandWrapper.class))).thenReturn(caller);
        when(context.authenticatedUser()).thenReturn(caller);
        when(command.json()).thenReturn("{}");
        lenient().when(command.hasParameter("officeId")).thenReturn(true);
        when(command.longValueOfParameterNamed("officeId")).thenReturn(targetOfficeId);
        when(appUserRepository.findById(ROLE_ID)).thenReturn(Optional.of(userToUpdate));
        when(userToUpdate.getOffice()).thenReturn(existingOffice);
        when(existingOffice.getId()).thenReturn(OFFICE_ID);
        org.mockito.Mockito.lenient().doThrow(new NoAuthorizationException("office outside hierarchy")).when(officeScopeValidator)
                .validateOffice(targetOfficeId);

        assertThrows(NoAuthorizationException.class, () -> underTest.updateUser(ROLE_ID, command));
    }

    @Test
    void permitsUpdatingUserWithinExistingAndTargetOfficeScope() {
        final Long targetOfficeId = 28L;
        when(context.authenticatedUser(any(CommandWrapper.class))).thenReturn(caller);
        when(context.authenticatedUser()).thenReturn(caller);
        when(command.json()).thenReturn("{}");
        lenient().when(command.hasParameter("officeId")).thenReturn(true);
        when(command.longValueOfParameterNamed("officeId")).thenReturn(targetOfficeId);
        when(appUserRepository.findById(ROLE_ID)).thenReturn(Optional.of(userToUpdate));
        when(userToUpdate.getOffice()).thenReturn(existingOffice);
        when(existingOffice.getId()).thenReturn(OFFICE_ID);
        when(userToUpdate.update(command, platformPasswordEncoder, null)).thenReturn(Map.of());

        assertDoesNotThrow(() -> underTest.updateUser(ROLE_ID, command));

        verify(officeScopeValidator).validateOffice(OFFICE_ID);
        verify(officeScopeValidator).validateOffice(targetOfficeId);
    }
}
