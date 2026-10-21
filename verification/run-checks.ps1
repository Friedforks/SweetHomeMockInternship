# Run after mvn test, using the same JDK 8 or 11 as Maven.
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac' }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    & $javac -cp 'target/classes;SweetHome3D/lib/*' -d target/test-classes verification/RegressionChecks.java
    if ($LASTEXITCODE -ne 0) { throw 'Regression checks did not compile.' }
    & $java '-Djava.awt.headless=true' -cp 'target/test-classes;target/classes;SweetHome3D/lib/*' RegressionChecks
    if ($LASTEXITCODE -ne 0) { throw 'Regression checks failed.' }
} finally {
    Pop-Location
}
