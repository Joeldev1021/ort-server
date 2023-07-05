/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

val dockerImagePrefix: String by project

plugins {
    application

    alias(libs.plugins.jib)
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
}

group = "org.ossreviewtoolkit.server"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":api-v1"))
    implementation(project(":clients:keycloak"))
    implementation(project(":dao"))
    implementation(project(":model"))
    implementation(project(":secrets:secrets-spi"))
    implementation(project(":services"))
    implementation(project(":storage:storage-spi"))
    implementation(project(":transport:transport-spi"))

    implementation(libs.jsonSchemaSerialization)
    implementation(libs.koinKtor)
    implementation(libs.ktorKotlinxSerialization)
    implementation(libs.ktorServerAuth)
    implementation(libs.ktorServerAuthJwt)
    implementation(libs.ktorServerCallLogging)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerDefaultHeaders)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerStatusPages)
    implementation(libs.ktorSwaggerUi)

    runtimeOnly(project(":secrets:file"))
    runtimeOnly(project(":secrets:vault"))
    runtimeOnly(project(":storage:database"))
    runtimeOnly(project(":transport:activemqartemis"))
    runtimeOnly(project(":transport:rabbitmq"))

    runtimeOnly(libs.logback)

    testImplementation(testFixtures(project(":clients:keycloak")))
    testImplementation(testFixtures(project(":dao")))
    testImplementation(testFixtures(project(":secrets:secrets-spi")))

    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestAssertionsKtor)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.ktorClientContentNegotiation)
    testImplementation(libs.ktorClientCore)
    testImplementation(libs.ktorServerCommon)
    testImplementation(libs.ktorServerTestHost)
    testImplementation(libs.mockk)
}

jib {
    from.image = "eclipse-temurin:${libs.versions.eclipseTemurin.get()}"
    to.image = "${dockerImagePrefix}ort-server-core:latest"

    container {
        mainClass = "io.ktor.server.netty.EngineMain"
        creationTime.set("USE_CURRENT_TIMESTAMP")
    }
}
