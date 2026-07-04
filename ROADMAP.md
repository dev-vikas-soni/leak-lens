# LeakLens Roadmap 🗺️

Our mission is to make memory management in Kotlin/Android as effortless as writing code. Here is
our vision for the future of LeakLens.

## 🎯 Near-Term (Now)

- [ ] **Hardened PSI Fixes**: Moving from comment-based fixes to semantic `KtPsiFactory`
  transformations.
- [ ] **Direct AI API**: Integration with Gemini/OpenAI for in-IDE fix previews.
- [ ] **Performance Benchmarks**: Publishing first tier results for 1M LOC projects.

## 🚀 Mid-Term (Next)

- [ ] **Compose Multiplatform (Desktop)**: Support for heap analysis on JVM desktop targets.
- [ ] **KMP Common Module Inspections**: Rules for memory-safe state management in shared Kotlin
  code.
- [ ] **Data-Flow Analysis**: Deeper UAST analysis to track escaping references across classes.

## 🌟 Long-Term (Later)

- [ ] **CI/CD Integration**: A CLI tool that outputs SARIF reports for GitHub Actions to block leaky
  PRs.
- [ ] **On-Device Assistant**: A lite companion library for complex on-device triggers.
- [ ] **Visual Retention Graphs**: 2D interactive graphs of the shortest path to GC Roots.

## 🌈 Future

- [ ] **Automatic Regression Testing**: AI-generated test cases to verify memory fixes.
- [ ] **Native iOS Support**: Extending Shark-powered analysis to Kotlin Native (Experimental).

---

## 💡 How We Prioritize

We prioritize features based on:

1. **Crash Prevention**: Features that stop OOMs in production.
2. **Developer Velocity**: Features that reduce the "Time-to-Fix" a leak.
3. **Privacy & Security**: Ensuring zero-trust handling of user heap data.

*Note: This roadmap is a living document and will evolve based on community feedback and Kotlin
Foundation Grant outcomes.*
