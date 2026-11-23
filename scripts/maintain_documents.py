"""Synchronize course copies and clean known generated artifacts without following links."""
from pathlib import Path
import argparse, os, shutil, ctypes
CODE = Path(__file__).resolve().parents[1]
ROOT = CODE.parent
COURSE = ROOT / "Course_Documents"

def link(p):
    return bool(getattr(os.lstat(p), "st_file_attributes", 0) & 0x400)

def size(p):
    if link(p): return 0
    if p.is_file(): return p.stat().st_size
    return sum(size(Path(e.path)) for e in os.scandir(p))

def discard(p):
    # Windows marks some OneDrive build directories read-only. Change this path's
    # attribute only; SetFileAttributesW operates on a reparse link itself.
    attrs = getattr(os.lstat(p), "st_file_attributes", 0)
    if os.name == "nt" and attrs & 1:
        if not ctypes.windll.kernel32.SetFileAttributesW(str(p), attrs & ~1):
            raise ctypes.WinError()
    if link(p):
        os.rmdir(p) if p.is_dir() else p.unlink()
    elif p.is_dir():
        for e in os.scandir(p): discard(Path(e.path))
        p.rmdir()
    else: p.unlink()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--clean",action="store_true")
    parser.add_argument("--dry-run",action="store_true")
    args=parser.parse_args()
    mappings=[("progress_report_1","04_Progress_Report_I","PR1_Progress_Report_I"),("progress_report_2","05_Progress_Report_II","PR2_Progress_Report_II"),("concepts_guide","06_Concepts_And_Methods_Guide","COE619_Project_Concepts_And_Methods_Guide")]
    for src,folder,stem in mappings:
        for ext in ("pdf","tex"):
            a=CODE/"docs"/src/(stem+"."+ext);b=COURSE/folder/a.name
            if a.is_file() and (not b.exists() or a.read_bytes()!=b.read_bytes()):
                print("Sync:",b.relative_to(ROOT))
                if not args.dry_run:
                    b.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(a,b)
    # Current filled form is separate from the frozen historical PDF/TeX.
    for ext in ('docx', 'pdf'):
        a=CODE/'docs/progress_report_2/coordinator_template'/('PR2_202045160.'+ext)
        b=COURSE/'05_Progress_Report_II'/a.name
        if a.is_file() and (not b.exists() or a.read_bytes()!=b.read_bytes()):
            print('Sync:',b.relative_to(ROOT))
            if not args.dry_run:
                b.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(a,b)
    if not args.clean: return
    target=CODE/"target"
    candidates=[target/n for n in ("pr2-template","meeting-deck","framework-patch","source-check","meeting-build","classes","maven-status","generated-sources","test-classes","default-smoke","corrected-default-repeat","debug-smoke","mean-metric-regression")]
    # Essential research inputs/outputs are retained under results before cleanup.
    # The downloaded archive is reproducible from the pinned commit and digest.
    candidates += [target/n for n in ("planetlab-source.zip","default-smoke.log","capacity-admission.txt","framework-compile.log","framework-repair.log","forecast-decision-audit.txt","forecast-scope-capacity.txt")]
    candidates += [target/n for n in ("bitbrains-fastStorage.zip","corrected-default-repeat.log","debug-smoke.log","debug-prepare.log","mean-metric-regression.log","full-day-hotspot.txt","full-day-history-size.txt","demand-power-hotspot.txt","demand-power-history.txt","power-compile.log","demand-compile.log","demand-repair.log")]
    if target.exists():
        candidates += [p for p in target.iterdir() if p.is_file() and (p.suffix==".png" or p.name.endswith("-build.txt") or p.name=="demo-verification.log")]
    candidates+=list(ROOT.glob(".chart-data-*"))
    candidates += [CODE/'scripts/research/__pycache__']
    candidates += [target/'forecast-20110420-mixed_scalein_forecast.log']
    candidates += [p for p in (CODE/"docs").rglob("*") if p.is_file() and p.suffix in (".aux",".out",".log",".toc",".fls",".fdb_latexmk")]
    candidates += [CODE/n for n in ("target-demo.log","target-stats.log","fixed-workloads.log","fixed-repeat.log","fixed-stats.log","source-cleanup-verification.log","source-compile-check.log","five-files-check.log")]
    total=0
    for p in candidates:
        if not os.path.lexists(p): continue
        assert os.path.commonpath([os.path.abspath(p),str(ROOT)])==str(ROOT)
        n=size(p)
        if args.dry_run:
            print("Would clean:",p.relative_to(ROOT),n,"bytes (links not followed)");total+=n;continue
        try: discard(p);total+=n
        except OSError as exc: print("In use / retained:",p.relative_to(ROOT),type(exc).__name__)
    print(f"{'Eligible' if args.dry_run else 'Cleaned'}: {total/1024/1024:.2f} MiB. Shared runtime targets untouched.")

if __name__=="__main__": main()
