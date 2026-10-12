# HabitApp

An Android app that learns from what my phone already records, predicts when a quick check is about to turn into a spiral, and learns which nudges actually work on me.

## Why

Planners and phone modes didn't work for me: I worked around my own settings. The goal isn't less screen time. It's two things:

- **Starting work:** less time between waking (or a planned slot) and actually starting.
- **Escaping spirals:** fewer quick checks that turn into 45 minutes, and faster exits when they do.

## How it works (planned)

- **Sensing:** reads app usage, unlocks and screen events through Android's usage stats, with no manual logging.
- **Your day:** editable daily slots, one-tap habit ticks, and a one-line day log with mood.
- **Risk model:** predicts the chance of a spiral in the next 30 minutes, with an explanation for each prediction.
- **Adaptive nudges:** a contextual bandit learns which intervention works, and when to stay quiet.
- **The robot:** a small pop-up robot with dry humour that suggests, then gets firmer, then gates an app only if needed.
- **Weekly review agent:** an LLM agent that summarises the week, grounded in the data. It never invents a number.

## Principles

- **Real ML, not a wrapper.** Models make the decisions; the LLM explains.
- **Transparent.** Every prediction and nudge shows why, and all data can be seen and corrected.
- **Never breaks the phone.** Calls, maps, banking and family always work, and uninstalling gives everything back.
- **Local first.** Data stays on the device unless sync is switched on.

## Tech

- Android: Kotlin, Jetpack Compose, Room, WorkManager
- ML and backend: Python, pandas, scikit-learn, PyTorch, FastAPI

## Status

**Stage 0: setup.** The project runs on a test device. Next: Stage 1, sensing.