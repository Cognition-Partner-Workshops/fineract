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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.ExcludeNothingWithPrettyPrintingOffJsonSerializerGoogleGson;
import org.apache.fineract.portfolio.self.registration.api.SelfServiceRegistrationApiResource;
import org.apache.fineract.portfolio.self.registration.service.SelfServiceRegistrationWritePlatformService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SelfServiceAccountDisclosureTest {

    private String response(String path, String json) throws Exception {
        return response(path, json, 200);
    }

    private String response(String path, String json, int expectedStatus) throws Exception {
        return response(path, json, 200, expectedStatus);
    }

    private String response(String path, String json, int initialStatus, int expectedStatus) throws Exception {
        return response(path, json, initialStatus, expectedStatus, MediaType.APPLICATION_JSON_TYPE);
    }

    private String response(String path, Object json, int initialStatus, int expectedStatus, MediaType media) throws Exception {
        AtomicReference<Object> entity = new AtomicReference<>(json);
        AtomicInteger status = new AtomicInteger(initialStatus);
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        String input = "{\"accountNumber\":\"001234567890\"}";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        when(request.getEntityStream()).thenReturn(inputStream);
        UriInfo uri = mock(UriInfo.class);
        when(request.getUriInfo()).thenReturn(uri);
        when(uri.getPath()).thenReturn(path);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getStatus()).thenAnswer(i -> status.get());
        doAnswer(i -> {
            status.set(i.getArgument(0));
            return null;
        }).when(response).setStatus(anyInt());
        when(response.hasEntity()).thenReturn(true);
        when(response.getEntity()).thenAnswer(i -> entity.get());
        when(response.getMediaType()).thenReturn(media);
        when(response.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        doAnswer(i -> {
            entity.set(i.getArgument(0));
            return null;
        }).when(response).setEntity(any());
        try (AnnotationConfigApplicationContext beans = new AnnotationConfigApplicationContext()) {
            beans.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            beans.scan("org.apache.fineract.portfolio.self.serialization");
            beans.refresh();
            for (ContainerResponseFilter filter : beans.getBeansOfType(ContainerResponseFilter.class).values()) {
                filter.filter(request, response);
            }
        }
        verify(request, never()).getEntityStream();
        verify(request, never()).setEntityStream(any());
        assertEquals(input, new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        assertEquals(expectedStatus, status.get());
        return entity.get().toString();
    }

    @Test
    void allSelfResourcesMaskNestedAccountsAndKeepAmountsAndIds() throws Exception {
        for (String resource : new String[] { "clients", "savingsaccounts", "loans", "shareaccounts", "accounttransfers/template",
                "beneficiaries/tpt" }) {
            JsonNode output = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(response(
                    "v1/self/" + resource,
                    "{\"accountNo\":\"001234567890\",\"nested\":[{\"fromAccountNo\":\"12345678\",\"accountNumber\":12345678}],\"amount\":12345678,\"id\":12345678,\"preciseAmount\":12345678901234567890.12345678901234567890}"));
            assertEquals("****7890", output.get("accountNo").asText());
            assertEquals("****5678", output.at("/nested/0/fromAccountNo").asText());
            assertEquals("****5678", output.at("/nested/0/accountNumber").asText());
            assertEquals(12345678, output.get("amount").asInt());
            assertEquals(12345678, output.get("id").asInt());
            assertEquals(0,
                    new BigDecimal("12345678901234567890.12345678901234567890").compareTo(output.get("preciseAmount").decimalValue()));
        }
    }

    @Test
    void staffAndSimilarlyNamedNonSelfPathsAreByteIdentical() throws Exception {
        String json = "{\"accountNo\":\"001234567890\"}";
        for (String path : new String[] { "v1/clients", "v1/selfish/clients", "v1/clients/1/self" }) {
            assertEquals(json, response(path, json));
        }
    }

    @Test
    void nullEmptyShortAndAlreadyMaskedAccountsAreHandled() throws Exception {
        JsonNode output = new ObjectMapper().readTree(response("v1/self/loans",
                "[{\"accountNo\":null},{\"accountNo\":\"\"},{\"accountNo\":\"123\"},{\"accountNo\":\"****7890\"}]"));
        assertTrue(output.get(0).get("accountNo").isNull());
        assertEquals("", output.get(1).get("accountNo").asText());
        assertEquals("****123", output.get(2).get("accountNo").asText());
        assertEquals("****7890", output.get(3).get("accountNo").asText());
    }

    @Test
    void malformedSelfJsonFailsClosedRatherThanReturningAnUnfilteredBody() throws Exception {
        String output = response("v1/self/clients", "invalid JSON", 500);
        assertFalse(output.contains("invalid JSON"));
        assertTrue(output.contains("Cannot safely serialize self-service response"));
    }

    @Test
    void beneficiaryErrorCodesCannotEchoTheAccountNumber() throws Exception {
        String output = response("v1/self/beneficiaries/tpt",
                "{\"userMessageGlobalisationCode\":\"error.msg.beneficiary.accountNumber.001234567890.accountType.savings\"}");
        assertFalse(output.contains("001234567890"));
        assertTrue(output.contains("****7890"));
    }

    @Test
    void duplicateAccountErrorMessagesAndNestedArgumentsAreMaskedWithoutAnAccountCodePrefix() throws Exception {
        String body = "{\"developerMessage\":\"Duplicate account 001234567890\",\"errors\":["
                + "{\"userMessageGlobalisationCode\":\"error.msg.savingsaccount.duplicate.accountNo\","
                + "\"defaultUserMessage\":\"Account 001234567890 already exists\","
                + "\"args\":[{\"value\":\"001234567890\"},12345678,null,true,42]}]}";
        for (String resource : new String[] { "loans", "savingsaccounts" }) {
            String output = response("v1/self/" + resource, body, 400, 400);
            assertFalse(output.contains("001234567890"));
            JsonNode json = new ObjectMapper().readTree(output);
            assertEquals("****7890", json.at("/errors/0/args/0/value").asText());
            assertTrue(json.at("/errors/0/args/1").isIntegralNumber());
            assertEquals(12345678, json.at("/errors/0/args/1").asInt());
            assertTrue(json.at("/errors/0/args/2").isNull());
            assertTrue(json.at("/errors/0/args/3").isBoolean());
            assertEquals(42, json.at("/errors/0/args/4").asInt());
        }
        assertEquals(body, response("v1/savingsaccounts", body, 400, 400));
        assertEquals(body, response("v1/self/loans", body));
    }

    @Test
    void errorNumbersKeepTheirTypesWhileStringArgumentsAreMasked() throws Exception {
        String body = "{\"message\":\"Account 12345678 already exists\","
                + "\"args\":[12345,12345678901234567890,12345.67,-12345,{\"value\":12345},\"12345678\",null,true]," + "\"count\":12345}";
        JsonNode output = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(response("v1/self/loans", body, 400, 400));
        JsonNode input = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body);
        for (int index = 0; index < 5; index++) {
            assertEquals(input.at("/args/" + index), output.at("/args/" + index));
        }
        assertEquals("****5678", output.at("/args/5").asText());
        assertTrue(output.at("/args/6").isNull());
        assertTrue(output.at("/args/7").isBoolean());
        assertEquals("Account ****5678 already exists", output.get("message").asText());
        assertEquals(input.get("count"), output.get("count"));
    }

    @Test
    void numericArgumentsMatchingWholeMessageDigitRunsAreMasked() throws Exception {
        JsonNode output = new ObjectMapper().readTree(response("v1/self/loans",
                "{\"message\":\"Duplicate account 12345678\",\"args\":[12345678,12345,5678],\"count\":12345678}", 400, 400));
        assertEquals("Duplicate account ****5678", output.get("message").asText());
        assertEquals("****5678", output.at("/args/0").asText());
        assertTrue(output.at("/args/0").isTextual());
        assertEquals(12345, output.at("/args/1").asInt());
        assertTrue(output.at("/args/1").isIntegralNumber());
        assertEquals(5678, output.at("/args/2").asInt());
        assertTrue(output.get("count").isIntegralNumber());
        assertEquals(12345678, output.get("count").asInt());
    }

    @Test
    void numericArgumentsMatchStringArgumentsBeforeRedactionRegardlessOfOrder() throws Exception {
        for (String arguments : new String[] { "12345678,{\"value\":\"account 12345678\"}", "{\"value\":\"account 12345678\"},12345678" }) {
            JsonNode output = new ObjectMapper().readTree(
                    response("v1/self/savingsaccounts", "{\"message\":\"Duplicate account\",\"arguments\":[" + arguments + "]}", 400, 400));
            for (JsonNode argument : output.get("arguments")) {
                if (argument.isObject()) {
                    assertEquals("account ****5678", argument.get("value").asText());
                } else {
                    assertTrue(argument.isTextual());
                    assertEquals("****5678", argument.asText());
                }
            }
        }
    }

    @Test
    void numericArgumentMatchingDoesNotCrossErrorObjectsOrMatchSubstrings() throws Exception {
        JsonNode output = new ObjectMapper().readTree(response("v1/self/loans",
                "{\"developerMessage\":\"Account 55555\",\"errors\":[" + "{\"message\":\"Account 12345678\",\"args\":[12345678]},"
                        + "{\"message\":\"Another error 9123456789\",\"args\":[12345678,55555,1234]}]}",
                400, 400));
        assertEquals("****5678", output.at("/errors/0/args/0").asText());
        for (JsonNode argument : output.at("/errors/1/args")) {
            assertTrue(argument.isIntegralNumber());
        }
        assertEquals(12345678, output.at("/errors/1/args/0").asInt());
        assertEquals(55555, output.at("/errors/1/args/1").asInt());
        assertEquals(1234, output.at("/errors/1/args/2").asInt());
    }

    @Test
    void nonJsonBodiesPassThroughIncludingWildcardMediaTypes() throws Exception {
        for (MediaType media : new MediaType[] { MediaType.TEXT_PLAIN_TYPE, MediaType.TEXT_HTML_TYPE,
                MediaType.APPLICATION_OCTET_STREAM_TYPE, MediaType.WILDCARD_TYPE, new MediaType("application", "*") }) {
            assertEquals("Registration accepted", response("v1/self/registration", "Registration accepted", 200, 200, media));
        }
    }

    @Test
    void blankAndMalformedJsonTypedBodiesStillFailClosed() throws Exception {
        for (String body : new String[] { "", " ", "invalid JSON" }) {
            assertTrue(response("v1/self/registration", body, 200, 500, new MediaType("application", "problem+json"))
                    .contains("Cannot safely serialize self-service response"));
        }
    }

    @Test
    void registrationSuccessHonoursItsDeclaredJsonMediaType() throws Exception {
        var serializer = new DefaultToApiJsonSerializer<org.apache.fineract.useradministration.domain.AppUser>(
                new ExcludeNothingWithPrettyPrintingOffJsonSerializerGoogleGson(), null, null);
        var resource = new SelfServiceRegistrationApiResource(mock(SelfServiceRegistrationWritePlatformService.class), serializer);
        String body = resource.createSelfServiceRegistrationRequest("{}");
        String media = SelfServiceRegistrationApiResource.class.getMethod("createSelfServiceRegistrationRequest", String.class)
                .getAnnotation(Produces.class).value()[0];
        String output = response("v1/self/registration", body, 200, 200, MediaType.valueOf(media));
        assertEquals(org.apache.fineract.portfolio.self.registration.SelfServiceApiConstants.createRequestSuccessMessage,
                new ObjectMapper().readTree(output).asText());
    }

    @Test
    void serializationFailuresLogStackTracesWithoutBodyOrExceptionMessages() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(SelfServiceAccountNumberResponseFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (Object body : new Object[] { "001234567890 malformed body", new FailingResponse() }) {
                assertTrue(response("v1/self/clients", body, 200, 500, MediaType.APPLICATION_JSON_TYPE)
                        .contains("Cannot safely serialize self-service response"));
            }
            assertEquals(2, appender.list.size());
            for (ILoggingEvent event : appender.list) {
                assertTrue(event.getThrowableProxy() != null);
                assertTrue(event.getThrowableProxy().getStackTraceElementProxyArray().length > 0);
                String diagnostic = event.getFormattedMessage() + ThrowableProxyUtil.asString(event.getThrowableProxy());
                assertFalse(diagnostic.contains("001234567890"));
                assertFalse(diagnostic.contains("malformed body"));
                assertFalse(diagnostic.contains("sensitive exception message"));
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static final class FailingResponse {

        public String getAccountNo() {
            throw new IllegalArgumentException("001234567890 sensitive exception message");
        }
    }
}
