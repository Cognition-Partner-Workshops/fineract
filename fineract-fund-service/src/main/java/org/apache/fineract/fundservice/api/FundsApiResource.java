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
package org.apache.fineract.fundservice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.apache.fineract.fundservice.data.FundData;
import org.apache.fineract.fundservice.data.FundRequest;
import org.apache.fineract.fundservice.service.FundReadService;
import org.apache.fineract.fundservice.service.FundWriteService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC REST controller mirroring the monolith JAX-RS Fund contract at {@code /v1/funds}. The raw JSON body is
 * validated by {@code FundApiJsonValidator} (rejecting unknown parameters and enforcing the length rules) before the
 * request reaches the read/write services.
 */
@RestController
@RequestMapping(path = "/v1/funds", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Funds", description = "CRUD operations for funds in the carved-out Fund microservice.")
@SecurityRequirement(name = "basicAuth")
public class FundsApiResource {

    private final FundReadService fundReadService;
    private final FundWriteService fundWriteService;
    private final org.apache.fineract.fundservice.serialization.FundApiJsonValidator validator;

    public FundsApiResource(final FundReadService fundReadService, final FundWriteService fundWriteService,
            final org.apache.fineract.fundservice.serialization.FundApiJsonValidator validator) {
        this.fundReadService = fundReadService;
        this.fundWriteService = fundWriteService;
        this.validator = validator;
    }

    @GetMapping
    @Operation(summary = "Retrieve Funds", description = "Returns the list of funds ordered by name.")
    public List<FundData> retrieveFunds() {
        return this.fundReadService.retrieveAll();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a Fund", description = "Creates a fund and returns its generated identifier.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = FundRequest.class)))
    public Map<String, Object> createFund(@RequestBody(required = false) final String body) {
        final FundRequest request = this.validator.validateForCreate(body);
        final Long resourceId = this.fundWriteService.create(request);
        return Map.of("resourceId", resourceId);
    }

    @GetMapping("/{fundId}")
    @Operation(summary = "Retrieve a Fund", description = "Returns the details of a single fund; 404 if it does not exist.")
    public FundData retrieveFund(@PathVariable("fundId") final Long fundId) {
        return this.fundReadService.retrieveOne(fundId);
    }

    @PutMapping(path = "/{fundId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a Fund", description = "Updates the name/externalId of a fund and returns the applied changes.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = FundRequest.class)))
    public Map<String, Object> updateFund(@PathVariable("fundId") final Long fundId, @RequestBody(required = false) final String body) {
        final FundRequest request = this.validator.validateForUpdate(body);
        return this.fundWriteService.update(fundId, request);
    }
}
