#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#

# Multi-stage build for the carved-out Fund microservice.
#
# Build context is the repository root (see docker-compose-fund-service.yml) so the
# Gradle build can resolve settings.gradle / buildSrc and the git-versioning plugin
# (.git must be present). Only the :fineract-fund-service:bootJar task is executed,
# so the heavy upstream monolith modules are never built.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
# Retry once to tolerate transient Gradle plugin/dependency download flakes.
RUN ./gradlew --no-daemon :fineract-fund-service:bootJar \
    || ./gradlew --no-daemon :fineract-fund-service:bootJar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# The bootable (fat) jar ends in -SNAPSHOT.jar; the -plain.jar is excluded by the glob.
COPY --from=build /workspace/fineract-fund-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
