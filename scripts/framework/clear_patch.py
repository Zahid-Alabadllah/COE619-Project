from pathlib import Path
root=Path(__file__).resolve().parents[2]
compiled=root/"target/meeting-build/classes/org/cloudsimplus/allocationpolicies/migration/VmAllocationPolicyMigrationAbstract.class"
assert compiled.resolve().is_relative_to((root/"target").resolve())
compiled.unlink(missing_ok=True)
