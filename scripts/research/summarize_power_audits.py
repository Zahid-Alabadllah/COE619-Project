"""Verify completed corrected-power batches and retain the legacy counterexample."""
from pathlib import Path
import csv
import gzip
import hashlib
import json
import re

CODE=Path(__file__).resolve().parents[2]
ROOT=CODE/'results/predictor_investigation'
CORRECTED=('corrected_default_power_audit','corrected_mean_power_audit',
           'online_holdout_20110420/validated_power_mean','online_holdout_20110420/validated_power_control',
           'online_holdout_20110420/bounded_power_mean')


def inspect(folder):
    path=ROOT/folder
    text=gzip.open(path/'simulation.log.gz','rt',encoding='utf-8-sig').read()
    checks=[]
    for line in text.splitlines():
        if line.startswith(('POWER_AUDIT ','POWER_HISTORY_AUDIT ')):
            match=re.fullmatch(r'(POWER_AUDIT|POWER_HISTORY_AUDIT) scenario=(\S+) policy=(\S+) (?:inactiveWorkSamples|inactiveCpuEntries)=(\d+)',line)
            assert match,line
            kind,case,policy,value=match.groups()
            checks.append(dict(kind=kind,case=case,policy=policy,violations=int(value)))
    rows=list(csv.DictReader((path/'experiments.csv').open()))
    assert len(rows)==15 and len(checks)==30,(folder,len(rows),len(checks))
    assert all(row['vms']==row['vms_created'] for row in rows)
    return dict(rows=len(rows),checks=checks,violations=sum(r['violations'] for r in checks))


def main():
    report=dict(legacy=inspect('legacy_default_power_audit'),corrected={})
    assert report['legacy']['violations']>0,'Expected retained legacy counterexample'
    for folder in CORRECTED:
        result=inspect(folder)
        assert result['violations']==0,(folder,result)
        report['corrected'][folder]=result
    initial=json.loads((ROOT/'online_holdout_20110309/half/run.json').read_text(encoding='utf-8-sig'))
    for key,path in [('frozen_csv_sha256',CODE/'results/experiments.csv'),
                     ('frozen_pr2_sha256',CODE/'docs/progress_report_2/PR2_Progress_Report_II.pdf')]:
        digest=hashlib.sha256(path.read_bytes()).hexdigest().upper()
        assert digest==initial[key],key
        report[key]=digest
    report['production_java_files']=len(list((CODE/'src/main/java').rglob('*.java')))
    assert report['production_java_files']==5
    report['interpretation']='Zero violations in recorded samples/history for four corrected batches; not proof over every unrecorded instant or all future configurations. Legacy energies need erratum.'
    (ROOT/'power_audit_summary.json').write_text(json.dumps(report,indent=2)+'\n')
    runs=sum(x['rows'] for x in report['corrected'].values())
    checks=sum(len(x['checks']) for x in report['corrected'].values())
    print(f'PASS: {runs} corrected policy runs / {checks} power checks have zero recorded violations; legacy counterexample retained; frozen PR2/CSV unchanged; five production Java files')


if __name__=='__main__':
    main()
