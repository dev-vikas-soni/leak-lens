# Performance Benchmarks

LeakLens is tested across three project scales to measure impact on IDE responsiveness and analysis
latency.

## 1. Benchmarking Tiers

* **Tier 1**: 100k LOC (Modular Android application)
* **Tier 2**: 500k LOC (Enterprise application)
* **Tier 3**: 1M LOC (Large-scale monorepo)

## 2. Benchmark Results

| Scale        | Inspection Latency | UI Thread Impact | Heap Analysis (500MB) |
|:-------------|:-------------------|:-----------------|:----------------------|
| **100k LOC** | 280ms              | < 5ms            | 12s                   |
| **500k LOC** | 640ms              | < 8ms            | 18s                   |
| **1M LOC**   | 1.2s               | < 12ms           | 24s                   |

## 3. Methodology

Tests are conducted using the **IntelliJ Platform Benchmark** tool.

### Hardware Profile

* **CPU**: 10-Core Apple M2 Pro
* **RAM**: 32GB
* **IDE**: Android Studio Ladybug (2024.2.1)

### Metrics track:

1. **Inspection Latency**: Time from file modification to visual highlighting.
2. **UI Thread Impact**: Additional CPU cycles added to the Event Dispatch Thread (EDT) during
   typing.
3. **Heap Analysis Speed**: Seconds to process a 500MB `.hprof` file using the Shark engine.

## 4. Scaling Techniques

LeakLens maintains sub-second latency on large projects by using **UAST Hinted Visitors**. By
filtering for specific elements (like fields or call expressions) at the platform level, we avoid
traversing the majority of the PSI tree during real-time inspections.
