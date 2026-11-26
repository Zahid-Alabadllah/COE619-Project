param([ValidateSet('repaired','legacy','no_migrations')][string]$Mode='repaired', [ValidatePattern('^[a-z0-9_-]*$')][string]$Label='')
$ErrorActionPreference='Stop'
$codeRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location $codeRoot
$previousOptions=$env:JAVA_TOOL_OPTIONS
try {
 $root='results/predictor_investigation/read_only_underload'
 $output="$root/controlled_$Mode"
 if($Label) {$output+="_$Label"}
 if (Test-Path $output) {throw 'Do not overwrite a completed check'}
 New-Item -ItemType Directory -Path $output -Force | Out-Null
 $env:JAVA_TOOL_OPTIONS="-Dcoe619.spacedHistory=true -Dcoe619.evaluateForecast=true -Dcoe619.onlineForecast=true -Dcoe619.planningHistoryGuard=true -Dcoe619.robustForecast=true -Dcoe619.mixedJobs=true -Dcoe619.metricAuditDirectory=$output/metric_audit -Dcoe619.auditPower=true -Dcoe619.auditPlacement=true -Dcoe619.auditPlanning=true -Dcoe619.predictorDiagnostics=true -Dcoe619.forecastAuditDirectory=$output/host_observations"
 if($Mode -eq 'no_migrations') {$env:JAVA_TOOL_OPTIONS+=' -Dcoe619.noMigrations=true'}
 $started=(Get-Date).ToUniversalTime().ToString('o')
 & ./scripts/run.ps1 -PrepareDebug -ReadOnlyUnderload:($Mode -ne 'legacy') *> "$output/build.log"
 if($LASTEXITCODE -ne 0) {throw 'Check build failed'}
 $cp='target/meeting-build/classes;'+(Get-Content target/meeting-build/classpath.txt -Raw).Trim()
 & javac --release 17 -cp $cp -d target/meeting-build/classes scripts/research/UnderloadIntegrationCheck.java
 if($LASTEXITCODE -ne 0) {throw 'Check compilation failed'}
 & java -cp $cp kfupm.coe619.UnderloadIntegrationCheck "$root/controlled_inputs" $output *> "$output/simulation.log"
 $exitCode=$LASTEXITCODE
 if($Mode -eq 'legacy') {
   if($exitCode -eq 0 -or !(Select-String -LiteralPath "$output/simulation.log" -Pattern 'Invalid end-window placement' -Quiet)) {throw 'Expected legacy residency failure not reproduced'}
 } elseif($exitCode -ne 0) {throw 'Repaired/control integration failed'}
 @{
   mode=$Mode; started_utc=$started; completed_utc=(Get-Date).ToUniversalTime().ToString('o')
   java_options=$env:JAVA_TOOL_OPTIONS; code_commit=(& git rev-parse HEAD)
   expected_legacy_failure=($Mode -eq 'legacy'); exit_code=$exitCode
   planner_sha256=(Get-FileHash scripts/research/UnderloadReservationPlanner.java -Algorithm SHA256).Hash
 } | ConvertTo-Json | Set-Content "$output/run.json" -Encoding utf8
 & python -c "import gzip,pathlib,sys; p=pathlib.Path(sys.argv[1]); gzip.open(str(p)+'.gz','wb').write(p.read_bytes()); p.unlink()" "$output/simulation.log"
 if($LASTEXITCODE -ne 0) {throw 'Log compression failed'}
 Write-Output "Controlled underload check completed: $Mode"
} finally {
 & python scripts/framework/clear_patch.py
 $env:JAVA_TOOL_OPTIONS=$previousOptions
 Pop-Location
}
