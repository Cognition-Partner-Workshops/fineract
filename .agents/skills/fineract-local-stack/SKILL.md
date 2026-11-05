---
name: fineract-local-stack
description: Build and run Fineract locally with PostgreSQL on Linux or Windows, use the Maven mirror, and collect unit-test coverage.
---

# Run Fineract locally

Run commands from the repository root and keep PostgreSQL and the backend running in separate terminal sessions. The default tenant uses `mifos` / `password` for local API access.

## Linux prerequisites

Use Docker Engine with Compose and JDK 21. On a machine with SDKMAN installed:

```bash
source ~/.sdkman/bin/sdkman-init.sh
sdk list java
# Select an installed JDK 21; adjust the version directory when necessary.
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.7-tem"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
docker compose version
```

Explicit `JAVA_HOME` and `PATH` exports avoid a system Java 11 installation taking precedence over SDKMAN.

## Windows prerequisites

Windows Devin machines can start with Java absent. Run PowerShell as administrator for installation:

```powershell
choco install temurin21 -y
```

Open a fresh PowerShell session, or set the installed JDK path explicitly:

```powershell
$env:JAVA_HOME = (Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' | Sort-Object Name -Descending | Select-Object -First 1).FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
docker compose version
```

Start Docker Desktop with the WSL2 Linux-container engine before running Compose. Run the backend commands below in PowerShell; call Node package commands as `npm.cmd` when starting the separate web app.

## PostgreSQL and build

Start the database from the repository root on either platform:

```sh
docker compose -f docker-compose-postgresql.yml up -d db
docker compose -f docker-compose-postgresql.yml ps db
```

Wait for the database container to become healthy. Compose initializes `fineract_tenants` and `fineract_default` using the local configuration under `config/docker`. Keep the configuration's sample credentials confined to local development.

Maven Central can return HTTP 429 on hosted machines. The included init script puts Google's Maven Central mirror ahead of repositories that redirect to Central, including Gradle Plugin Portal. Pass the init script on every Gradle invocation.

Linux:

```bash
./gradlew -I .agents/skills/fineract-local-stack/maven-mirror.gradle :fineract-provider:bootJar --max-workers=4
```

PowerShell:

```powershell
.\gradlew.bat -I .agents/skills/fineract-local-stack/maven-mirror.gradle :fineract-provider:bootJar --max-workers=4
```

## Start the HTTPS backend

Load the Compose environment as data, preserving values containing spaces. Override the container hostname `db` with `localhost` when running Java on the host. The external PostgreSQL JDBC driver is downloaded by Gradle and supplied through `loader.path`.

Linux:

```bash
for file in config/docker/env/fineract-common.env config/docker/env/fineract-postgresql.env; do
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[A-Z_][A-Z_0-9]*= ]] || continue
    name=${line%%=*}
    value=${line#*=}
    value=${value#\"}
    value=${value%\"}
    export "$name=$value"
  done < "$file"
done
export FINERACT_HIKARI_JDBC_URL=jdbc:postgresql://localhost:5432/fineract_tenants
export FINERACT_DEFAULT_TENANTDB_HOSTNAME=localhost
driver=$(find "$HOME/.gradle/caches" -name 'postgresql-*.jar' -print -quit)
jar=$(find fineract-provider/build/libs -maxdepth 1 -name 'fineract-provider-*.jar' -print -quit)
java -Dloader.path="$(dirname "$driver")" -jar "$jar"
```

PowerShell:

```powershell
foreach ($file in @('config/docker/env/fineract-common.env', 'config/docker/env/fineract-postgresql.env')) {
  Get-Content $file | ForEach-Object {
    if ($_ -match '^([A-Z_][A-Z_0-9]*)=(.*)$') {
      [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim('"'), 'Process')
    }
  }
}
$env:FINERACT_HIKARI_JDBC_URL = 'jdbc:postgresql://localhost:5432/fineract_tenants'
$env:FINERACT_DEFAULT_TENANTDB_HOSTNAME = 'localhost'
$driver = Get-ChildItem "$HOME\.gradle\caches" -Recurse -Filter 'postgresql-*.jar' | Select-Object -First 1
$jar = Get-ChildItem 'fineract-provider/build/libs/fineract-provider-*.jar' | Select-Object -First 1
java "-Dloader.path=$($driver.DirectoryName)" -jar $jar.FullName
```

Wait for `Started ServerApplication` and the HTTPS listener on 8443. Initial startup runs database migrations. In another terminal, check an authenticated tenant endpoint:

```bash
curl -k -u mifos:password -H 'Fineract-Platform-TenantId: default' https://localhost:8443/fineract-provider/api/v1/clients
```

Use `curl.exe` with the same arguments in PowerShell to avoid the PowerShell alias. An empty database returns `{"totalFilteredRecords":0,"pageItems":[]}`. The `-k` option accepts the local self-signed certificate.

## Unit tests and JaCoCo

Run unit tests and generate coverage reports from a Linux terminal with JDK 21 selected.

```bash
./gradlew -I .agents/skills/fineract-local-stack/maven-mirror.gradle :fineract-loan:test :fineract-savings:test :fineract-provider:test :fineract-loan:jacocoTestReport :fineract-savings:jacocoTestReport :fineract-provider:jacocoTestReport --max-workers=4
```

Replace `./gradlew` with `.\gradlew.bat` in PowerShell and keep the same arguments. Request tests explicitly because `jacocoTestReport` does not run tests automatically.

Each module writes HTML to `build/code-coverage/index.html`, XML to `build/reports/jacoco/test/jacocoTestReport.xml`, and execution data to `build/jacoco/test.exec`. For a coverage percentage, divide the XML root counter's `covered` value by `covered + missed`; use `type="LINE"` for line coverage or `type="BRANCH"` for branch coverage.

Module reports include their own unit-test execution data. To count provider tests exercising loan or savings classes, aggregate execution data with all relevant modules' class and source directories. HTTP tests against a separate backend require a JaCoCo agent on the backend JVM. The `integration-tests` module starts Cargo Tomcat by default; use a separate database for integration runs and account for the 8443 port conflict before starting Cargo.
