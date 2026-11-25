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
 Fill $t.Cell(3,2) 'From: Term 261, Week 11'
 Fill $t.Cell(3,3) 'To: 15 November 2026'
 Fill $t.Cell(4,2) 'Dr. Mohamed Faten Zhani'
 Fill $t.Cell(8,2) "Zahid Alabadllah | 202045160`rComputer Engineering Department | KFUPM"
 Fill $t.Cell(5,2) 'Computer Engineering Department'
 Fill $t.Cell(11,2) '26 November 2026 (Week 15)'
 $content=@'
Extended the PR1 THR (80% CPU) and IQR baselines with unweighted ordinary least squares (OLS) forecasting in CloudSim Plus 8.0.0. Ten active host CPU readings and actual timestamps predict 300 seconds ahead; the 80% reactive check remains.
Added idle-host shutdown and PlanetLab traces (1,052-file pool). Five fixed cases cover day/night, three quiet real groups and heavy real workloads. Each VM keeps its five-minute CPU trace; one runner writes 15 policy-comparison rows.
Prepared the concepts guide and demonstration. Quiet cases had no migrations. No predictive energy benefit was established; Section B qualifies historical energy results.
'@
 Fill $d.Tables.Item(3).Cell(2,1) $content.Trim().Replace("`n","`r").Replace("`r`r","`r")
 # Keep the existing chart grid, merged cells, headers and legend. Remove sample data only.
 $chart=$d.Tables.Item(4)
 foreach($c in $chart.Range.Cells){ if($c.RowIndex -ge 3 -and $c.RowIndex -le 23 -and $c.ColumnIndex -le 24){ Fill $c ''; $c.Shading.BackgroundPatternColor=16777215 } }
 $tasks=@('Literature and simulation setup','THR and IQR baselines','Model design and initial OLS','Real workloads and common runner','PR2 and concepts guide','Forecast validation and refinement','Final evaluation and deliverables')
 $rows=@(3,6,9,12,15,18,21)
 $weeks=@(6,9,13,15,15,0,0)
 for($i=0;$i -lt 7;$i++){
   $status=if($i -lt 5){'Completed for PR2 (100%)'}else{'Planned for Term 262'}
   Fill $chart.Cell($rows[$i],25) ($tasks[$i]+"`r"+$status)
   if($weeks[$i] -gt 0){
     $col=25-$weeks[$i]
     foreach($c in $chart.Range.Cells){
       if($c.RowIndex -eq $rows[$i] -and $c.ColumnIndex -eq $col){$c.Shading.BackgroundPatternColor=12632256}
       if($c.RowIndex -eq ($rows[$i]+1) -and $c.ColumnIndex -eq 10){$c.Shading.BackgroundPatternColor=5287936}
     }
   }
 }
 Fill $d.Tables.Item(5).Cell(2,1) "The proposal scheduled model design for Term 261, Week 13, and implementation for Term 262. I completed an initial OLS implementation early, together with the shared workload runner and concepts guide. Implementation is complete for this initial version; demonstrating a forecasting benefit remains an evaluation goal.`rGrey chart cells show planned checkpoints. Green cells at Week 15 show completion verified for this report, not exact completion dates. The chart describes work completed by 15 November 2026. Forecast refinement and final evaluation remain outside this report and are labelled Term 262 without assigning unconfirmed calendar dates."
 Fill $d.Tables.Item(6).Cell(2,1) "The initial OLS predictor can trigger extra moves and keep servers on longer. In the stored day/night run, THR and IQR each made two migrations, while OLS made three. These are case observations, not proof of general superiority. OLS here is a simple unweighted baseline, not the paper's weighted local regression.`rA later accounting audit found CPU work on hosts recorded as off. Energy from the affected historical runs is therefore not reliable evidence of savings. Corrected validation is required; this report makes no predictive energy-saving claim and excludes later predictor experiments.`rMetrics follow Beloglazov and Buyya (2012), Section 5.4, with explicit approximations. SLATAH averages each host's saturated time divided by its active time; saturation is 100% in the paper and at least 99.9% in our code. PDM averages estimated migration work lost divided by estimated total VM work. Our numerator uses a 10% VM-capacity penalty during transfers; the denominator uses completed job work, or capacity times execution time when no job has finished.`rSLAV = SLATAH x PDM. A zero SLATAH makes SLAV zero even when migration costs remain. These are research measures, not universal contractual SLA standards.`rNext evaluation must check forecasting accuracy, energy, migrations and CPU shortfall separately on the same workloads.`rFree paper: https://beloglazov.info/papers/2012-optimal-algorithms-ccpe.pdf"
 $link=$d.Tables.Item(6).Cell(2,1).Range.Duplicate
 $link.Find.Text='https://beloglazov.info/papers/2012-optimal-algorithms-ccpe.pdf'
 if($link.Find.Execute()){[void]$d.Hyperlinks.Add($link,'https://beloglazov.info/papers/2012-optimal-algorithms-ccpe.pdf')}
 # Answer text is left-aligned inside the original template cells.
 foreach($index in @(3,5,6)) { $d.Tables.Item($index).Cell(2,1).Range.ParagraphFormat.Alignment=0 }
 $d.Fields.Update() | Out-Null
 $d.Repaginate()
 $d.SaveAs2($out,16)
 $d.ExportAsFixedFormat(($out -replace '\.docx$','.pdf'),17)
 "Saved $out; pages=$($d.ComputeStatistics(2))"
 $d.Close(0)
} finally { $w.Quit() }







