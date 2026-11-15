param([string]$Scenario = "", [switch]$Stats, [string]$OutputDir = "results/demo")
$ErrorActionPreference = "Stop"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $jdk = Join-Path $env:USERPROFILE ".jdk/jdk-25.0.2"
    if (Test-Path $jdk) { $env:JAVA_HOME = $jdk; $env:Path = "$jdk/bin;$env:Path" }
    & mvn -q compile "-Dcoe619.build.directory=target/meeting-build" dependency:build-classpath "-Dmdep.outputFile=target/meeting-build/classpath.txt"
    if ($LASTEXITCODE -ne 0) { throw "Maven failed" }
    $cp = "target/meeting-build/classes;" + (Get-Content target/meeting-build/classpath.txt -Raw).Trim()
    if ($Stats) { & java -cp $cp kfupm.coe619.TraceStats }
    elseif ($Scenario) { & java -cp $cp kfupm.coe619.ExperimentRunner $Scenario --output $OutputDir }
    else { & java -cp $cp kfupm.coe619.ExperimentRunner --output $OutputDir }
    if ($LASTEXITCODE -ne 0) { throw "Java failed" }
} finally { Pop-Location }
