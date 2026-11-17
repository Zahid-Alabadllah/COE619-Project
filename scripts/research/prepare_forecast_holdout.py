"""Prepare fixed, disjoint validation groups without consulting model outcomes."""
from pathlib import Path, PurePosixPath
from datetime import datetime, timezone
import argparse
import hashlib
import json
import zipfile
from urllib.request import urlretrieve

CODE = Path(__file__).resolve().parents[2]
COMMIT = '344ffdbf7e528bfa6f276014e889e68a0cb640c2'
URL = f'https://codeload.github.com/beloglazov/planetlab-workload-traces/zip/{COMMIT}'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--day', default='20110309')
    args = parser.parse_args()
    assert args.day.isdigit() and len(args.day) == 8
    root = CODE / 'results/predictor_investigation' / ('online_holdout_' + args.day)
    assert not (root / 'provenance.json').exists(), 'Do not overwrite a prepared validation set'
    archive = CODE / 'target/planetlab-source.zip'
    archive.parent.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        urlretrieve(URL, archive)
    # Digest pins the exact archive previously downloaded from the author.
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    assert digest == 'ef817107a2783a28283bcb9dcbe28c38977a14a7345e6171a4b8d3f00315a88f'
    pool = {}
    with zipfile.ZipFile(archive) as source:
        for member in source.infolist():
            parts = PurePosixPath(member.filename).parts
            if member.is_dir() or len(parts) != 3 or parts[1] != args.day:
                continue
            name = parts[2]
            assert name not in ('.', '..') and '/' not in name and '\\' not in name
            data = source.read(member)
            values = [float(x) for x in data.decode().split()]
            assert len(values) == 288 and all(0 <= x <= 100 for x in values), name
            pool[name] = (sum(values)/288, data)
    assert len(pool) >= 76
    heavy = sorted(pool, key=lambda name: (-pool[name][0], name))[:16]
    remaining = sorted(set(pool) - set(heavy))
    light = [remaining[i * (len(remaining)-1)//59] for i in range(60)]
    assert len(set(heavy + light)) == 76
    (root / 'traces').mkdir(parents=True)
    (root / 'manifests').mkdir()
    hashes = {}
    for name in heavy + light:
        (root / 'traces' / name).write_bytes(pool[name][1])
        hashes[name] = hashlib.sha256(pool[name][1]).hexdigest()
    for case, names in [('real_a', light[:20]), ('real_b', light[20:40]), ('real_c', light[40:]), ('heavy', heavy)]:
        paths = [(root / 'traces' / name).relative_to(CODE).as_posix() for name in names]
        (root / 'manifests' / (case+'.txt')).write_text('\n'.join(paths)+'\n')
    (root / 'manifests/diurnal.txt').write_bytes((CODE/'src/main/resources/workloads/diurnal.txt').read_bytes())
    provenance = dict(prepared_utc=datetime.now(timezone.utc).isoformat(), source='https://github.com/beloglazov/planetlab-workload-traces',
        commit=COMMIT, archive_sha256=digest, day=args.day, pool_size=len(pool),
        selection='16 highest mean traces; 60 evenly spaced sorted remaining names; disjoint fixed cases; not model outcomes',
        candidate='Input preparation fixes groups only; per-run options and study plans identify the model and frozen settings; no selection based on model outcomes in this script',
        caveat='Heavy group selected retrospectively by whole-day load; synthetic diurnal is a repeated regression case, not fresh data',
        per_file_sha256=hashes)
    (root/'provenance.json').write_text(json.dumps(provenance, indent=2)+'\n')
    print('Prepared', args.day, 'from', len(pool), 'traces; retained', len(hashes))


if __name__ == '__main__':
    main()
