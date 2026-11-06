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
package org.apache.fineract.infrastructure.security.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OfficeScopeValidator {

    private static final String LOAN_HIERARCHY_SQL = """
            select office.hierarchy
            from m_loan loan
            left join m_client client on client.id = loan.client_id
            left join m_group client_group on client_group.id = loan.group_id
            join m_office office on office.id = coalesce(client.office_id, client_group.office_id)
            where loan.id = ?
            """;
    private static final String SAVINGS_HIERARCHY_SQL = """
            select office.hierarchy
            from m_savings_account savings
            left join m_client client on client.id = savings.client_id
            left join m_group client_group on client_group.id = savings.group_id
            join m_office office on office.id = coalesce(client.office_id, client_group.office_id)
            where savings.id = ?
            """;
    private static final String CLIENT_HIERARCHY_SQL = """
            select office.hierarchy
            from m_client client
            join m_office office on office.id = client.office_id
            where client.id = ?
            union
            select transfer_office.hierarchy
            from m_client client
            join m_office transfer_office on transfer_office.id = client.transfer_to_office_id
            where client.id = ? and client.transfer_to_office_id is not null
            """;
    private static final String GROUP_HIERARCHY_SQL = """
            select office.hierarchy
            from m_group client_group
            join m_office office on office.id = client_group.office_id
            where client_group.id = ?
            """;
    private static final String OFFICE_HIERARCHY_SQL = "select hierarchy from m_office where id = ?";
    private static final String SAVINGS_OWNER_SQL = "select client_id, group_id from m_savings_account where id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;

    public void validate(final CommandWrapper wrapper) {
        validateOffice(wrapper.getOfficeId());
        validateGroup(wrapper.getGroupId());
        validateClient(wrapper.getClientId());
        validateLoan(wrapper.getLoanId());
        validateSavings(wrapper.getSavingsId());
    }

    public void validate(final CommandSource commandSource) {
        validateOffice(commandSource.getOfficeId());
        validateGroup(commandSource.getGroupId());
        validateClient(commandSource.getClientId());
        validateLoan(commandSource.getLoanId());
        validateSavings(commandSource.getSavingsId());
    }

    public void validateLoan(final Long loanId) {
        validateHierarchy(loanId, LOAN_HIERARCHY_SQL);
    }

    public void validateSavings(final Long savingsId) {
        validateHierarchy(savingsId, SAVINGS_HIERARCHY_SQL);
    }

    public void validateSavingsHasSameOwner(final Long targetSavingsId, final Long sourceSavingsId) {
        if (targetSavingsId == null) {
            return;
        }
        final SavingsOwner targetOwner = savingsOwner(targetSavingsId);
        if (targetOwner == null) {
            return;
        }
        final SavingsOwner sourceOwner = savingsOwner(sourceSavingsId);
        if (sourceOwner == null || !sourceOwner.matches(targetOwner)) {
            throw new NoAuthorizationException("Savings account must have the same owner as the deposit account.");
        }
        validateSavings(targetSavingsId);
    }

    public void validateSavingsHasSameOwner(final Long targetSavingsId, final Long clientId, final Long groupId) {
        if (targetSavingsId == null) {
            return;
        }
        final SavingsOwner targetOwner = savingsOwner(targetSavingsId);
        if (targetOwner == null) {
            return;
        }
        final boolean sameOwner = clientId != null ? Objects.equals(clientId, targetOwner.clientId())
                : groupId != null && Objects.equals(groupId, targetOwner.groupId());
        if (!sameOwner) {
            throw new NoAuthorizationException("Savings account must have the same owner as the deposit account.");
        }
        validateSavings(targetSavingsId);
    }

    private SavingsOwner savingsOwner(final Long savingsId) {
        final List<Map<String, Object>> rows = jdbcTemplate.queryForList(SAVINGS_OWNER_SQL, savingsId);
        if (rows.isEmpty()) {
            return null;
        }
        final Map<String, Object> row = rows.get(0);
        final Number clientId = (Number) row.get("client_id");
        final Number groupId = (Number) row.get("group_id");
        return new SavingsOwner(clientId == null ? null : clientId.longValue(), groupId == null ? null : groupId.longValue());
    }

    private record SavingsOwner(Long clientId, Long groupId) {

        boolean matches(final SavingsOwner other) {
            if (clientId != null) {
                return Objects.equals(clientId, other.clientId);
            }
            return groupId != null && Objects.equals(groupId, other.groupId);
        }
    }

    public void validateSavingsBelongsToClient(final Long savingsId, final Long clientId) {
        if (savingsId == null) {
            return;
        }
        validateSavingsHasSameOwner(savingsId, clientId, null);
    }

    public void validateClient(final Long clientId) {
        if (clientId == null) {
            return;
        }
        final List<String> hierarchies = jdbcTemplate.queryForList(CLIENT_HIERARCHY_SQL, String.class, clientId, clientId);
        validateAnyHierarchy(hierarchies);
    }

    public void validateGroup(final Long groupId) {
        validateHierarchy(groupId, GROUP_HIERARCHY_SQL);
    }

    public void validateOffice(final Long officeId) {
        validateHierarchy(officeId, OFFICE_HIERARCHY_SQL);
    }

    private void validateHierarchy(final Long resourceId, final String sql) {
        if (resourceId == null) {
            return;
        }
        final List<String> hierarchies = jdbcTemplate.queryForList(sql, String.class, resourceId);
        validateAnyHierarchy(hierarchies);
    }

    private void validateAnyHierarchy(final List<String> hierarchies) {
        if (hierarchies.isEmpty()) {
            return;
        }
        final String userHierarchy = context.officeHierarchy();
        final String hierarchyToValidate = hierarchies.stream().filter(hierarchy -> hierarchy.startsWith(userHierarchy)).findFirst()
                .orElse(hierarchies.get(0));
        context.validateAccessRights(hierarchyToValidate);
    }
}
