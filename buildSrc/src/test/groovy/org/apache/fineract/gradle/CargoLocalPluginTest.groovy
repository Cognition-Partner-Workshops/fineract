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
package org.apache.fineract.gradle

import com.bmuschko.gradle.cargo.convention.BinFile
import com.bmuschko.gradle.cargo.convention.ConfigFile
import com.bmuschko.gradle.cargo.convention.Deployable
import org.gradle.api.InvalidUserDataException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class CargoLocalPluginTest {

    private final project = ProjectBuilder.builder().build()

    private def task() {
        project.pluginManager.apply(CargoLocalPlugin)
        def task = project.tasks.getByName('cargoStartLocal')
        def war = new File(project.projectDir, 'test.war')
        war.createNewFile()
        task.deployables = [new Deployable(files: project.files(war))]
        task
    }

    @Test
    void validatesLocalTasksWithoutLegacySuperCalls() {
        task()
        ['cargoConfigureLocal', 'cargoRedeployLocal', 'cargoRunLocal', 'cargoStartLocal', 'cargoStopLocal'].each { name ->
            def local = project.tasks.getByName(name)
            local.deployables = project.tasks.getByName('cargoStartLocal').deployables
            local.validateConfiguration()
        }
    }

    @Test
    void rejectsEmptyDeployables() {
        def local = task()
        local.deployables = []
        assertThrows(InvalidUserDataException) { local.validateConfiguration() }
    }

    @Test
    void rejectsMissingDeployable() {
        def local = task()
        local.deployables = [new Deployable(files: project.files('missing.war'))]
        assertThrows(InvalidUserDataException) { local.validateConfiguration() }
    }

    @Test
    void rejectsMissingConfigFile() {
        def local = task()
        local.configFiles = [new ConfigFile(files: project.files('missing.xml'), toDir: 'conf')]
        assertThrows(InvalidUserDataException) { local.validateConfiguration() }
    }

    @Test
    void rejectsMissingBinaryFile() {
        def local = task()
        local.files = [new BinFile(file: new File(project.projectDir, 'missing.jar'), toDir: 'lib')]
        assertThrows(InvalidUserDataException) { local.validateConfiguration() }
    }

    @Test
    void rejectsUnsupportedConfigurationType() {
        def local = task()
        local.configType = 'invalid'
        assertThrows(InvalidUserDataException) { local.validateConfiguration() }
    }
}
