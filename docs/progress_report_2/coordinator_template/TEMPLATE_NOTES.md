# PR2 template and chart contract

Use the stable PR2_202045160 Word/PDF files for submission. The original coordinator template remains unchanged. The current approved form preserves its three sections, portrait/landscape/portrait page geometry, branding, cover fields and seven chart task blocks.

The user requested weekly completed bars consistent with PR1. Carry forward PR1's first three completed phase windows: W1-3 scope/proposal, W3-6 initial review/setup, and W6-10 preliminary THR/IQR/PR1. Show W11-15 as complete for the initial PR2 scope only. It includes model design, initial OLS, runner, real inputs, guide and report preparation. This does not mean that the final predictor or its energy benefit is complete.

The 24 weekly header columns and task-column vertical merges are preserved. Irregular sample timeline merges were normalized to weekly cells; completed cells were merged into four phase bars with 100% labels, as in PR1. Phase bars indicate the reported phase coverage, not measured daily work or exact completion dates. Grey is planned, green is completed initial scope. The first three ranges/statuses are copied from PR1, whose SHA256 is pinned in week_chart_source.xml.

T262 remains unplotted because this template has only 24 columns. Future phase windows match PR1: W1-7 predictor/refinement/PR3, W8-13 experiments/demonstration, W14-15 report/presentation. Detailed proposal checkpoints remain W4, W7, W10, W12-13, W14 and W15. Columns 16-24 are unused. Do not invent next-term dates.

Build: fill_template.ps1 edits a disposable target copy of the approved filled form, applies apply_week_chart.py with the editable week_chart_source.xml, refreshes fields, checks three pages and exports through installed Word. The chart-only Word patch preserves non-chart package parts, and the PDF build retains the approved cover page unchanged. Cover edits or signatures require a separately reviewed new cover export. Keep the original template hash unchanged. Bundled Poppler is used for all-page image inspection because packaged render_docx.py lacks LibreOffice.

The scientific work cutoff remains 15 November 2026. Historical energy results remain qualified; later predictor experiments are excluded. Advisor signature remains blank. See submission_review.json for current hashes and checks. Earlier versions are retained in numbered Course_Documents archives; temporary renders belong under target and are cleaned after inspection.

The earlier isolated checkpoint chart is archived in Course_Documents/05_Progress_Report_II/Archive/2026-11-25_Checkpoint_Chart. The approved cover and scientific text are preserved; current output files use the same stable names.
