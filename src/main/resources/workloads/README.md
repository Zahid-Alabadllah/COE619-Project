# Fixed workload manifests

Each .txt file lists existing trace paths in VM order. Runtime never samples or shuffles them. Every policy uses the same list for a case.

Real A/B/C are three disjoint groups of 20 paths frozen from the name-sorted real pool after Python Random(619).shuffle, taking consecutive blocks of 20. This preparation detail records provenance, not a runtime parameter. It makes no representativeness claim. Diurnal and heavy use their folders in name order. Do not edit lists to improve a policy's outcome; changes require new evidence and updated reports.
