param(
    [Parameter(Mandatory=$true)][ValidatePattern('^2011[0-9]{4}$')][string]$Day,
    [ValidateSet('online','half','control')][string]$Model = 'online',
    [string]$Label = '',
    [switch]$StablePlanning
)
$ErrorActionPreference = 'Stop'
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
    if ($Model -eq 'online') { $properties += ' -Dcoe619.onlineForecast=true' }
    elseif ($Model -eq 'half') { $properties += ' -Dcoe619.dampedForecast=true' }
    else { $properties += ' -Dcoe619.disableForecast=true' }
    $properties += " -Dcoe619.researchWorkloads=$root/manifests"
    $csvBefore = (Get-FileHash -LiteralPath results/experiments.csv -Algorithm SHA256).Hash
    $reportBefore = (Get-FileHash -LiteralPath docs/progress_report_2/PR2_Progress_Report_II.pdf -Algorithm SHA256).Hash
    $log = "target/forecast-$Day-$Label.log"
    New-Item -ItemType Directory -Path target -Force | Out-Null
    $started = (Get-Date).ToUniversalTime().ToString('o')
    $env:JAVA_TOOL_OPTIONS = $properties
    & ./scripts/run.ps1 -FrameworkFix -StablePlanning:$StablePlanning -OutputDir $output *> $log
    if ($LASTEXITCODE -ne 0) { throw 'Simulation failed; retain target log for diagnosis' }
    if ((Get-FileHash -LiteralPath results/experiments.csv -Algorithm SHA256).Hash -ne $csvBefore) { throw 'Frozen CSV changed' }
    if ((Get-FileHash -LiteralPath docs/progress_report_2/PR2_Progress_Report_II.pdf -Algorithm SHA256).Hash -ne $reportBefore) { throw 'Frozen PR2 changed' }
    $runMetadata = [ordered]@{
        started_utc=$started; completed_utc=(Get-Date).ToUniversalTime().ToString('o')
        day=$Day; model=$Model; java_options=$properties; framework_fix=$true; stable_planning=$StablePlanning.IsPresent
        frozen_csv_sha256=$csvBefore; frozen_pr2_sha256=$reportBefore
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
