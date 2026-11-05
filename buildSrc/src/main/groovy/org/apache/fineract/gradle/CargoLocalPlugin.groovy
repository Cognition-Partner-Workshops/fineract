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

import com.bmuschko.gradle.cargo.convention.CargoPluginExtension
import com.bmuschko.gradle.cargo.convention.Deployable
import com.bmuschko.gradle.cargo.tasks.AbstractCargoContainerTask
import com.bmuschko.gradle.cargo.tasks.local.CargoConfigureLocal
import com.bmuschko.gradle.cargo.tasks.local.CargoRedeployLocal
import com.bmuschko.gradle.cargo.tasks.local.CargoRunLocal
import com.bmuschko.gradle.cargo.tasks.local.CargoStartLocal
import com.bmuschko.gradle.cargo.tasks.local.CargoStopLocal
import com.bmuschko.gradle.cargo.tasks.local.LocalCargoContainerTask
import com.bmuschko.gradle.cargo.tasks.remote.CargoDeployRemote
import com.bmuschko.gradle.cargo.tasks.remote.CargoRedeployRemote
import com.bmuschko.gradle.cargo.tasks.remote.CargoUndeployRemote
import com.bmuschko.gradle.cargo.tasks.remote.RemoteCargoContainerTask
import com.bmuschko.gradle.cargo.util.ProjectInfoHelper
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project

class CargoLocalPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply('com.bmuschko.cargo-base')
        def extension = project.extensions.create('cargo', CargoPluginExtension, project)
        project.tasks.withType(AbstractCargoContainerTask).configureEach { task ->
            task.conventionMapping.map('containerId') { extension.containerId }
            task.conventionMapping.map('port') { extension.port }
            task.conventionMapping.map('deployables') {
                def artifact = ProjectInfoHelper.getProjectDeployableTask(project)
                if (!extension.deployables) {
                    return artifact ? [new Deployable(files: artifact.outputs.files)] : []
                }
                extension.deployables.each { deployable ->
                    if (!deployable.files && artifact) {
                        deployable.files = artifact.outputs.files
                    }
                }
                extension.deployables
            }
        }
        project.tasks.withType(LocalCargoContainerTask).configureEach { task ->
            ['jvmArgs', 'logLevel', 'homeDir', 'configType', 'configHomeDir', 'outputFile', 'logFile',
             'rmiPort', 'zipUrlInstaller', 'configFiles', 'files', 'extraClasspath', 'sharedClasspath'].each { name ->
                task.conventionMapping.map(name) { extension.local.getProperty(name) }
            }
            task.conventionMapping.map('startStopTimeout') { extension.startStopTimeout }
            task.conventionMapping.map('containerProperties') { extension.local.containerProperties.properties }
            task.conventionMapping.map('systemProperties') { extension.local.systemProperties.properties }
        }
        project.tasks.withType(RemoteCargoContainerTask).configureEach { task ->
            ['protocol', 'hostname', 'username', 'password'].each { name ->
                task.conventionMapping.map(name) { extension.remote.getProperty(name) }
            }
            task.conventionMapping.map('containerProperties') { extension.remote.containerProperties.properties }
        }
        project.tasks.register('cargoDeployRemote', CargoDeployRemote)
        project.tasks.register('cargoRedeployRemote', CargoRedeployRemote)
        project.tasks.register('cargoUndeployRemote', CargoUndeployRemote)
        project.tasks.register('cargoConfigureLocal', Configure)
        project.tasks.register('cargoRedeployLocal', Redeploy)
        project.tasks.register('cargoRunLocal', Run)
        project.tasks.register('cargoStartLocal', Start)
        project.tasks.register('cargoStopLocal', Stop)
    }

    static void validate(LocalCargoContainerTask task) {
        if (!task.deployables) {
            throw new InvalidUserDataException('No deployables assigned!')
        }
        task.deployables.each { deployable ->
            if (deployable.file && !deployable.file.exists()) {
                throw new InvalidUserDataException("Deployable ${deployable.file.canonicalPath} does not exist")
            }
        }
        task.configFiles.each { configFile ->
            configFile.files.each { file ->
                if (!file.exists()) {
                    throw new InvalidUserDataException("Config file ${file.canonicalPath} does not exist")
                }
            }
        }
        task.files.each { binFile ->
            if (!binFile.file || !binFile.file.exists()) {
                throw new InvalidUserDataException("Binary File ${binFile.file == null ? 'null' : binFile.file.canonicalPath} does not exist")
            }
        }
        if (task.configType && !(task.configType in ['standalone', 'existing'])) {
            throw new InvalidUserDataException("Configuration type ${task.configType} is not supported, use 'standalone' or 'existing'")
        }
    }

    static class Configure extends CargoConfigureLocal {
        @Override
        void validateConfiguration() {
            CargoLocalPlugin.validate(this)
        }
    }

    static class Redeploy extends CargoRedeployLocal {
        @Override
        void validateConfiguration() {
            CargoLocalPlugin.validate(this)
        }
    }

    static class Run extends CargoRunLocal {
        @Override
        void validateConfiguration() {
            CargoLocalPlugin.validate(this)
        }
    }

    static class Start extends CargoStartLocal {
        @Override
        void validateConfiguration() {
            CargoLocalPlugin.validate(this)
        }
    }

    static class Stop extends CargoStopLocal {
        @Override
        void validateConfiguration() {
            CargoLocalPlugin.validate(this)
        }
    }
}
