"""Post-run operational scoring, including off-host targets; never used for learning."""
import argparse
import bisect
from collections import Counter, defaultdict
import csv
import gzip
import json
import math
from pathlib import Path


def reference(target, cpu, power, horizon):
    if target > horizon + 1e-9:
        return None, 'outside_window', None
    index = bisect.bisect_right([x[0] for x in power], target)-1
    if index < 0:
        return None, 'unknown_power', None
    if not power[index][1]:
        return 0.0, 'recorded_off', 0.0
    start = power[index][0]
    end = power[index+1][0] if index+1 < len(power) else math.inf
    times = [x[0] for x in cpu]
    candidates = [x for x in cpu[bisect.bisect_left(times,target-1):bisect.bisect_right(times,target+1)]
                  if x[1] and x[0] >= start and x[0] < end]
    if not candidates:
        return None, 'missing_active_observation', None
    sample = min(candidates,key=lambda x:(abs(x[0]-target),x[0]))
    return sample[2], 'active_observed', sample[0]-target


def load_observations(path):
    cpu,power = defaultdict(list),defaultdict(list)
    with gzip.open(path,'rt') as stream:
        for row in csv.DictReader(stream):
            active = row['active']=='true'
            host,time = row['host'],float(row['time'])
            if row['kind']=='power':
                power[host].append((time,active))
            else:
                allocated,capacity = float(row['allocated_mips']),float(row['capacity_mips'])
                assert capacity>0
                assert active or allocated<=1e-9, 'Cannot assign off-host zero with a power invariant violation'
                cpu[host].append((time,active,allocated/capacity))
    for rows in cpu.values():rows.sort(key=lambda x:x[0])
    for host,rows in power.items():
        rows.sort(key=lambda x:x[0])
        compact=[]
        for point in rows:
            if compact and point[0]==compact[-1][0]:compact[-1]=point
            elif not compact or point[1]!=compact[-1][1]:compact.append(point)
        power[host]=compact
    return cpu,power


def predictions(log):
    rows={};case=None
    with gzip.open(log,'rt',encoding='utf-8-sig') as stream:
        for line in stream:
            fields=dict(x.split('=',1) for x in line.split()[1:] if '=' in x)
            if line.startswith('CASE_START'):case=fields['scenario']
            elif line.startswith('FORECAST_INPUT'):
                key=case,fields['host'],fields['target']
                assert key not in rows
                rows[key]=dict(latest=float(fields['samples'].split(';')[0].split(':')[1]))
            elif line.startswith('FORECAST_SCOPE'):
                row=rows[case,fields['host'],fields['target']]
                row.update(issue=float(fields['issue']),due=float(fields['due']),
                           active=fields['active']=='true',vms=int(fields['vms']))
            elif line.startswith('FORECAST_ROBUST '):
                rows[case,fields['host'],fields['target']]['estimate']=float(fields['selected'])
            elif line.startswith('FORECAST_EXCLUDED'):
                rows[case,fields['host'],fields['target']]['runtime_exclusion']=fields['reason']
            elif line.startswith('FORECAST_ERROR'):
                rows[case,fields['host'],fields['target']]['runtime_actual']=float(fields['actual'])
    assert rows
    assert all({'latest','issue','due','active','vms','estimate'} <= row.keys() for row in rows.values())
    return rows


def score(errors):
    if not errors:return dict(n=0,mae_pp=None,rmse_pp=None)
    return dict(n=len(errors),mae_pp=100*sum(abs(x) for x in errors)/len(errors),
                rmse_pp=100*math.sqrt(sum(x*x for x in errors)/len(errors)))


def late_capacity(path, power, horizon):
    """Hindsight diagnostic only, never an achievable-savings or forecast claim."""
    if not path.exists():return None
    with path.open() as stream:jobs=list(csv.DictReader(stream))
    short=[row for row in jobs if int(row['length_mi_per_pe'])==5_000_000]
    if not short or not all(row['completed']=='true' for row in short):
        return dict(scope='Not all short jobs completed; late-long-job bound not evaluated')
    long=[row for row in jobs if int(row['length_mi_per_pe'])!=5_000_000]
    assert long and all(row['completed']=='false' for row in long)
    assert all(int(row['vm'])%4==3 for row in long)
    row=long[0]
    nominal=int(row['host_pes'])*float(row['host_mips'])/(int(row['vm_pes'])*float(row['vm_mips']))
    assert all((x['host_pes'],x['host_mips'],x['vm_pes'],x['vm_mips'])==
               (row['host_pes'],row['host_mips'],row['vm_pes'],row['vm_mips']) for x in jobs)
    cut=max(float(row['finish']) for row in short)
    duration=(horizon-cut)/3600
    actual_hours=0.0
    for events in power.values():
        for i,(start,active) in enumerate(events):
            end=events[i+1][0] if i+1<len(events) else horizon
            if active:actual_hours+=max(0,min(end,horizon)-max(start,cut))/3600
    physical=math.ceil(len(long)/math.floor(nominal))
    placement=math.ceil(len(long)/math.floor(.8*nominal))
    return dict(scope='Late phase after all short jobs finish; fixed one-long-per-initial-host setup. Policy floor applies to THR/robust80% reserved-CPU placement, not all possible policies.',
                last_short_finish_s=cut,unfinished_long_jobs=len(long),late_hours=duration,
                physical_nominal_vms_per_host=math.floor(nominal),reserved80_destination_vms_per_host=math.floor(.8*nominal),
                physical_capacity_hosts=physical,policy_capacity_hosts=placement,
                actual_power_on_host_hours=actual_hours,policy_floor_host_hours=placement*duration,
                difference_between_capacity_floors_fixed_power_kwh=(placement-physical)*duration*.175,
                caution='Difference is structural under the current placement check, not proven recoverable energy or predictive savings. VM start column is allocation time and is not used as a birth timestamp.')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run',type=Path)
    parser.add_argument('output',type=Path)
    args=parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    assert not (args.output/'results.json').exists(),'Do not overwrite evaluation'
    forecasts=predictions(args.run/'simulation.log.gz')
    meta=json.loads((args.run/'run.json').read_text(encoding='utf-8-sig'))
    horizon=float(meta['energy_demand_window_seconds'][1])
    counts=defaultdict(Counter);errors=defaultdict(list);mismatches=Counter()
    observations={case:load_observations(args.run/'host_observations'/(case+'.csv.gz'))
                  for case in {key[0] for key in forecasts}}
    with gzip.open(args.output/'targets.csv.gz','wt',newline='') as stream:
        writer=csv.writer(stream)
        writer.writerow(['case','host','target','eligible_at_issue','reference_kind','actual','forecast','persistence','sample_delta_s','runtime_exclusion'])
        for (case,host,target),row in forecasts.items():
            eligible=row['active'] and row['vms']>0
            actual,kind,delta=reference(row['due'],observations[case][0][host],observations[case][1][host],horizon)
            counts[case]['issued']+=1;counts[case]['eligible' if eligible else 'ineligible_at_issue']+=1
            counts[case][kind]+=1
            if eligible:counts[case]['eligible_'+kind]+=1
            if actual is not None:
                for label in ['all_issued']+(['eligible'] if eligible else []):
                    errors[case,label,'candidate'].append(row['estimate']-actual)
                    errors[case,label,'persistence'].append(row['latest']-actual)
                if eligible and row.get('runtime_exclusion')=='migration':counts[case]['eligible_scored_across_migration']+=1
                if eligible and 'runtime_actual' not in row:counts[case]['eligible_recovered_target']+=1
                if 'runtime_actual' in row and abs(row['runtime_actual']-actual)>1e-9:mismatches[case]+=1
            writer.writerow([case,host,row['due'],eligible,kind,actual,row['estimate'],row['latest'],delta,row.get('runtime_exclusion','')])
    result=dict(scope='Post-run observed operational targets, including VM completion/off periods and migration interventions; no interpolated labels or learning changes',
                eligibility='Host active and resident VM count>0 at issue, never based on future outcome',
                reference='Recorded off=0, otherwise nearest active CPU observation in same power episode within1s; missing remains explicit',
                horizon_seconds=horizon,coverage=dict(counts),runtime_label_disagreements=dict(mismatches),accuracy={},late_capacity={})
    for case in counts:
        result['late_capacity'][case]=late_capacity(args.run/'host_observations'/(case+'-jobs.csv'),observations[case][1],horizon)
        result['accuracy'][case]={label:{model:score(errors[case,label,model]) for model in ('candidate','persistence')}
                                  for label in ('eligible','all_issued')}
        print(case,dict(counts[case]),'eligible',result['accuracy'][case]['eligible'],flush=True)
    (args.output/'results.json').write_text(json.dumps(result,indent=2)+'\n')


if __name__=='__main__':main()
