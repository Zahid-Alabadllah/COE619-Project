param([string]$Scenario = "", [int]$Seeds = 5, [switch]$Stats)
$ErrorActionPreference = "Stop"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $jdk = Join-Path $env:USERPROFILE ".jdk/jdk-25.0.2"
    if (Test-Path $jdk) { $env:JAVA_HOME = $jdk; $env:Path = "$jdk/bin;$env:Path" }
    & mvn -q compile dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
    if ($LASTEXITCODE -ne 0) { throw "Maven failed" }
    $cp = "target/classes;" + (Get-Content target/classpath.txt -Raw).Trim()
    if ($Stats) { & java -cp $cp kfupm.coe619.TraceStats }
    elseif ($Scenario) { & java -cp $cp kfupm.coe619.ExperimentRunner $Scenario --seeds $Seeds }
    else { & java -cp $cp kfupm.coe619.ExperimentRunner --seeds $Seeds }
    if ($LASTEXITCODE -ne 0) { throw "Java failed" }
} finally { Pop-Location }
