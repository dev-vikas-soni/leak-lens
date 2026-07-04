# LeakLens Marketing Assets & Pitch Deck 📣

## 1. Tagline

> Shift-Left Memory Governance for the Kotlin Ecosystem.

## 2. Elevator Pitch (30 Seconds)

LeakLens is the preventative memory infrastructure for Android/Kotlin. Unlike traditional profilers
that tell you what *has already* leaked, LeakLens uses real-time static analysis and host-side heap
processing to catch leaks *while you write code*. It reduces the fix cycle from days to
milliseconds, ensuring your app stays stable without the QA context switch.

## 3. Marketplace Short Description

Shift-Left memory leak detection for Android/Kotlin. Catch leaks as you type with real-time UAST
inspections, host-side Shark analysis, and context-aware AI fixes. Zero-SDK, Zero-Setup.

## 4. One-Minute Pitch

Most memory tools wait for your app to crash. LeakLens is different. It's built on a "Zero-SDK"
host-side engine that correlates live code inspections with runtime heap dumps. As you type a
`GlobalScope.launch` in an Activity, LeakLens flags it instantly. If a leak persists, our
Shark-powered engine analyzes it locally and Gemini suggests an idiomatic Kotlin fix. It's not just
a tool; it's a memory safety net for modern Kotlin engineering.

## 5. LinkedIn Announcement

Excited to announce the launch of **LeakLens**! 🧠💧

We are shifting memory management from "Post-Mortem Debugging" to "Real-time Correctness."
✅ 12+ UAST Inspections (Compose, Flows, Coroutines)
✅ Host-side Shark Heap Analysis (Zero-SDK)
✅ Context-Aware AI Fixes

Stop debugging leaks in QA. Start preventing them in the IDE.

[Link to Marketplace] #Kotlin #AndroidDev #JetBrains #OpenSource

## 6. Conference Abstract (CFP)

**Title**: Shifting Memory Correctness to the Left: Building IDE-Native Performance Tooling

**Abstract**:
Memory leaks remain the top cause of non-deterministic crashes in Android. While tools like
LeakCanary have improved the status quo, they remain reactive. In this talk, we explore how to build
a "Shift-Left" memory governance engine using the IntelliJ Platform SDK and UAST. We will dive into
how LeakLens performs host-side graph analysis of 1GB heap dumps without freezing the UI, and how
Coroutines and StateFlow enable a real-time feedback loop between static code and dynamic runtime
states.
