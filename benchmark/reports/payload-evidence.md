# Payload Evidence Report

This report presents evidence of payload size reduction achieved by filtering and solving constraints locally using the Constraint-Native Generative Engine.

For more details, see the [Methodology](../../docs/benchmark-methodology.md).

## Scenarios

| Inventory Size | Desired Solutions | Baseline (Bytes) | Constrained (Bytes) | Reduction (%) | Feasible Solutions | Hard Violations | Unknown IDs Accepted | Nodes Expanded | Termination | Pairwise Diversity |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 25 | 3 | 3723 | 490 | 86.84% | 3 | 0 | 0 | 352 | BOUNDED_SEARCH_COMPLETED | 0.75 |
| 100 | 3 | 14721 | 465 | 96.84% | 3 | 0 | 0 | 2193 | BOUNDED_SEARCH_COMPLETED | 1.0 |
| 500 | 3 | 73593 | 405 | 99.45% | 3 | 0 | 0 | 11362 | BOUNDED_SEARCH_COMPLETED | 1.0 |
| 1000 | 3 | 147235 | 425 | 99.71% | 3 | 0 | 0 | 22438 | BOUNDED_SEARCH_COMPLETED | 1.0 |

