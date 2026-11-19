$ErrorActionPreference='Stop'
$root='C:\Users\zahed\OneDrive - KFUPM\MX HPCC\T261\COE619'
$stage=Join-Path $root 'COE619-Project-Code\target\pr2-template'
$out=Join-Path $root 'Course_Documents\05_Progress_Report_II\PR2_202045160.docx'
$w=New-Object -ComObject Word.Application
$w.Visible=$false
$w.DisplayAlerts=0
function Fill($cell,[string]$text){$r=$cell.Range.Duplicate; $r.End=$r.End-1; $r.Text=$text}
try {
 $d=$w.Documents.Open((Join-Path $PSScriptRoot 'Coordinator_Progress_Report_Template.docx'),$false,$false)
 $t=$d.Tables.Item(2)
 Fill $t.Cell(1,2) 'Energy-Aware Predictive Virtual Machine Consolidation in Cloud Data Centers'
 $r=$t.Cell(2,3).Range.Duplicate; $r.End=$r.Start+1; $r.Text=([string][char]0x2612); $r.Font.Name='Segoe UI Symbol'
 Fill $t.Cell(3,2) 'From: PR1 submission'
 Fill $t.Cell(3,3) 'To: 15 November 2026'
 Fill $t.Cell(4,2) 'Dr. Mohamed Faten Zhani'
 Fill $t.Cell(8,2) "Zahid Alabadllah | 202045160`rComputer Engineering Department | KFUPM"
 Fill $t.Cell(11,2) 'Term 261, Week 15'
 $content=@'
Implemented THR (80% CPU), adaptive IQR and an initial OLS predictor in Java and CloudSim Plus 8.0.0. OLS fits ten active CPU readings and forecasts five minutes ahead; before enough history exists, it uses THR.
Added idle-host shutdown and PlanetLab inputs: each VM follows its own five-minute CPU trace. One runner compares all policies on five fixed workloads and saves 15 result rows.
Prepared the concepts guide and advisor demonstration. The initial predictor showed no clear advantage. Quiet real cases had no migrations. Historical energy values need the accounting qualification below. Later predictor experiments are excluded.
'@
 Fill $d.Tables.Item(3).Cell(2,1) $content.Trim().Replace("`n","`r").Replace("`r`r","`r")
 # Keep the existing chart grid, merged cells, headers and legend. Remove sample data only.
 $chart=$d.Tables.Item(4)
 foreach($c in $chart.Range.Cells){ if($c.RowIndex -ge 3 -and $c.RowIndex -le 23 -and $c.ColumnIndex -le 24){ Fill $c ''; $c.Shading.BackgroundPatternColor=16777215 } }
 $tasks=@('Literature and simulation setup','THR and IQR baselines','Initial OLS predictor','Real workloads and common runner','PR2 and concepts guide','Forecast validation and refinement','Final evaluation and deliverables')
 $rows=@(3,6,9,12,15,18,21)
 $weeks=@(6,9,13,15,15,0,0)
 for($i=0;$i -lt 7;$i++){
   $status=if($i -lt 5){'Completed for PR2 (100%)'}else{'Planned for Term 262'}
   Fill $chart.Cell($rows[$i],25) ($tasks[$i]+"`r"+$status)
   if($weeks[$i] -gt 0){
     $col=25-$weeks[$i]
     foreach($c in $chart.Range.Cells){
       if($c.RowIndex -eq $rows[$i] -and $c.ColumnIndex -eq $col){$c.Shading.BackgroundPatternColor=12632256}
       if($c.RowIndex -eq ($rows[$i]+1) -and $c.ColumnIndex -eq $col){$c.Shading.BackgroundPatternColor=5287936}
     }
   }
 }
 Fill $d.Tables.Item(5).Cell(2,1) 'The original plan placed predictive-model design in Term 261 and implementation in Term 262. An initial OLS implementation was completed early, alongside the workload runner and guide. This is an implementation milestone, not proof of energy savings. The chart uses proposal milestone weeks (6, 9, 13 and 15) as planned checkpoints; green marks completion status at the PR2 cutoff, not exact completion dates. Later-term work is labelled in the task column and left unplotted because the next-term calendar is not confirmed. No dates or completion percentages for that work are invented.'
 Fill $d.Tables.Item(6).Cell(2,1) "The initial forecast can cause extra migrations and keep hosts active longer. Lower prediction error and lower energy are separate goals. Quiet workloads do not stress the policies enough to distinguish them. A later audit also found CPU processing on hosts recorded as off, so the original energy rankings need corrected reruns; no predictive energy-saving claim is made here.`rSLATAH means the share of each active host's time spent almost fully loaded (at least 99.9% CPU), averaged across hosts. PDM estimates work lost during migration using our assumed 10% penalty; SLAV = SLATAH x PDM. A zero product can hide migration costs. These research-specific measures follow Beloglazov and Buyya (2012), with implementation approximations; they are not universal SLA standards.`rFree paper: https://beloglazov.info/papers/2012-optimal-algorithms-ccpe.pdf`rScope: achievements through 15 November 2026; the later accounting caveat is disclosed without adding later predictor results. Missing signatures and unverified cover details are intentionally blank."
 $link=$d.Tables.Item(6).Cell(2,1).Range.Duplicate
 $link.Find.Text='https://beloglazov.info/papers/2012-optimal-algorithms-ccpe.pdf'
 if($link.Find.Execute()){[void]$d.Hyperlinks.Add($link,'https://beloglazov.info/papers/2012-optimal-algorithms-ccpe.pdf')}
 $d.Repaginate()
 $d.SaveAs2($out,16)
 $d.ExportAsFixedFormat(($out -replace '\.docx$','.pdf'),17)
 "Saved $out; pages=$($d.ComputeStatistics(2))"
 $d.Close(0)
} finally { $w.Quit() }







