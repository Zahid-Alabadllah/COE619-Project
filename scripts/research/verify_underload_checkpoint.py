"""Verify preserved evidence for the opt-in numeric underload planner."""
import csv
import gzip
import hashlib
import json
import re
from pathlib import Path


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    root = Path('results/predictor_investigation/read_only_underload')
    runs = Path('results/predictor_investigation/online_holdout_20110420')
    plan = json.loads((root / 'plan.json').read_text(encoding='utf-8-sig'))
    for name, expected in plan['frozen_files'].items():
        assert digest(Path(name)) == expected, name
    pairs = [
        (runs / 'readonly_validated_matrix/experiments.csv', runs / 'readonly_underload_matrix/experiments.csv'),
        (runs / 'readonly_no_migration_matrix/experiments.csv', runs / 'bounded_scalein_no_migration/experiments.csv'),
        (root / 'default_isolation/experiments.csv', Path('results/predictor_investigation/corrected_default_power_audit/experiments.csv')),
    ]
    for actual, reference in pairs:
        assert actual.read_bytes() == reference.read_bytes(), (actual, reference)
    text = gzip.open(runs / 'readonly_validated_matrix/simulation.log.gz', 'rt').read()
    planning = [line for line in text.splitlines() if 'PLAN_STATE_AUDIT ' in line]
    assert planning and all('unchanged=true' in line for line in planning)
    power = [line for line in text.splitlines() if line.startswith(('POWER_AUDIT ', 'POWER_HISTORY_AUDIT '))]
    placement = [line for line in text.splitlines() if line.startswith('PLACEMENT_AUDIT ')]
    assert len(power) == 30 and all(line.endswith('=0') for line in power)
    assert len(placement) == 15
    for line in placement:
        values = re.findall(r'(?:repeatedEntries|crossHostWithoutMigration|wrongHost|overCapacity|missingUnfinished|residentNotCreated)=(\d+)', line)
        assert len(values) == 6 and all(int(value) == 0 for value in values), line
    replay = json.loads((root / 'forecast_replay.json').read_text())
    assert replay['forecast_checks'] == 13298 and replay['max_difference'] == 0
    unit = (root / 'unit_checks.txt').read_text()
    assert 'PASS: public optimizer' in unit and 'PASS: already-invalid duplicate' in unit
    metric = json.loads((root / 'matrix_pdm_audit.json').read_text())
    assert len(metric['rows']) == 15
    assert not any(row['undefined_nonzero_cost_vms'] for row in metric['rows'])
    data = list(csv.DictReader((runs / 'readonly_validated_matrix/experiments.csv').open()))
    heavy = {row['policy']: row for row in data if row['scenario'] == 'heavy'}
    assert heavy['THR']['energy_kwh'] == heavy['LR']['energy_kwh']
    assert float(heavy['LR']['energy_kwh']) > float(heavy['IQR']['energy_kwh'])
    override = Path('target/meeting-build/classes/org/cloudsimplus/allocationpolicies/migration/VmAllocationPolicyMigrationAbstract.class')
    assert not override.exists(), 'Runtime override leaked'
    assert not list(Path('target/meeting-build/classes/kfupm/coe619').glob('UnderloadReservationPlanner*.class'))
    output = dict(frozen_files_verified=plan['frozen_files'], byte_identical_pairs=[list(map(str, pair)) for pair in pairs],
                  unchanged_planning_checks=len(planning), metric_rows=15,
                  zero_power_checks=len(power), zero_final_placement_checks=len(placement),
                  exact_forecast_replay_checks=replay['forecast_checks'],
                  migrated_vm_instances_excluded_by_legacy=sum(row['migrated_legacy_exclusions'] for row in metric['rows']),
                  runtime_patch_cleared=True, predictive_energy_advantage=False,
                  heavy_energy_kwh={key: float(row['energy_kwh']) for key, row in heavy.items()})
    (root / 'verification.json').write_text(json.dumps(output, indent=2) + '\n')
    print(json.dumps(output, indent=2))


if __name__ == '__main__':
    main()
