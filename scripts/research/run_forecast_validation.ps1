param(
    [Parameter(Mandatory=$true)][ValidatePattern('^2011[0-9]{4}$')][string]$Day,
    [ValidateSet('online','half','control','online_guard','mean_guard','robust_guard')][string]$Model = 'online',
    [string]$Label = '',
    [switch]$StablePlanning,
    [switch]$ReadOnlyUnderload,
    [ValidateSet("","diurnal","real_a","real_b","real_c","heavy")][string]$Scenario = "",
    [switch]$FullDay,
    [switch]$AuditPower,
    [switch]$AuditPlacement,
    [switch]$AuditMetrics,
    [switch]$DecisionAudit,
    [switch]$NoMigrations,
    [switch]$MixedJobs,
    [switch]$AuditForecasts,
    [switch]$AllHostForecasts,
    [ValidateRange(0,86400)][int]$IdleShutdownSeconds=0,
    [switch]$PowerGuard=$true
)
$ErrorActionPreference = 'Stop'
if (($AuditForecasts -or $AllHostForecasts) -and $Model -ne 'robust_guard') {
    throw 'This forecast-scope audit currently supports robust_guard only'
}
if ($AuditMetrics -and !$MixedJobs) { throw 'Migration metric audit currently requires exact-window MixedJobs observation' }
$codeRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location $codeRoot
$priorJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $root = "results/predictor_investigation/online_holdout_$Day"
    if (!(Test-Path -LiteralPath "$root/provenance.json")) { throw 'Prepare fixed workload manifests first' }
    if (!$Label) { $Label = $Model }
    if ($Label -notmatch '^[a-z0-9_-]+$') { throw 'Use a simple run label' }
    $output = "$root/$Label"
    if (Test-Path -LiteralPath "$output/experiments.csv") { throw 'Do not overwrite an existing experiment' }
    $properties = '-Dcoe619.spacedHistory=true -Dcoe619.evaluateForecast=true'
    if ($Model -in @('online','online_guard','mean_guard','robust_guard')) { $properties += ' -Dcoe619.onlineForecast=true' }
    elseif ($Model -eq 'half') { $properties += ' -Dcoe619.dampedForecast=true' }
    else { $properties += ' -Dcoe619.disableForecast=true' }
    if ($Model -in @('online_guard','mean_guard','robust_guard')) { $properties += ' -Dcoe619.planningHistoryGuard=true' }
    if ($Model -eq 'mean_guard') { $properties += ' -Dcoe619.meanForecast=true' }
    if ($Model -eq 'robust_guard') { $properties += ' -Dcoe619.robustForecast=true' }
    if ($ReadOnlyUnderload) { $properties += ' -Dcoe619.auditPlanning=true' }
    if ($FullDay) { $properties += ' -Dcoe619.fullDay=true' }
    if ($AuditMetrics) { $properties += " -Dcoe619.metricAuditDirectory=$output/metric_audit" }
    if ($AuditPlacement) { $properties += ' -Dcoe619.auditPlacement=true' }
    if ($AuditPower) { $properties += ' -Dcoe619.auditPower=true' }
    if ($DecisionAudit) { $properties += ' -Dcoe619.predictorDiagnostics=true' }
    if ($NoMigrations) { $properties += ' -Dcoe619.noMigrations=true' }
    if ($MixedJobs) { $properties += ' -Dcoe619.mixedJobs=true' }
    if ($AuditForecasts) { $properties += " -Dcoe619.forecastAuditDirectory=$output/host_observations" }
    if ($AllHostForecasts) { $properties += ' -Dcoe619.liveForecastOnly=false' }
    $properties += " -Dcoe619.idleShutdownSeconds=$IdleShutdownSeconds"
    $properties += " -Dcoe619.powerGuard=$($PowerGuard.IsPresent.ToString().ToLowerInvariant())"
    $properties += " -Dcoe619.researchWorkloads=$root/manifests"
    $csvBefore = (Get-FileHash -LiteralPath results/experiments.csv -Algorithm SHA256).Hash
    $reportBefore = (Get-FileHash -LiteralPath docs/progress_report_2/PR2_Progress_Report_II.pdf -Algorithm SHA256).Hash
    $currentReports = @{}
    foreach ($extension in @('docx','pdf')) {
        $path = "docs/progress_report_2/coordinator_template/PR2_202045160.$extension"
        $currentReports[$path] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    }
    $log = "target/forecast-$Day-$Label.log"
    New-Item -ItemType Directory -Path target -Force | Out-Null
    $started = (Get-Date).ToUniversalTime().ToString('o')
    $env:JAVA_TOOL_OPTIONS = $properties
    & ./scripts/run.ps1 -FrameworkFix -StablePlanning:$StablePlanning -ReadOnlyUnderload:$ReadOnlyUnderload -Scenario $Scenario -OutputDir $output *> $log
    if ($LASTEXITCODE -ne 0) { throw 'Simulation failed; retain target log for diagnosis' }
    if ((Get-FileHash -LiteralPath results/experiments.csv -Algorithm SHA256).Hash -ne $csvBefore) { throw 'Frozen CSV changed' }
    if ((Get-FileHash -LiteralPath docs/progress_report_2/PR2_Progress_Report_II.pdf -Algorithm SHA256).Hash -ne $reportBefore) { throw 'Frozen PR2 changed' }
    foreach ($path in $currentReports.Keys) {
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $currentReports[$path]) { throw "Frozen current PR2 changed: $path" }
    }
    $runMetadata = [ordered]@{
        started_utc=$started; completed_utc=(Get-Date).ToUniversalTime().ToString('o')
        day=$Day; model=$Model; java_options=$properties; framework_fix=$true; stable_planning=$StablePlanning.IsPresent; full_day=$FullDay.IsPresent
        cloudlet_length_mi_per_pe=$(if ($FullDay) { 1000000000 } else { 20000000 })
        read_only_underload=$ReadOnlyUnderload.IsPresent; scenario=$Scenario
        metric_audit=$AuditMetrics.IsPresent
        placement_audit=$AuditPlacement.IsPresent
        power_audit=$AuditPower.IsPresent; idle_shutdown_seconds=$IdleShutdownSeconds
        decision_audit=$DecisionAudit.IsPresent; migrations_disabled=$NoMigrations.IsPresent; mixed_jobs=$MixedJobs.IsPresent
        energy_demand_window_seconds=$(if ($MixedJobs) { @(0,86400) } else { $null })
        short_cloudlet_length_mi_per_pe=$(if ($MixedJobs) { 5000000 } else { $null })
        completed_vm_destruction_delay_seconds=$(if ($MixedJobs) { 1 } else { $null })
        observation_mode=$(if ($MixedJobs) { 'synchronous_exact_window' } else { 'graceful_recorded_histories' })
        forecast_observations=$AuditForecasts.IsPresent; all_host_forecasts=$AllHostForecasts.IsPresent
        power_guard=$PowerGuard.IsPresent
        frozen_csv_sha256=$csvBefore; frozen_pr2_sha256=$reportBefore
        frozen_current_reports=$currentReports
        code_commit=(& git rev-parse HEAD); code_status=(@(& git status --short))
    }
    $runMetadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$output/run.json" -Encoding utf8
    & python -c "import gzip,pathlib,sys; p=pathlib.Path(sys.argv[1]); q=pathlib.Path(sys.argv[2]); gzip.open(q,'wb').write(p.read_bytes()); p.unlink()" $log "$output/simulation.log.gz"
    if ($LASTEXITCODE -ne 0) { throw 'Log compression failed' }
    Write-Output "Completed $Day $Label; frozen report and CSV verified"
} finally {
    $env:JAVA_TOOL_OPTIONS = $priorJavaOptions
    Pop-Location
}
