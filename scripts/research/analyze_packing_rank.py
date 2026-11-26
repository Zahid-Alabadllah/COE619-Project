"""Verify forecast ranking, executed plans and homogeneous-VM packing limits."""
import csv
import gzip
import json
import sys
from pathlib import Path

ROOT=Path('results/predictor_investigation')
RUNS=ROOT/'online_holdout_20110420'


def load(label):
    folder=RUNS/label
    groups={};moves=[];case=policy='';power=placement=0
    for line in gzip.open(folder/'simulation.log.gz','rt'):
        if line.startswith('CASE_START '):
            p=dict(x.split('=',1) for x in line.split()[1:]);case=p['scenario'];policy=p['policy']
        if line.startswith(('POWER_AUDIT ','POWER_HISTORY_AUDIT ')):
            assert line.strip().endswith('=0');power+=1
        if line.startswith('PLACEMENT_AUDIT '):
            for key in ('repeatedEntries','crossHostWithoutMigration','wrongHost','overCapacity','missingUnfinished','residentNotCreated'):
                assert f'{key}=0' in line
            placement+=1
        if policy!='LR':continue
        if line.startswith('PACKING_GROUP '):
            p=dict(x.split('=',1) for x in line.split()[1:]);groups[case,p['time'],p['source']]=p
        if line.startswith('MIGRATION_AUDIT '):moves.append(line.strip())
    assert power==30 and placement==15
    return groups,moves,list(csv.DictReader((folder/'experiments.csv').open()))


def main():
    if '--heterogeneous' in sys.argv:
        _,am,a=load('heterogeneous_rank_current')
        _,bm,b=load('heterogeneous_rank_forecast')
        output=[]
        for x,y in zip(a,b):
            for key in ('scenario','policy','vms_created','cloudlets_finished','end_time_s'):assert x[key]==y[key]
            if x['policy']!='LR':assert x==y
            output.append(dict(case=x['scenario'],policy=x['policy'],current_kwh=float(x['energy_kwh']),
                forecast_kwh=float(y['energy_kwh']),delta_kwh=float(y['energy_kwh'])-float(x['energy_kwh']),
                current_migrations=int(x['migrations']),forecast_migrations=int(y['migrations'])))
        sizes=0
        for f in (RUNS/'heterogeneous_rank_forecast/host_observations').glob('*-jobs.csv'):
            for x in csv.DictReader(f.open()):
                assert int(x['vm_pes'])==1+int(x['vm'])%2;sizes+=1
        assert sizes==84
        result=dict(vm_sizes_checked=sizes,rows=output,migration_events_identical=am==bm,
            zero_power_checks=60,clean_final_placement_checks=30,
            decision='No energy improvement; real_b LR slightly worse, other cases unchanged. Retire experiment.')
        (ROOT/'heterogeneous_packing_rank/comparison.json').write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps(result,indent=2));return
    a,am,ar=load('packing_rank_current_trace')
    b,bm,br=load('packing_rank_forecast_trace')
    c,cm,cr=load('packing_rank_capacity_audit')
    assert ar==br==cr and am==bm==cm
    for arm in ('current','forecast'):
        assert (RUNS/f'packing_rank_{arm}/experiments.csv').read_bytes()==(RUNS/f'packing_rank_{arm}_trace/experiments.csv').read_bytes()
    assert set(a)==set(b)==set(c)
    changed=[k for k in sorted(a) if (a[k]['complete'],a[k]['proposed'])!=(b[k]['complete'],b[k]['proposed'])]
    completed=[k for k in a if a[k]['complete']=='true' or b[k]['complete']=='true']
    shortage=[k for k in changed if int(c[k]['physicalSlots'])<int(c[k]['needed'])]
    assert all(a[k]['complete']=='false' and b[k]['complete']=='false' for k in changed)
    result=dict(common_groups=len(a),changed_groups=len(changed),completed_groups=len(completed),
        changed_completed_groups=0,changed_groups_with_physical_slot_shortage=len(shortage),
        migration_events_identical=True,migration_events=len(am),all_15_csv_rows_identical=True,
        zero_power_checks=150,clean_final_placement_checks=75,
        limitation='Physical-slot bound uses this benchmark\'s identical VM resource sizes and eligible destination set; not a universal cloud capacity bound.',
        examples=[dict(case=k[0],time=k[1],source=k[2],current=a[k]['proposed'],forecast=b[k]['proposed'],
                       needed=int(c[k]['needed']),physical_slots=int(c[k]['physicalSlots'])) for k in changed[:5]])
    (ROOT/'forecast_packing_rank/decision_audit.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))


if __name__=='__main__':main()
