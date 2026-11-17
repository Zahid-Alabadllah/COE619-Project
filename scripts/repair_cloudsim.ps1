param([switch]$StablePlanning)
$ErrorActionPreference = "Stop"
$codeRoot = Split-Path $PSScriptRoot -Parent
Push-Location $codeRoot
try {
    & mvn -q dependency:get "-Dartifact=org.ow2.asm:asm:9.7.1"
    if ($LASTEXITCODE -ne 0) { throw "Cannot obtain pinned patch-tool dependency" }
    $asmJar = Join-Path $env:USERPROFILE ".m2/repository/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar"
    $simJar = Join-Path $env:USERPROFILE ".m2/repository/org/cloudsimplus/cloudsim-plus/8.0.0/cloudsim-plus-8.0.0.jar"
    $originalHash = (Get-FileHash -LiteralPath $simJar -Algorithm SHA256).Hash
    & javac --release 17 -cp $asmJar -d target/framework-patch scripts/framework/MutableMigrationMapPatch.java
    if ($LASTEXITCODE -ne 0) { throw "Patch tool compilation failed" }
    & java -cp "target/framework-patch;$asmJar" MutableMigrationMapPatch $simJar target/meeting-build/classes $StablePlanning.IsPresent.ToString().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0) { throw "CloudSim compatibility repair failed" }
    if ((Get-FileHash -LiteralPath $simJar -Algorithm SHA256).Hash -ne $originalHash) { throw "Original dependency changed unexpectedly" }
} finally { Pop-Location }
