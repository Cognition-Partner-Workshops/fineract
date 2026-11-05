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
package org.apache.fineract.infrastructure.event.external.producer.jms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.Session;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.messaging.jms.MessageFactory;
import org.apache.fineract.infrastructure.core.service.HashingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;

class JMSMultiExternalEventProducerInterruptTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void interruptedWhileWaitingForSendingRestoresInterruptStatus() throws Exception {
        FineractProperties.FineractExternalEventsProducerJmsProperties jms = new FineractProperties.FineractExternalEventsProducerJmsProperties();
        jms.setProducerCount(1);
        FineractProperties.FineractExternalEventsProducerProperties producer = new FineractProperties.FineractExternalEventsProducerProperties();
        producer.setJms(jms);
        FineractProperties.FineractExternalEventsProperties external = new FineractProperties.FineractExternalEventsProperties();
        external.setProducer(producer);
        FineractProperties.FineractEventsProperties events = new FineractProperties.FineractEventsProperties();
        events.setExternal(external);
        FineractProperties fineractProperties = new FineractProperties();
        fineractProperties.setEvents(events);

        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Session session = mock(Session.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(session);
        HashingService hashingService = mock(HashingService.class);
        when(hashingService.consistentHash(anyLong(), anyInt())).thenReturn(0);
        Future<?> future = mock(Future.class);
        when(future.get()).thenThrow(new InterruptedException("interrupted"));
        AsyncTaskExecutor taskExecutor = mock(AsyncTaskExecutor.class);
        doReturn(future).when(taskExecutor).submit(any(Runnable.class));

        JMSMultiExternalEventProducer underTest = new JMSMultiExternalEventProducer(mock(Destination.class), connectionFactory,
                mock(MessageFactory.class), taskExecutor, hashingService, fineractProperties);

        assertThatThrownBy(() -> underTest.sendEvents(Map.of(1L, List.of(new byte[0])))).isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
