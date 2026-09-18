# ResourceManager

Android process manager with root access. Reads system resource usage 
directly from Linux kernel interfaces and dumpsys output.

## Features
- Real-time CPU and memory usage from `/proc/stat` and `/proc/meminfo`
- Full process list via root `ps` with sorting possibility
- Per-app battery consumption from `dumpsys batterystats` 
  (CPU fg/bg, wakelocks, sensors, network)
- Snapshot history stored in local database
- Self-update via GitHub Releases API

## Tech Stack
Kotlin · Coroutines · Room · Retrofit · OkHttp · ViewBinding

## Requirements
- Rooted Android device
- Android 13+
