"""Replay causal forecast inputs; select settings on early real_a only."""
from pathlib import Path
import csv, re, math, json, argparse, gzip
BASE=Path(__file__).resolve().parents[1]
ROOT=BASE/"results/predictor_investigation"
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--log",default="accuracy-replay.log")
parser.add_argument("--output",default="forecast_accuracy.json")
parser.add_argument("--locked",action="store_true",help="Use the previously selected 10-point, half-trend model without tuning")
args=parser.parse_args()
inputs={}; observations=[];case=None
log=ROOT/args.log
if not log.exists(): log=Path(str(log)+".gz")
source=gzip.open(log,"rt").read() if log.suffix==".gz" else log.read_text()
for line in source.splitlines():
    if line.startswith("CASE_START"):
        case=re.search(r"scenario=(\S+)",line)[1]
    elif line.startswith("FORECAST_INPUT"):
        m=re.search(r"host=(\d+) target=([\d.]+) samples=(.*)",line)
        inputs[(case,m[1],m[2])]=[tuple(map(float,p.split(":"))) for p in m[3].split(";")]
    elif line.startswith("FORECAST_ERROR"):
        m=re.search(r"host=(\d+) target=([\d.]+) actual=([-+\d.]+) (?:ols|forecast)=([-+\d.]+) persistence=([-+\d.]+)",line)
        key=(case,m[1],m[2])
        if key in inputs:observations.append((case,float(m[2]),float(m[3]),inputs[key]))
assert observations

def predict(row,k,gain):
    samples=row[3][:k];latest=samples[0][1]
    if gain==0:return latest
    mt=sum(t for t,u in samples)/len(samples);mu=sum(u for t,u in samples)/len(samples)
    variance=sum((t-mt)**2 for t,u in samples)
    slope=sum((t-mt)*(u-mu) for t,u in samples)/variance if variance else 0
    ols=mu+slope*(row[1]-mt)
    return latest+gain*(ols-latest)

def score(rows,k,g):
    e=[predict(x,k,g)-x[2] for x in rows]
    return {"count":len(e),"mae_pp":100*sum(abs(x) for x in e)/len(e),"rmse_pp":100*math.sqrt(sum(x*x for x in e)/len(e))}

training=[x for x in observations if x[0]=="real_a"]
cutoff=sorted(x[1] for x in training)[int(len(training)*.7)]
training=[x for x in training if x[1]<cutoff]
# Lock the choice before examining other cases or the later time portion.
candidates=[(k,g) for k in [3,5,10] for g in [0,.25,.5,.75,1]]
best=(10,0.5) if args.locked else min(candidates,key=lambda kg:score(training,*kg)["mae_pp"])
report={"selection":"locked from earlier tuning" if args.locked else "minimum MAE on early 70% of real_a targets only", "cutoff_s":cutoff,"chosen_window":best[0],"chosen_trend_weight":best[1],"training":score(training,*best),"validation":{}}
for c in ["diurnal","real_a","real_b","real_c","heavy"]:
    rows=[x for x in observations if x[0]==c and (c!="real_a" or x[1]>=cutoff)]
    if rows:report["validation"][c]={"persistence":score(rows,10,0),"original_ols":score(rows,10,1),"selected":score(rows,*best)}
(ROOT/args.output).write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
