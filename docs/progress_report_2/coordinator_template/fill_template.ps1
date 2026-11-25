$ErrorActionPreference='Stop'
$root='C:\Users\zahed\OneDrive - KFUPM\MX HPCC\T261\COE619'
$stage=Join-Path $root 'COE619-Project-Code\target\pr2-template'
$out=Join-Path $root 'Course_Documents\05_Progress_Report_II\PR2_202045160.docx'
New-Item -ItemType Directory -Path $stage -Force | Out-Null
$workingTemplate=Join-Path $stage 'working_template.docx'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Coordinator_Progress_Report_Template.docx') -Destination $workingTemplate -Force
$w=New-Object -ComObject Word.Application
$w.Visible=$false
$w.DisplayAlerts=0
function Fill($cell,[string]$text){$r=$cell.Range.Duplicate; $r.End=$r.End-1; $r.Text=$text}
try {
 $d=$w.Documents.Open($workingTemplate,$false,$false)
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
 $tasks=@('Proposal and advisor confirmation','Literature and simulation setup','THR/IQR baselines and PR1','Model design and PR2','Predictor refinement and PR3','Experiments and demonstration','Final report and presentation')
 $statuses=@('Plan: T261 W3|Completed by PR1 (W10)','Plan: T261 W6|Completed by PR1 (W10)','Plan: T261 W9 / W10|Completed by PR1 (W10)','Plan: T261 W13 / W15|Initial version prepared','T262 W4 / W7|Planned','T262 W10 / W12-13|Planned','T262 W14 / W15|Planned')
 $rows=@(3,6,9,12,15,18,21)
 $plans=@(@(3),@(6),@(9,10),@(13,15),@(),@(),@())
 $verifiedBy=@(10,10,10,15,0,0,0)
 for($i=0;$i -lt 7;$i++){
   $taskCell=$chart.Cell($rows[$i],25)
   Fill $taskCell ($tasks[$i]+"`r"+$statuses[$i].Replace('|',"`r"))
   $taskCell.Range.ParagraphFormat.Alignment=0
   $taskCell.Range.Font.Size=9
   $taskCell.Range.Font.Bold=0
   $taskCell.Range.Paragraphs.Item(1).Range.Font.Bold=1
   foreach($c in $chart.Range.Cells){
     if($c.ColumnIndex -le 24){
       $week=25-$c.ColumnIndex
       if($c.RowIndex -eq $rows[$i] -and $plans[$i] -contains $week){$c.Shading.BackgroundPatternColor=12632256}
       if($verifiedBy[$i] -gt 0 -and $c.RowIndex -eq ($rows[$i]+1) -and $week -eq $verifiedBy[$i]){$c.Shading.BackgroundPatternColor=5287936}
     }
   }
 }
 # Clarify the retained grid within the existing single-line caption.
 $caption=$d.Content.Duplicate
 $caption.Find.Text='Accomplishment Chart:'
 if($caption.Find.Execute()) {
   $note=$caption.Paragraphs.Item(1).Range.Duplicate
   $note.End=$note.End-1
   $note.Text='Accomplishment Chart: T261 W1-15 only; T262 targets in labels. Green = completed-by checkpoint.'
   $note.Font.Bold=0
   $note.Font.Italic=1
   $lead=$note.Duplicate
   $lead.End=$lead.Start+21
   $lead.Font.Bold=1
   $lead.Font.Italic=0
 }
 Fill $d.Tables.Item(5).Cell(2,1) "The proposal scheduled model design for T261 W13 and predictive implementation for T262 W4. An initial OLS version was implemented early; refinement, validation and proof of benefit remain future objectives within this report's scope. The common runner and concepts guide support the PR2 deliverable.`rThe chart retains the template grid. Grey cells show proposal checkpoints; green cells mean completed by a documented checkpoint. PR1 confirms the first three tasks by W10; the initial model and PR2 are prepared by W15. These are not exact completion dates or claims of a finished predictor. Only T261 W1-15 uses the grid; columns 16-24 are unused. T262 targets are listed separately in task labels: implementation/refinement W4, PR3 W7, experiments W10, demonstration W12-13, final report W14 and presentation W15. Accomplishments retain the 15 November cutoff; later predictor experiments are excluded."
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







