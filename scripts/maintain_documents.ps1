param([switch]$Clean, [switch]$DryRun)
$ErrorActionPreference = "Stop"
$taskArguments = @((Join-Path $PSScriptRoot "maintain_documents.py"))
if ($Clean) { $taskArguments += "--clean" }
if ($DryRun) { $taskArguments += "--dry-run" }
& python @taskArguments
if ($LASTEXITCODE -ne 0) { throw "Document maintenance failed" }
