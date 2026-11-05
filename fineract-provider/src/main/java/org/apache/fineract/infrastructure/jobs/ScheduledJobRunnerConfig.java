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
package org.apache.fineract.infrastructure.jobs;

import java.util.List;
import org.apache.fineract.infrastructure.core.persistence.ExtendedJpaTransactionManager;
import org.apache.fineract.infrastructure.core.persistence.TransactionLifecycleCallback;
import org.apache.fineract.infrastructure.core.service.database.RoutingDataSource;
import org.apache.fineract.infrastructure.jobs.config.FineractDataFieldMaxValueIncrementerFactory;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.support.JobRegistrySmartInitializingSingleton;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.dao.Jackson2ExecutionContextStringSerializer;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.batch.infrastructure.item.database.support.DataFieldMaxValueIncrementerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableBatchProcessing
public class ScheduledJobRunnerConfig {

    @Bean
    public PlatformTransactionManager transactionManager(ObjectProvider<TransactionManagerCustomizers> transactionManagerCustomizers,
            List<TransactionLifecycleCallback> callbacks) {
        ExtendedJpaTransactionManager transactionManager = new ExtendedJpaTransactionManager();
        transactionManager.setLifecycleCallbacks(callbacks);
        transactionManager.setValidateExistingTransaction(true);
        transactionManagerCustomizers.ifAvailable(customizers -> customizers.customize(transactionManager));
        return transactionManager;
    }

    @Bean
    public Jackson2ExecutionContextStringSerializer executionContextSerializer() {
        return new Jackson2ExecutionContextStringSerializer();
    }

    @Bean
    public DataFieldMaxValueIncrementerFactory incrementerFactory(RoutingDataSource routingDataSource) {
        // The DefaultDataFieldMaxValueIncrementerFactory has to be overridden because Spring 6 introduced
        // a new MariaDB incrementer that's incompatible with Spring Batch 4.x
        return new FineractDataFieldMaxValueIncrementerFactory(routingDataSource);
    }

    @Bean
    public JobRepository jobRepository(RoutingDataSource routingDataSource, PlatformTransactionManager transactionManager,
            Jackson2ExecutionContextStringSerializer executionContextSerializer, DataFieldMaxValueIncrementerFactory incrementerFactory,
            @Value("${spring.batch.jdbc.table-prefix:BATCH_}") String tablePrefix) throws Exception {
        JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
        factory.setDataSource(routingDataSource);
        factory.setTransactionManager(transactionManager);
        factory.setIsolationLevelForCreate("ISOLATION_READ_COMMITTED");
        factory.setSerializer(executionContextSerializer);
        factory.setIncrementerFactory(incrementerFactory);
        factory.setTablePrefix(tablePrefix);
        factory.setJobInstanceIncrementerName("JOB_SEQ");
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    @Bean
    public JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }

    @Bean
    public JobRegistrySmartInitializingSingleton jobRegistryInitializer(JobRegistry jobRegistry) {
        return new JobRegistrySmartInitializingSingleton(jobRegistry);
    }

    @Bean
    public TaskExecutorJobOperator jobOperator(JobRepository jobRepository, JobRegistry jobRegistry) throws Exception {
        TaskExecutorJobOperator operator = new TaskExecutorJobOperator();
        operator.setJobRepository(jobRepository);
        operator.setJobRegistry(jobRegistry);
        operator.afterPropertiesSet();
        return operator;
    }
}
