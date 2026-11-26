"""Audit the legacy PDM and its capacity-time fallback at an exact observation boundary.
The correction remains an explicit approximation, not the paper's demand integral.
"""
import argparse
import csv
import json
from pathlib import Path


def calculate(rows):
    legacy_terms, corrected_terms, details, undefined = [], [], [], []
    for row in rows:
        capacity = float(row['capacity_mips'])
        finished = float(row['finished_work_mi'])
        old_denominator = float(row['legacy_denominator_mi'])
        completed = float(row['completed_transfer_seconds'])
        pending = float(row['pending_transfer_seconds'])
        end = float(row['observed_end'])
        start = float(row['job_start'])
        status = row['job_status']
        if min(capacity, completed, pending, end) < 0:
            raise ValueError('Negative capacity, cost or observation window')
        if status not in {'SUCCESS', 'INEXEC'}:
            raise ValueError('Unaccounted job state: ' + status)
        stop = min(end, float(row['job_finish'])) if status == 'SUCCESS' else end
        if not 0 <= start <= stop <= end:
            raise ValueError('Invalid observed execution window')
        loss = .1 * capacity * (completed + pending)
        legacy_loss = .1 * capacity * completed
        if old_denominator > 0:
            legacy_terms.append(legacy_loss / old_denominator)
        denominator = finished if finished > 0 else capacity * (stop - start)
        reason = 'finished_job_work' if finished > 0 else 'observed_capacity_time'
        if denominator > 0:
            corrected_terms.append(loss / denominator)
        elif loss > 0:
            undefined.append(row['vm'])
        details.append({
            'vm': row['vm'], 'legacy_denominator_mi': old_denominator,
            'windowed_denominator_mi': denominator, 'denominator_basis': reason,
            'completed_transfer_seconds': completed, 'pending_transfer_seconds': pending,
            'estimated_loss_mi': loss,
            'migrated_but_legacy_excluded': completed > 0 and old_denominator <= 0,
            'legacy_runtime_seconds': float(row['vm_execution_seconds']),
            'observed_execution_seconds': stop - start,
        })
    return {
        'legacy_reproduced_pdm': sum(legacy_terms) / len(legacy_terms) if legacy_terms else 0.,
        'windowed_capacity_time_pdm': None if undefined else (sum(corrected_terms) / len(corrected_terms) if corrected_terms else 0.),
        'legacy_vm_count': len(legacy_terms), 'windowed_vm_count': len(corrected_terms),
        'migrated_legacy_exclusions': sum(r['migrated_but_legacy_excluded'] for r in details),
        'undefined_nonzero_cost_vms': undefined, 'vms': details,
    }


def boundary_checks():
    def record(**changes):
        row = dict(vm='0', capacity_mips='2000', finished_work_mi='0', legacy_denominator_mi='0',
                   completed_transfer_seconds='3', pending_transfer_seconds='0', observed_end='100',
                   job_start='0', job_finish='-1', job_status='INEXEC', vm_execution_seconds='0')
        row.update(changes)
        return row
    a = calculate([record()])
    assert a['legacy_reproduced_pdm'] == 0 and abs(a['windowed_capacity_time_pdm'] - .003) < 1e-15
    assert a['migrated_legacy_exclusions'] == 1
    b = calculate([record(finished_work_mi='10000', legacy_denominator_mi='10000', job_status='SUCCESS', job_finish='50')])
    assert abs(b['windowed_capacity_time_pdm'] - .06) < 1e-15
    c = calculate([record(completed_transfer_seconds='0', pending_transfer_seconds='2')])
    assert abs(c['windowed_capacity_time_pdm'] - .002) < 1e-15
    d = calculate([record(completed_transfer_seconds='0', pending_transfer_seconds='0')])
    assert d['windowed_capacity_time_pdm'] == 0
    e = calculate([record(job_start='100')])
    assert e['windowed_capacity_time_pdm'] is None and e['undefined_nonzero_cost_vms'] == ['0']
    return 5


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    baseline = {(r['scenario'], r['policy']): r for r in csv.DictReader((args.run / 'experiments.csv').open())}
    output = {'boundary_checks': boundary_checks(), 'method': 'capacity-time approximation with original job start and common observation end; completed-job denominators unchanged', 'rows': []}
    for path in sorted((args.run / 'metric_audit').glob('*-vms.csv')):
        scenario, policy = path.stem[:-4].rsplit('-', 1)
        result = calculate(list(csv.DictReader(path.open())))
        stored = float(baseline[scenario, policy]['pdm'])
        if abs(stored - result['legacy_reproduced_pdm']) > max(1e-13, abs(stored) * 1e-8):
            raise AssertionError('Legacy PDM mismatch: ' + path.name)
        result.update(scenario=scenario, policy=policy, stored_pdm=stored)
        output['rows'].append(result)
    if len(output['rows']) != len(baseline):
        raise AssertionError('Metric exports do not cover every policy row')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2) + '\n')
    print('Legacy metric reproduced for', len(output['rows']), 'rows; migrated VMs excluded by legacy:', sum(r['migrated_legacy_exclusions'] for r in output['rows']))


if __name__ == '__main__':
    main()
