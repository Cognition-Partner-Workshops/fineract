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
package org.apache.fineract.commands.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.domain.CommandProcessingResultType;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.exception.CommandNotAwaitingApprovalException;
import org.apache.fineract.commands.exception.CommandNotFoundException;
import org.apache.fineract.commands.exception.UnsupportedCommandException;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.jobs.service.SchedulerJobRunnerReadService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.OfficeScopeValidator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioCommandSourceWritePlatformServiceImpl implements PortfolioCommandSourceWritePlatformService {

    private final PlatformSecurityContext context;
    private final CommandSourceRepository commandSourceRepository;
    private final FromJsonHelper fromApiJsonHelper;
    private final CommandProcessingService processAndLogCommandService;
    private final SchedulerJobRunnerReadService schedulerJobRunnerReadService;
    private final ConfigurationDomainService configurationService;
    private final OfficeScopeValidator officeScopeValidator;

    private static final Set<String> SELF_SERVICE_USER_FIELDS = Set.of("roles", "officeId", "isSelfServiceUser");
    private static final Set<String> PASSWORD_FIELDS = Set.of("password", "repeatPassword");

    @Override
    public CommandProcessingResult logCommandSource(final CommandWrapper wrapper) {
        boolean isApprovedByChecker = false;
        final AppUser authenticatedUser = this.context.authenticatedUser(wrapper);
        JsonElement parsedCommand = null;
        if (wrapper.isChangeOfOwnUserDetails(authenticatedUser.getId())) {
            parsedCommand = this.fromApiJsonHelper.parse(wrapper.getJson());
            if (wrapper.isUpdate() && containsAny(parsedCommand, SELF_SERVICE_USER_FIELDS)) {
                throw new NoAuthorizationException("Users cannot update their own roles, office, or self-service status.");
            }
            if (isPasswordOnlyChange(wrapper, parsedCommand)) {
                isApprovedByChecker = true;
            } else {
                authenticatedUser.validateHasPermissionTo(wrapper.getTaskPermissionName());
            }
        } else {
            authenticatedUser.validateHasPermissionTo(wrapper.getTaskPermissionName());
        }
        this.officeScopeValidator.validate(wrapper);
        validateIsUpdateAllowed();

        final String json = wrapper.getJson();
        if (parsedCommand == null) {
            parsedCommand = this.fromApiJsonHelper.parse(json);
        }
        JsonCommand command = JsonCommand.from(json, parsedCommand, this.fromApiJsonHelper, wrapper.getEntityName(), wrapper.getEntityId(),
                wrapper.getSubentityId(), wrapper.getGroupId(), wrapper.getClientId(), wrapper.getLoanId(), wrapper.getSavingsId(),
                wrapper.getTransactionId(), wrapper.getHref(), wrapper.getProductId(), wrapper.getCreditBureauId(),
                wrapper.getOrganisationCreditBureauId(), wrapper.getJobName(), wrapper.getLoanExternalId());

        return this.processAndLogCommandService.executeCommand(wrapper, command, isApprovedByChecker);
    }

    private boolean isPasswordOnlyChange(final CommandWrapper wrapper, final JsonElement parsedCommand) {
        if ((!wrapper.isUpdate() && !wrapper.isChangePasswordOperation()) || parsedCommand == null || !parsedCommand.isJsonObject()) {
            return false;
        }
        return parsedCommand.getAsJsonObject().has("password")
                && parsedCommand.getAsJsonObject().keySet().stream().allMatch(PASSWORD_FIELDS::contains);
    }

    private boolean containsAny(final JsonElement parsedCommand, final Set<String> keys) {
        if (parsedCommand == null || !parsedCommand.isJsonObject()) {
            return false;
        }
        final JsonObject jsonObject = parsedCommand.getAsJsonObject();
        return keys.stream().anyMatch(jsonObject::has);
    }

    @Override
    public CommandProcessingResult approveEntry(final Long makerCheckerId) {
        validateIsUpdateAllowed();
        final CommandSource commandSourceInput = validateMakerCheckerTransaction(makerCheckerId);

        final CommandWrapper wrapper = CommandWrapper.fromExistingCommand(makerCheckerId, commandSourceInput.getActionName(),
                commandSourceInput.getEntityName(), commandSourceInput.getResourceId(), commandSourceInput.getSubResourceId(),
                commandSourceInput.getResourceGetUrl(), commandSourceInput.getProductId(), commandSourceInput.getOfficeId(),
                commandSourceInput.getGroupId(), commandSourceInput.getClientId(), commandSourceInput.getLoanId(),
                commandSourceInput.getSavingsId(), commandSourceInput.getTransactionId(), commandSourceInput.getCreditBureauId(),
                commandSourceInput.getOrganisationCreditBureauId(), commandSourceInput.getIdempotencyKey(),
                commandSourceInput.getLoanExternalId());
        final JsonElement parsedCommand = this.fromApiJsonHelper.parse(commandSourceInput.getCommandAsJson());
        final JsonCommand command = JsonCommand.fromExistingCommand(makerCheckerId, commandSourceInput.getCommandAsJson(), parsedCommand,
                this.fromApiJsonHelper, commandSourceInput.getEntityName(), commandSourceInput.getResourceId(),
                commandSourceInput.getSubResourceId(), commandSourceInput.getGroupId(), commandSourceInput.getClientId(),
                commandSourceInput.getLoanId(), commandSourceInput.getSavingsId(), commandSourceInput.getTransactionId(),
                commandSourceInput.getResourceGetUrl(), commandSourceInput.getProductId(), commandSourceInput.getCreditBureauId(),
                commandSourceInput.getOrganisationCreditBureauId(), commandSourceInput.getJobName(),
                commandSourceInput.getLoanExternalId());

        try {
            return this.processAndLogCommandService.executeCommand(wrapper, command, true);
        } catch (RuntimeException exception) {
            this.commandSourceRepository.releaseApprovalClaim(makerCheckerId, this.context.authenticatedUser(),
                    CommandProcessingResultType.AWAITING_APPROVAL.getValue());
            throw exception;
        }
    }

    @Transactional
    @Override
    public Long deleteEntry(final Long makerCheckerId) {

        validateIsUpdateAllowed();
        validateMakerCheckerTransaction(makerCheckerId);

        this.commandSourceRepository.deleteById(makerCheckerId);

        return makerCheckerId;
    }

    private CommandSource validateMakerCheckerTransaction(final Long makerCheckerId) {
        final CommandSource commandSource = this.commandSourceRepository.findById(makerCheckerId)
                .orElseThrow(() -> new CommandNotFoundException(makerCheckerId));
        if (!commandSource.isAwaitingApproval()) {
            throw new CommandNotAwaitingApprovalException(makerCheckerId);
        }
        AppUser appUser = this.context.authenticatedUser();
        String permissionCode = commandSource.getPermissionCode();
        appUser.validateHasCheckerPermissionTo(permissionCode);
        if (!configurationService.isSameMakerCheckerEnabled() && !appUser.isCheckerSuperUser()) {
            AppUser maker = commandSource.getMaker();
            if (maker == null) {
                throw new UnsupportedCommandException(permissionCode, "Maker user is missing.");
            }
            if (Objects.equals(appUser.getId(), maker.getId())) {
                throw new UnsupportedCommandException(permissionCode, "Can not be checked by the same user.");
            }
        }
        this.officeScopeValidator.validate(commandSource);
        final int claimed = this.commandSourceRepository.claimForApproval(makerCheckerId, appUser, DateUtils.getAuditOffsetDateTime(),
                CommandProcessingResultType.AWAITING_APPROVAL.getValue());
        if (claimed != 1) {
            throw new CommandNotAwaitingApprovalException(makerCheckerId);
        }
        return commandSource;
    }

    private void validateIsUpdateAllowed() {
        this.schedulerJobRunnerReadService.isUpdatesAllowed();
    }

    @Override
    public Long rejectEntry(final Long makerCheckerId) {
        validateIsUpdateAllowed();
        final CommandSource commandSourceInput = validateMakerCheckerTransaction(makerCheckerId);
        final AppUser maker = this.context.authenticatedUser();
        commandSourceInput.markAsRejected(maker);
        try {
            this.commandSourceRepository.save(commandSourceInput);
        } catch (RuntimeException exception) {
            this.commandSourceRepository.releaseApprovalClaim(makerCheckerId, maker,
                    CommandProcessingResultType.AWAITING_APPROVAL.getValue());
            throw exception;
        }
        return makerCheckerId;
    }
}
