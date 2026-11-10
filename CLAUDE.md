# Working on ChairPlay

Rules for coding agents, learned from mistakes made on this port. DiPlay itself lives in the `upstream/` submodule; its `AGENTS.md` and CI command are for the Android app and do not apply here.

## Scope

- For every upstream update, audit local overrides and bug workarounds. Remove code superseded by equivalent upstream behavior, keeping regression coverage. Retain only documented desktop differences or missing features.
- Never edit files inside `upstream/`: it is DiPlay, pinned to a commit, and moving it to a newer DiPlay must stay a pointer change. Replace or wrap upstream code from this repository instead.
- Notes and plans that are not for the repository go to `desktop-docs/` (ignored by git).
- To take a newer DiPlay: `git -C upstream fetch`, `git -C upstream checkout <commit>`, build and test, then commit the `upstream` pointer. Upstream can change the core's API, so a new DiPlay can break this build. When the submodule points at a mirror rather than DiPlay's GitHub, run `scripts\mirror-upstream.cmd <url>` first (add `--local` if only this clone uses the mirror): the script fills it and pins every commit used here.
- Never fetch DiPlay's refs into this repository (only into `upstream/`); its tags here would be pushed with `--tags`. The pre-push hook refuses history that does not start at this project's first commit.
- Git hooks live in `.githooks/` (`git config core.hooksPath .githooks`). Never bypass them with `--no-verify`; a refused commit means a key or app-folder file is staged. After changing a hook or `scripts\mirror-upstream.cmd`, run `scripts\test-repo-tools.cmd`.
- The owner works in cmd: scripts meant to be run by hand are `.cmd` (CRLF, Windows' own `sort`/`find` by full path). Hooks stay bash, because git runs them with its bash. Every process start is slow on Windows; batch work into few processes (one `git cat-file --batch`, not one call per file).
- Another agent session may share this working tree: never stage ahead of the commit (a `git rm` sat in the index and went into another session's commit). Commit only your own paths: `git commit -- <paths>`.
- Git here is 2.37: `merge-tree --write-tree` does not exist, and its error exit is not a conflict.

## Building and testing

- JDK 17 only. From Git Bash: `export JAVA_HOME="/c/Program Files/Java/jdk-17.0.1"`, then `./gradlew …`. Never run DiPlay's own build in `upstream/`: it needs the Android SDK.
- Run tests with a bound and a log, not through a pipe that can hide a hang: `timeout 900 ./gradlew test --console=plain > <scratchpad>/test.log 2>&1`, then read the log.
- While working, run only the tests of what changed: `./gradlew test --tests '<package>.*'` for Kotlin, `npm test -- <files>` in `webui/` for the page. `test` runs the Kotlin tests alone; `./gradlew check` runs them and the page's. Run `check` once before a task's commit and once before a merge.
- Only the session that started the builds stops the Gradle daemon: a subagent never runs `./gradlew --stop`, which breaks other builds.
- Count results from `build/test-results/test/*.xml`, and check their time: a run that did not reach the tests leaves the previous run's reports behind.
- Upstream's C code (SpeexDSP behind `SpeexEchoCanceller`) is built by `speexEchoDll` with a pinned zig that `installZig` downloads into the Gradle user home. The first build needs about 6 minutes and the network. To move zig, change `zigVersion` and `zigSha256` in `gradle/native.gradle.kts` together (the hash is in https://ziglang.org/download/index.json). The DLL ships in the image's root, next to the exe: jpackage's `app/` folder is not on `java.library.path`.
- `gradlew.bat` is patched: it passes no `-classpath` (the empty one broke the Oracle `javapath` java.exe stub when `JAVA_HOME` is unset). Regenerating the wrapper (`gradle wrapper`) drops the patch; put it back.
- Every commit must build on its own. When splitting a change into several commits, compile the earlier one alone (a temporary `git worktree` at that commit) before calling it done.
- Write the failing test first and watch it fail for the expected reason (superpowers:test-driven-development).
- Except for texts and layout: no test pins what the app says or how it is laid out. No assertion on a label, caption, tooltip, message, title, the settings file's comments or any other wording; on the order, count, placement or grouping of elements; or on CSS classes, styles, sizes or drawing constants. Such tests only restate the code, catch no bug and double the cost of every change the owner asks for; check those changes with a screenshot of the page instead. Tests stay for behavior: what a control saves, sends or asks the app for, which state enables, disables or holds a control, which page or status a state leads to, parsing, validation, geometry and protocol. A text may still find an element (`getByRole('button', { name: 'Connect' })`).

## Size of files

- A code file (Kotlin, TypeScript, CSS, C, scripts, build files, tests alike) stays under 300 lines, split by what it is about: a stylesheet per screen or component, a class or concern per Kotlin file, a test file per subject.
- A longer file needs a reason, written in a comment at its top ("Over 300 lines: …"), such as a protocol catalog kept whole. Length alone, or "it grew", is no reason.
- Copies and forks of others' code stay as they are, whatever their length and with no comment added: the files that replace DiPlay's (`com.shilapi.xcertplay`), and code taken from other projects (`native/maneuver`, from MHI2Q).
- A change that would take a file past 300 lines splits it first. A file already over the limit without a reason gets no longer; its split is a refactoring of its own (the list is in desktop-docs/file-size-refactor.md).

## Editing files

- Never put a script or file content longer than one line into a Bash command: no heredoc (`<<'EOF'`), no multi-line `python -c`, no `echo … >`. Quotes, backticks, `${…}` and backslashes in it break the shell or are rewritten by the tool layer (`\n` turns into a real line break), and this has wasted time about 20 times. Write the script with the Write tool into the scratchpad and run it by path (`python <scratchpad>/edit.py <args>`), or change the file with Edit or Write. This holds for every language and for "simple" content too.
- Avoid `sed` on lines with backslashes or Windows paths; use the Edit tool.
- Kotlin initialises properties top to bottom. A property (or companion `val`) that calls code using a property declared further down gets null: declare in order, or make it `by lazy`.

## Interface texts

Every text the app shows (web UI, controls window, window titles, messages) follows the Microsoft Writing Style Guide:

- Sentence case everywhere: tabs, labels, buttons, choices, checkboxes, statuses, tooltips and group headers (written "Safe area"; CSS may show them in capitals). Names keep their spelling: ChairPlay, CarPlay, AltScreen, iPhone, Siri, Wi-Fi, Bluetooth, DiPlay. So do acronyms (HUD, ETA) and key names as Windows writes them (Enter, Esc, Shift+Tab, Num Enter). Units are lower case: px, fps, mm, ppi, s.
- One word, one spelling: choices side by side do not show the same word in two cases ("Turn card", not "Card" beside "Map + card").
- No full stop after a label, button, choice or one-sentence tooltip; a text of several sentences has them.
- Tooltips on actions are imperative ("End the session and connect again (Ctrl+R)"); a disabled control's tooltip says what enables it ("Connect the iPhone first").
- A message names the problem, then what to do: "MFi files not found. Put identity.pk8 and certificate.p7b in this folder:".
- US English (License, color), and a comma before the last "and" of a list.
- No caption explains what happens when a switch is off or a field is blank: hover explains a field.

## Layout of the settings pages

- Every page fits 640 CSS px wide (a 1280 px window at 200 %) with no horizontal scroll. After changing a page, open it at 640 width (Playwright against the running app) and check that no element's right edge passes the viewport and no scroll container's `scrollWidth` exceeds its `clientWidth`; look at a screenshot too.
- One screenshot per state: 640 CSS px wide, the whole page's height in that one shot. No second width, no separate short and long shots of the same screen.
- When a mock exists, its icons, images and other finished assets go into the app as they are; draw nothing of your own where the mock has one.
- Wrap early: a row holds one field, one group of choices, or one checkbox. Two fields share a row only when they are one value's parts (width × height, latitude and longitude, power and mass) and fit at 640.
- A row's label is on its left. In a row of several fields, the row's label names the whole, and each field's own name or unit goes to its right, in the unit's style ("[150] hp [1300] kg", "[48.85] latitude [2.29] longitude"), never before it. The same holds on every page.
- Nothing in a row breaks or is cut: a label, unit, choice, or button keeps its text on one line and whole. A row that does not fit at 640 gets split into rows, never squeezed.

## Running the app and background work

- Never hard-kill a running CarPlay session (`taskkill /F`, stopping the background task that started the exe): the iPhone then ignores new sessions until it is restarted. Stop it with `taskkill //PID <pid>` without `/F`, which closes the window and runs the shutdown hook, or ask the owner to close the window.
- On this Windows machine, stopping a background task or a Monitor kills only the Git Bash wrapper; its `tail -F`, `grep --line-buffered` and `sleep` children keep running inside the project folder and block renaming it. Prefer bounded waits (`for i in $(seq 1 60); do grep -q … && break; sleep 1; done`) over `tail -F`; after stopping any pipeline, kill the leftovers by command line (PowerShell `Get-CimInstance Win32_Process`, matching the scratchpad log path), never other sessions' processes.
- Stop the Gradle daemon (`./gradlew --stop`) when the session's builds are done.
- `tasklist | grep …` lists its own `grep.exe`; that one is not a leftover.
- Install the app with `./gradlew deploy` into `Documents\ChairPlayApp`; it keeps the data files there. Never print or copy `identity.properties`, `offline-mfi/` or the Wi-Fi passphrase.
