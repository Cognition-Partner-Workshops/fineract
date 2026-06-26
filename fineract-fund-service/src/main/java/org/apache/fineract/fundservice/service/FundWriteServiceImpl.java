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
package org.apache.fineract.fundservice.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.fundservice.data.FundRequest;
import org.apache.fineract.fundservice.domain.Fund;
import org.apache.fineract.fundservice.domain.FundRepository;
import org.apache.fineract.fundservice.exception.FundDuplicateException;
import org.apache.fineract.fundservice.exception.FundNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-side service implementation. Replaces the monolith Maker-Checker command bus with a direct, transactional
 * repository call while preserving its semantics: create returns the new id, update returns the map of actual changes,
 * and unique-constraint violations are translated into duplicate-name / duplicate-externalId errors (HTTP 409).
 */
@Service
public class FundWriteServiceImpl implements FundWriteService {

    private static final String NAME_CONSTRAINT = "fund_name_org";
    private static final String EXTERNAL_ID_CONSTRAINT = "fund_externalid_org";

    private final FundRepository fundRepository;

    public FundWriteServiceImpl(final FundRepository fundRepository) {
        this.fundRepository = fundRepository;
    }

    @Transactional
    @Override
    public Long create(final FundRequest request) {
        try {
            final Fund fund = new Fund(emptyToNull(request.getName()), emptyToNull(request.getExternalId()));
            this.fundRepository.saveAndFlush(fund);
            return fund.getId();
        } catch (final DataIntegrityViolationException dve) {
            throw mapDuplicate(dve, request.getName(), request.getExternalId());
        }
    }

    @Transactional
    @Override
    public Map<String, Object> update(final Long id, final FundRequest request) {
        final Fund fund = this.fundRepository.findById(id).orElseThrow(() -> new FundNotFoundException(id));

        final Map<String, Object> changes = new LinkedHashMap<>();
        if (request.getName() != null && !request.getName().equals(fund.getName())) {
            changes.put("name", request.getName());
            fund.setName(emptyToNull(request.getName()));
        }
        if (request.getExternalId() != null && !request.getExternalId().equals(fund.getExternalId())) {
            changes.put("externalId", request.getExternalId());
            fund.setExternalId(emptyToNull(request.getExternalId()));
        }

        if (!changes.isEmpty()) {
            try {
                this.fundRepository.saveAndFlush(fund);
            } catch (final DataIntegrityViolationException dve) {
                throw mapDuplicate(dve, request.getName(), request.getExternalId());
            }
        }
        return changes;
    }

    private FundDuplicateException mapDuplicate(final DataIntegrityViolationException dve, final String name, final String externalId) {
        final Throwable mostSpecific = dve.getMostSpecificCause();
        final String message = mostSpecific.getMessage() == null ? "" : mostSpecific.getMessage().toLowerCase(Locale.ROOT);
        // Match on the constraint name or the column-name token (e.g. PostgreSQL's "Key (external_id)=(...)"). Avoid
        // bare substrings like "external_id"/"name" because the violated column *value* is embedded in the message and
        // would otherwise cause misclassification (e.g. a fund name that contains the text "external_id").
        if (message.contains(EXTERNAL_ID_CONSTRAINT) || message.contains("(external_id)=")) {
            return new FundDuplicateException("error.msg.fund.duplicate.externalId",
                    "A fund with external id '" + externalId + "' already exists", "externalId", externalId, dve);
        }
        if (message.contains(NAME_CONSTRAINT) || message.contains("(name)=")) {
            return new FundDuplicateException("error.msg.fund.duplicate.name", "A fund with name '" + name + "' already exists", "name",
                    name, dve);
        }
        return new FundDuplicateException("error.msg.fund.duplicate", "A fund with the same unique value already exists", null, null, dve);
    }

    private static String emptyToNull(final String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
