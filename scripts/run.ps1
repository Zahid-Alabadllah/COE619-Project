param([switch]$FrameworkFix, [switch]$StablePlanning, [string]$Scenario = "", [string]$OutputDir = "results/demo")
$ErrorActionPreference = "Stop"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if ($StablePlanning -and !$FrameworkFix) { throw 'StablePlanning requires the explicit FrameworkFix research run' }
    $jdk = Join-Path $env:USERPROFILE ".jdk/jdk-25.0.2"
    if (Test-Path $jdk) { $env:JAVA_HOME = $jdk; $env:Path = "$jdk/bin;$env:Path" }
    & python (Join-Path $PSScriptRoot "framework/clear_patch.py")
    if ($LASTEXITCODE -ne 0) { throw "Cannot clear stale framework override" }
    & mvn -q compile "-Dcoe619.build.directory=target/meeting-build" dependency:build-classpath "-Dmdep.outputFile=target/meeting-build/classpath.txt"
    if ($LASTEXITCODE -ne 0) { throw "Maven failed" }
    if ($FrameworkFix) { & (Join-Path $PSScriptRoot "repair_cloudsim.ps1") -StablePlanning:$StablePlanning }
    $cp = "target/meeting-build/classes;" + (Get-Content target/meeting-build/classpath.txt -Raw).Trim()
    if ($Scenario) { & java -cp $cp kfupm.coe619.ExperimentRunner $Scenario --output $OutputDir }
    else { & java -cp $cp kfupm.coe619.ExperimentRunner --output $OutputDir }
    if ($LASTEXITCODE -ne 0) { throw "Java failed" }
} finally {
    & python (Join-Path $PSScriptRoot "framework/clear_patch.py")
    Pop-Location
}
