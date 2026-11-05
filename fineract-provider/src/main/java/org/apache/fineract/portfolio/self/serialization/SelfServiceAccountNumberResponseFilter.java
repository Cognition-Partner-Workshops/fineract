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
package org.apache.fineract.portfolio.self.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Provider
@Component
public class SelfServiceAccountNumberResponseFilter implements ContainerResponseFilter {

    private static final Pattern ERROR_DIGITS = Pattern.compile("[0-9]{5,}");
    private final ObjectMapper mapper;

    public SelfServiceAccountNumberResponseFilter(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        String path = request.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.equals("v1/self") && !path.startsWith("v1/self/")) {
            return;
        }
        MediaType media = response.getMediaType();
        if (!response.hasEntity() || media == null
                || !(media.isCompatible(MediaType.APPLICATION_JSON_TYPE) || media.getSubtype().endsWith("+json"))) {
            return;
        }
        Object entity = response.getEntity();
        if (entity instanceof String text && text.isBlank()) {
            return;
        }
        JsonNode json;
        try {
            json = entity instanceof String text ? mapper.readTree(text) : mapper.valueToTree(entity);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            response.setStatus(500);
            response.setEntity("{\"message\":\"Cannot safely serialize self-service response\"}");
            response.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
            return;
        }
        response.setEntity(mask(json, response.getStatus() >= 400, false).toString());
        response.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
    }

    private JsonNode mask(JsonNode node, boolean error, boolean redactErrorValue) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (SelfServiceAccountNumbers.isAccountNumber(field.getKey()) && value.isValueNode() && !value.isNull()) {
                    object.put(field.getKey(), SelfServiceAccountNumbers.mask(value.asText()));
                } else {
                    String key = field.getKey().toLowerCase(Locale.ROOT);
                    boolean errorField = key.endsWith("message") || key.equals("args") || key.equals("arguments");
                    object.set(field.getKey(), mask(value, error, redactErrorValue || (error && errorField)));
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int index = 0; index < array.size(); index++) {
                array.set(index, mask(array.get(index), error, redactErrorValue));
            }
        } else if (node.isTextual() || (redactErrorValue && node.isNumber())) {
            String value = node.asText();
            String masked = node.isTextual() ? SelfServiceAccountNumbers.maskErrorCode(value) : value;
            if (redactErrorValue) {
                masked = ERROR_DIGITS.matcher(masked).replaceAll(match -> SelfServiceAccountNumbers.mask(match.group()));
            }
            if (!masked.equals(value)) {
                return TextNode.valueOf(masked);
            }
        }
        return node;
    }
}
