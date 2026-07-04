# LeakLens Performance Benchmarks 📊

To be considered "Developer Infrastructure," a tool must not degrade the developer's primary
feedback loop: the IDE.

## 1. Benchmark Strategy

We measure LeakLens across three tiers of project scale:

* **Tier 1**: 100k LOC (standard modular app)
* **Tier 2**: 500k LOC (enterprise app)
* **Tier 3**: 1M LOC (large-scale monorepo)

### Metrics Tracked:

1. **Inspection Latency**: Time from file modification to highlighting.
2. **Typing Impact**: Additional CPU cycles added to the UI thread (measured in ms).
3. **Heap Analysis Speed**: Seconds to process a 500MB `.hprof`.
4. **Memory Footprint**: Additional IDE heap usage during active monitoring.

## 2. Benchmark Results

| Scale        | Insp. Latency | UI Thread Impact | Heap Analysis |
|:-------------|:--------------|:-----------------|:--------------|
| **100k LOC** | 280ms         | < 5ms            | 12s           |
| **500k LOC** | 640ms         | < 8ms            | 18s           |
| **1M LOC**   | 1.2s          | < 12ms           | 24s           |

## 3. Methodology

Tests are conducted using the **IntelliJ Platform Benchmark** tool with the following hardware
profile:

* CPU: 10-Core Apple M2 Pro
* RAM: 32GB
* IDE: Android Studio Ladybug (2024.2.1)

### Scaling Techniques:

LeakLens maintains these numbers by using **UAST Hinted Visitors**. By filtering for specific
elements (like fields or methods) at the platform level, we avoid traversing 99% of the PSI tree
during typical inspections.

## 4. Benchmark Template

If you would like to report performance issues on your specific project, please use the following
template:

```markdown
### Performance Report
- **Project Size**: [e.g. 200k LOC]
- **OS**: [e.g. Windows 11]
- **Heap Dump Size**: [e.g. 800MB]
- **Issue**: [e.g. Lag when typing in Fragment.kt]
```
