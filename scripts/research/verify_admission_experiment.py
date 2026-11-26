"""Retain the negative forecast-admission result and shared-placement tradeoffs."""
import csv
import gzip
import json
from pathlib import Path

ROOT = Path('results/predictor_investigation')
RUNS = ROOT / 'online_holdout_20110420'


def rows(name):
    return list(csv.DictReader((RUNS / name / 'experiments.csv').open()))


def check_audits(name, count):
    log = gzip.open(RUNS / name / 'simulation.log.gz', 'rt').read()
    power = [line for line in log.splitlines() if line.startswith(('POWER_AUDIT ', 'POWER_HISTORY_AUDIT '))]
    placement = [line for line in log.splitlines() if line.startswith('PLACEMENT_AUDIT ')]
    assert len(power) == 2 * count and all(line.endswith('=0') for line in power)
    assert len(placement) == count and all('missingUnfinished=0' in line and 'crossHostWithoutMigration=0' in line for line in placement)
    assert 'unchanged=false' not in log
    return log


def main():
    base = rows('readonly_validated_matrix')
    control = rows('readonly_demand_matrix')
    candidate = rows('readonly_predictive_admission_matrix')
    assert len(base) == len(control) == len(candidate) == 15
    assert control == candidate, 'Forecast admission unexpectedly changed the control'
    for name in ('readonly_demand_matrix', 'readonly_predictive_admission_matrix'):
        check_audits(name, 15)
    diagnostic = rows('readonly_predictive_admission_diagnostic')
    assert diagnostic == [r for r in candidate if r['scenario'] == 'heavy']
    log = check_audits('readonly_predictive_admission_diagnostic', 3)
    checks = [line for line in log.splitlines() if line.startswith('ADMISSION_FORECAST_CHECK ')]
    available = sum('available=true' in line for line in checks)
    vetoes = sum(line.startswith('ADMISSION_FORECAST_VETO ') for line in log.splitlines())
    assert checks and available and vetoes == 0
    comparison = []
    for before, after in zip(base, control):
        assert (before['scenario'], before['policy']) == (after['scenario'], after['policy'])
        for key in ('end_time_s', 'vms_created', 'cloudlets_finished'):
            assert before[key] == after[key]
        comparison.append(dict(scenario=after['scenario'], policy=after['policy'],
            baseline_kwh=float(before['energy_kwh']), demand_kwh=float(after['energy_kwh']),
            delta_kwh=float(after['energy_kwh'])-float(before['energy_kwh']),
            baseline_migrations=int(before['migrations']), demand_migrations=int(after['migrations']),
            baseline_sampled_shortfall=float(before['unmet_demand']), demand_sampled_shortfall=float(after['unmet_demand'])))
    output = dict(rows=comparison, forecast_control_csv_identical=True,
        heavy_forecast_admission_checks=len(checks), heavy_available_forecasts=available, heavy_forecast_vetoes=vetoes,
        predictive_energy_advantage=False, decision='Retire both experimental runtime options; preserve shared-admission findings and the null forecasting result.')
    source_control = rows('readonly_demand_sources_control')
    source_predictive = rows('readonly_demand_sources_predictive')
    assert len(source_control) == 15 and source_control == source_predictive
    check_audits('readonly_demand_sources_control', 15)
    source_log = check_audits('readonly_demand_sources_predictive', 15)
    source_checks = [line for line in source_log.splitlines() if line.startswith('ADMISSION_FORECAST_CHECK ')]
    assert source_checks and all('available=true' in line for line in source_checks)
    assert 'ADMISSION_FORECAST_VETO ' not in source_log
    source_comparison = []
    for before, after in zip(base, source_control):
        for key in ('scenario', 'policy', 'end_time_s', 'vms_created', 'cloudlets_finished'):
            assert before[key] == after[key]
        source_comparison.append(dict(scenario=after['scenario'], policy=after['policy'],
            baseline_kwh=float(before['energy_kwh']), demand_source_kwh=float(after['energy_kwh']),
            baseline_migrations=int(before['migrations']), demand_source_migrations=int(after['migrations'])))
    output['demand_source_extension'] = dict(rows=source_comparison, forecast_control_csv_identical=True,
        available_forecast_checks=len(source_checks), vetoes=0, decision='Retire: mixed shared-policy energy changes and no added forecast effect.')
    (ROOT / 'read_only_underload/admission_result.json').write_text(json.dumps(output, indent=2) + '\n')
    print(json.dumps({k:v for k,v in output.items() if k != 'rows'}, indent=2))


if __name__ == '__main__':
    main()
