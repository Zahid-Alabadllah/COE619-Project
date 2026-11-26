"""Independent replay and past-only audit for the archived VM-group experiment."""
import csv
import argparse
import gzip
import json
from collections import defaultdict, deque
from pathlib import Path
import sys
sys.dont_write_bytecode=True
from evaluate_robust_pairs import robust_pair

ROOT=Path('results/predictor_investigation')
RUNS=ROOT/'online_holdout_20110420'


class Model:
    def __init__(self):
        self.ready=[];self.history=deque(maxlen=20);self.processed=0;self.previous=None
    def predict(self,time,latest):
        arrived=[p for p in self.ready if p[0]<time-1e-6]
        self.ready=[p for p in self.ready if p[0]>=time-1e-6]
        self.history.extend(arrived)
        raw=robust_pair([(p[1],p[2]) for p in self.history],latest)
        robust_loss=baseline_loss=0.
        for p in self.history:
            robust_loss+=abs(p[3]-p[2]);baseline_loss+=abs(p[1]-p[2])
        return raw, raw if len(self.history)>=10 and robust_loss<baseline_loss else latest
    def issue(self,time,latest,points):
        while self.processed<len(points) and points[self.processed][0]<time-1e-6:
            t,x=points[self.processed];self.processed+=1
            if self.previous is not None:
                old_t,old_x,old_raw=self.previous
                if abs(t-old_t-300)<=1:self.ready.append((t,old_x,x,old_raw))
            raw,_=self.predict(t,x);self.previous=t,x,raw
        return self.predict(time,latest)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run',default='vm_group_predictive')
    parser.add_argument('--control',default='vm_group_observation_control')
    parser.add_argument('--output',type=Path,default=ROOT/'vm_group_admission/verification.json')
    parser.add_argument('--allow-lr-change',action='store_true')
    args=parser.parse_args()
    counts=defaultdict(int);maximum=0.;changed=0
    path=RUNS/args.run/'simulation.log.gz'
    for line in gzip.open(path,'rt'):
        if line.startswith('CASE_START '):
            samples=defaultdict(list);models=defaultdict(Model)
        elif line.startswith('VM_DEMAND_SAMPLE '):
            p=dict(x.split('=',1) for x in line.split()[1:])
            samples[p['vm']].append((float(p['time']),float(p['fraction'])))
        elif line.startswith('VM_GROUP_FORECAST '):
            p=dict(x.split('=',1) for x in line.split()[1:]);t=float(p['time']);latest=float(p['latest'])
            raw,selected=models[p['vm']].issue(t,latest,samples[p['vm']])
            delta=max(abs(raw-float(p['raw'])),abs(selected-float(p['forecast'])))
            maximum=max(maximum,delta)
            assert delta<1e-12,p
            assert int(p['processed'])==models[p['vm']].processed
            assert p['lastPast']=='NaN' or float(p['lastPast'])<t-1e-6
            changed+=selected!=latest;counts['vm_forecasts']+=1
        elif line.startswith('VM_GROUP_ADMISSION '):
            counts['group_checks']+=1;counts['vetoes']+='veto=true' in line
        elif line.startswith(('POWER_AUDIT ','POWER_HISTORY_AUDIT ')):
            assert line.strip().endswith('=0');counts['zero_power_checks']+=1
        elif line.startswith('PLACEMENT_AUDIT '):
            for key in ('repeatedEntries','crossHostWithoutMigration','wrongHost','overCapacity','missingUnfinished','residentNotCreated'):
                assert f'{key}=0' in line
            counts['clean_final_placement_checks']+=1
    assert counts['vm_forecasts'] and counts['zero_power_checks']==30 and counts['clean_final_placement_checks']==15
    a=(RUNS/args.control/'experiments.csv').read_bytes()
    b=(RUNS/args.run/'experiments.csv').read_bytes()
    if not args.allow_lr_change:assert a==b
    else:
        left=list(csv.DictReader(a.decode().splitlines()));right=list(csv.DictReader(b.decode().splitlines()))
        assert len(left)==len(right)==15
        for x,y in zip(left,right):
            for key in ('scenario','policy','vms_created','cloudlets_finished','end_time_s'):assert x[key]==y[key]
            if x['policy']!='LR':assert x==y
    result=dict(counts=counts,max_replay_difference=maximum,forecasts_differing_from_persistence=changed,
        past_only_training_verified=True,control_csv_identical=a==b,predictive_energy_advantage=False)
    args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))


if __name__=='__main__':main()
