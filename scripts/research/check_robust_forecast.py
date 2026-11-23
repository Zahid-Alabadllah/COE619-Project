"""Reconstruct every opt-in Java robust forecast using an independent Python replay."""
from collections import deque, defaultdict
import argparse
import gzip
import json
import math
import re
import sys
sys.dont_write_bytecode=True
from evaluate_robust_pairs import robust_pair


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log')
    parser.add_argument('output')
    args=parser.parse_args()
    states={};inputs={};errors=defaultdict(list);checks=0;maximum=0;resets=0
    opener=gzip.open if args.log.endswith('.gz') else open
    with opener(args.log,'rt',encoding='utf-8-sig') as stream:
        for line in stream:
            if line.startswith('CASE_START'):
                case=re.search(r'scenario=(\S+)',line)[1];states={};inputs={}
            elif line.startswith('FORECAST_ROBUST_RESET'):
                host=re.search(r'host=(\d+)',line)[1];states.pop(host,None);resets+=1
            elif line.startswith('FORECAST_INPUT'):
                host,target,values=re.search(r'host=(\d+) target=([\d.]+) samples=(.*)',line).groups()
                samples=[tuple(map(float,x.split(':'))) for x in values.strip().split(';')]
                assert all(t<=float(target)-300+.001 for t,u in samples)
                inputs[host,target]=dict(latest=samples[0][1])
            elif line.startswith('FORECAST_ROBUST '):
                host,target,raw,selected=re.search(r'host=(\d+) target=([\d.]+) raw=([-+\deE.]+) selected=([-+\deE.]+)',line).groups()
                history,ready=states.setdefault(host,(deque(maxlen=20),[]))
                issue=float(target)-300
                history.extend(x for x in ready if x[0]<issue-1e-6)
                ready[:]=[x for x in ready if x[0]>=issue-1e-6]
                latest=inputs[host,target]['latest']
                expected_raw=robust_pair([(x[1],x[2]) for x in history],latest)
                # Match the Java learner's IEEE-754 accumulation order. Python's
                # compensated float sum can reverse a mathematically tied decision.
                robust_loss=persistence_loss=0.0
                for x in history:
                    robust_loss+=abs(x[3]-x[2])
                    persistence_loss+=abs(x[1]-x[2])
                choose=len(history)>=10 and robust_loss<persistence_loss
                expected=expected_raw if choose else latest
                difference=max(abs(float(raw)-expected_raw),abs(float(selected)-expected))
                maximum=max(maximum,difference)
                assert difference<1e-6,(case,host,target,raw,selected,expected_raw,expected,robust_loss,persistence_loss,robust_loss-persistence_loss)
                inputs[host,target].update(raw=float(raw),selected=float(selected))
                checks+=1
            elif line.startswith('FORECAST_ERROR'):
                host,target,actual,forecast,latest=re.search(r'host=(\d+) target=([\d.]+) actual=([-+\deE.]+) forecast=([-+\deE.]+) persistence=([-+\deE.]+)',line).groups()
                row=inputs[host,target];actual=float(actual)
                assert abs(float(forecast)-row['selected'])<1e-9
                states[host][1].append((float(target),row['latest'],actual,row['raw']))
                errors[case].append((row['selected']-actual,row['latest']-actual))
    assert checks and errors
    result=dict(forecast_checks=checks,max_difference=maximum,migration_resets=resets,accuracy={})
    for case,rows in errors.items():
        result['accuracy'][case]={name:dict(n=len(rows),mae_pp=100*sum(abs(r[i]) for r in rows)/len(rows),
            rmse_pp=100*math.sqrt(sum(r[i]**2 for r in rows)/len(rows))) for i,name in enumerate(('candidate','persistence'))}
    with open(args.output,'w') as out:json.dump(result,out,indent=2);out.write('\n')
    print('PASS:',checks,'robust forecasts; maximum difference',maximum,'resets',resets)
    for case,row in result['accuracy'].items(): print(case,row)


if __name__=='__main__':main()

