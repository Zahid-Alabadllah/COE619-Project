"""Verify issue-time eligibility, reference boundaries and unchanged simulation outcomes."""
import argparse
import csv
import gzip
import json
from pathlib import Path
from evaluate_forecast_scope import predictions, reference


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('legacy',type=Path)
    parser.add_argument('live',type=Path)
    parser.add_argument('output',type=Path)
    args=parser.parse_args()
    with (args.legacy/'experiments.csv').open() as f:a=list(csv.DictReader(f))
    with (args.live/'experiments.csv').open() as f:b=list(csv.DictReader(f))
    assert len(a)==len(b)
    assert all(x[k]==y[k] for x,y in zip(a,b) for k in x if k!='lr_predictions')
    old=predictions(args.legacy/'simulation.log.gz');new=predictions(args.live/'simulation.log.gz')
    assert set(new)<=set(old)
    assert all(r['active'] and r['vms']>0 for r in new.values())
    assert all(old[k]['estimate']==r['estimate'] and old[k]['latest']==r['latest'] for k,r in new.items())
    removed=[r for k,r in old.items() if k not in new]
    assert all(not r['active'] or r['vms']==0 for r in removed)
    power_checks=0
    for path in [args.legacy,args.live]:
        lines=[x for x in gzip.open(path/'simulation.log.gz','rt') if x.startswith(('POWER_AUDIT','POWER_HISTORY_AUDIT'))]
        assert len(lines)==2*len(a) and all(x.strip().endswith('=0') for x in lines)
        power_checks+=len(lines)
    cpu=[(15,True,.5),(20,False,0.)];power=[(0,False),(10,True),(20,False)]
    assert reference(5,cpu,power,100)[:2]==(0.,'recorded_off')
    assert reference(15,cpu,power,100)[:2]==(.5,'active_observed')
    assert reference(19.9,cpu,power,100)[1]=='missing_active_observation'
    assert reference(21,cpu,power,100)[:2]==(0.,'recorded_off')
    assert reference(101,cpu,power,100)[1]=='outside_window'
    report=dict(legacy_forecasts=len(old),live_forecasts=len(new),removed_ineligible_forecasts=len(removed),
                common_predictions_identical=True,all_other_csv_fields_identical=True,
                recorded_zero_power_checks=power_checks,reference_boundary_tests=5)
    args.output.write_text(json.dumps(report,indent=2)+'\n')
    print('PASS',report)


if __name__=='__main__':main()
