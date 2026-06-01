# LeakLens — User Manual

## Table of Contents
1. [Introduction](#introduction)
2. [System Requirements](#system-requirements)
3. [Installation](#installation)
4. [Getting Started](#getting-started)
5. [Features Guide](#features-guide)
6. [Configuration](#configuration)
7. [Troubleshooting](#troubleshooting)
8. [FAQ](#faq)

---

## 1. Introduction

**LeakLens** is an Android Studio IDE plugin that brings LeakCanary's memory leak detection directly into your development environment. It eliminates the need to context-switch between your IDE and the LeakCanary notification app by providing:

- Real-time heap dump capture and analysis
- Source code navigation from leak traces
- Automated fix suggestions with one-click apply
- Preventive static inspections at write-time
- Real-time memory monitoring
- CI-ready report exports (HTML/JSON/SARIF)

---

## 2. System Requirements

| Requirement | Minimum |
|-------------|---------|
| IDE | Android Studio Iguana (2023.2) or newer |
| JDK | JDK 17+ |
| OS | macOS, Windows, Linux |
| Device | Connected Android device/emulator (USB debugging enabled) |
| App | Debug build (recommended: with LeakCanary dependency) |
| RAM | 4GB+ free (heap analysis of large dumps requires memory) |

---

## 3. Installation

### From JetBrains Marketplace
1. Open Android Studio
2. Navigate to **Settings → Plugins → Marketplace**
3. Search for **"LeakLens"**
4. Click **Install** → Restart IDE

### From Source (Development)
```bash
git clone https://github.com/dev-vikas-soni/leak-lens.git
cd leak-lens
./gradlew buildPlugin
```
Then install via **Settings → Plugins → ⚙ → Install Plugin from Disk** → select `build/distributions/LeakLens-*.zip`

### From GitHub Releases
1. Download the latest `.zip` from [Releases](https://github.com/dev-vikas-soni/leak-lens/releases)
2. **Settings → Plugins → ⚙ → Install Plugin from Disk**
3. Select the downloaded zip → Restart

---

## 4. Getting Started

### First-Time Setup

1. **Open LeakLens Tool Window**
   - View → Tool Windows → LeakLens
   - Or click the LeakLens tab at the bottom panel

2. **Connect a Device**
   - Ensure USB debugging is enabled on your device
   - Connect via USB or start an emulator
   - Verify with `adb devices`

3. **Run Your App in Debug Mode**
   - Best results with LeakCanary added to your app:
     ```groovy
     debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.14'
     ```

4. **Capture Your First Heap Dump**
   - **Tools → LeakLens → Dump Heap Now**
   - Or wait for LeakCanary auto-detection (if using Auto-Detect)

---

## 5. Features Guide

### 5.1 Heap Dump & Analysis

#### Manual Dump
1. Go to **Tools → LeakLens → Dump Heap Now**
2. Select device (auto-selected if only one)
3. Select process (your debug app)
4. Wait for analysis (progress bar shown)
5. Results appear in the Leaks tab

#### Import Existing .hprof
1. **Tools → LeakLens → Import .hprof File**
2. Browse to any `.hprof` file
3. Analysis runs automatically

#### Auto-Detect (LeakCanary Integration)
1. **Tools → LeakLens → Start Auto-Detect**
2. Plugin monitors logcat for LeakCanary's heap dump messages
3. When LeakCanary dumps, LeakLens auto-pulls and analyzes

### 5.2 Leak Dashboard

The tool window has two main tabs:

**Leaks Tab:**
- Left: Tree view grouped by severity (🔴 Critical, 🟡 Warning, 🟢 Library)
- Right: Detailed leak trace with clickable class names
- Click any class name → navigates to source code

**Memory Tab:**
- Real-time graph showing Java Heap, Native Heap, Total PSS
- Requires active monitoring (Tools → LeakLens → Start Memory Monitor)

**History Tab (inside Leaks):**
- Past analysis entries with timestamps
- Summary stats per entry
- Click to reload past results

### 5.3 Source Navigation

- In the leak detail panel, all class names appear as **blue underlined links**
- **Single click** on any class name → opens that file in the editor
- The plugin uses `JavaPsiFacade` to resolve fully qualified class names

### 5.4 Editor Gutter Icons

When leaks are detected, the plugin places icons in the editor gutter:
- 🔴 (Error icon) — Class is the leaking object (Critical)
- ⚠️ (Warning icon) — Class appears in a leak trace reference chain
- ℹ️ (Info icon) — Known library/framework leak

### 5.5 Fix Suggestions

Each detected leak shows a **"Suggested Fix"** panel with:
- Root cause explanation
- Step-by-step fix instructions
- Before/After code snippets
- AI-generated suggestions (if enabled, marked with 🤖)

### 5.6 Quick Fix Actions (Alt+Enter)

In the editor, press **Alt+Enter** on leak-related code to see LeakLens quick fixes:
- "Add handler.removeCallbacksAndMessages(null) in onDestroy"
- "Null out _binding in onDestroyView"
- "Use applicationContext instead of Activity context"

### 5.7 Static Inspections (Preventive)

LeakLens detects these patterns **at write-time** (before runtime):

| Inspection | What It Detects |
|-----------|----------------|
| Static Activity Reference | Activity/Fragment in static/companion field |
| Anonymous Inner Class Leak | Anonymous class holding outer Activity |
| Context in Singleton | Activity Context passed to Singleton |
| Missing RemoveCallbacks | Handler without cleanup in onDestroy |
| GlobalScope Leak | GlobalScope.launch in Activity/Fragment |
| View Reference Held | View/Binding not nulled in onDestroyView |

View/configure: **Settings → Editor → Inspections → LeakLens**

### 5.8 Memory Monitor

1. **Tools → LeakLens → Start Memory Monitor**
2. Select process to monitor
3. View real-time graph in the **Memory** tab
4. If Java Heap exceeds threshold, auto-triggers heap dump

### 5.9 Reports & Baselines

#### Export Report
- **Tools → LeakLens → Export Report**
- Choose format: HTML (visual), JSON (data), SARIF (CI/GitHub)

#### Save Baseline
- **Tools → LeakLens → Save as Baseline**
- Creates `leak-baseline.json` in project root
- Commit to VCS — baseline leaks are suppressed in future analyses
- New leaks (not in baseline) will still be flagged

### 5.10 Deobfuscation

For release-build heap dumps with R8/ProGuard:
- Plugin auto-detects `app/build/outputs/mapping/release/mapping.txt`
- If not found, configure path in Settings
- Obfuscated class names are automatically mapped back to original names

---

## 6. Configuration

### Settings Location
**Settings → Tools → LeakLens**

### Available Settings

| Setting | Default | Description |
|---------|---------|-------------|
| Auto-detect | Off | Monitor logcat for LeakCanary dumps |
| Show gutter icons | On | Display leak markers in editor |
| Auto heap dump threshold | 256 MB | Trigger dump when Java heap exceeds this |
| Monitor interval | 5000 ms | Memory polling frequency |
| Use baseline | On | Suppress baseline leaks |
| Auto-detect mapping | On | Find mapping.txt automatically |
| Persist history | On | Save analysis history across sessions |
| Max history entries | 50 | History retention limit |
| Enable AI | Off | Send traces to AI for novel fixes |
| AI Provider | None | "openai" or "gemini" |
| AI API Key | — | Your API key |
| Anonymize packages | On | Strip package names before AI |

---

## 7. Troubleshooting

### "No connected devices found"
- Run `adb devices` in terminal to verify
- Ensure USB debugging is enabled
- Try `adb kill-server && adb start-server`

### "No debuggable processes found"
- Ensure your app is running in debug mode
- Check that the app has `android:debuggable="true"` in manifest (default for debug builds)

### Analysis takes too long
- Large heap dumps (500MB+) take 30-60s
- Ensure sufficient RAM (4GB+ free)
- Analysis runs in background — you can continue working

### Gutter icons not appearing
- Check Settings → Tools → LeakLens → "Show gutter icons" is enabled
- Re-run analysis — icons update only after analysis completes
- File must be open in editor

### AI suggestions not working
- Verify Settings → Tools → LeakLens → AI is enabled
- Check API key is valid
- Ensure network connectivity
- AI only triggers for leaks without static rule matches

---

## 8. FAQ

**Q: Does LeakLens require LeakCanary in my app?**
A: No. You can use manual heap dump or import .hprof files. However, LeakCanary enables auto-detection which is the most seamless workflow.

**Q: Does it work with Compose apps?**
A: Yes. The Shark engine analyzes the heap regardless of UI framework. The custom ObjectInspectors include Compose-specific detectors.

**Q: What about release builds?**
A: Import release .hprof files and provide a mapping.txt for deobfuscation. The plugin auto-detects mapping files from build output.

**Q: Is any data sent to the internet?**
A: By default, NO. Zero network calls. AI suggestions are opt-in only, and you control what gets sent.

**Q: Can I share baselines with my team?**
A: Yes! `leak-baseline.json` is designed to be committed to VCS. Team members with the plugin will automatically suppress baseline leaks.

**Q: How does it differ from Android Studio Profiler?**
A: The Profiler requires manual heap dump + manual instance tracking. LeakLens automates detection, provides root cause, and suggests fixes — all in one flow.

