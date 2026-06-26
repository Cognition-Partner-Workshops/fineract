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

import java.util.List;
import org.apache.fineract.fundservice.data.FundData;
import org.apache.fineract.fundservice.domain.Fund;
import org.apache.fineract.fundservice.domain.FundRepository;
import org.apache.fineract.fundservice.exception.FundNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side service implementation backed directly by {@link FundRepository}. Funds are returned ordered by name,
 * mirroring the monolith read contract.
 */
@Service
@Transactional(readOnly = true)
public class FundReadServiceImpl implements FundReadService {

    private final FundRepository fundRepository;

    public FundReadServiceImpl(final FundRepository fundRepository) {
        this.fundRepository = fundRepository;
    }

    @Override
    public List<FundData> retrieveAll() {
        return this.fundRepository.findAllByOrderByNameAsc().stream().map(FundReadServiceImpl::toData).toList();
    }

    @Override
    public FundData retrieveOne(final Long id) {
        final Fund fund = this.fundRepository.findById(id).orElseThrow(() -> new FundNotFoundException(id));
        return toData(fund);
    }

    private static FundData toData(final Fund fund) {
        return FundData.instance(fund.getId(), fund.getName(), fund.getExternalId());
    }
}
