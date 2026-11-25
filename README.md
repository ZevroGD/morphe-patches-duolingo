# morphe-patches-duolingo

Morphe patches for the Duolingo Android app.

**Target: Duolingo 6.98.4 (2463) only.** Duolingo's code is obfuscated, so the fingerprints use names that change
with every release. On any other version the patches fail instead of patching the wrong thing.

The patch bundle has been built and its JVM loader tests have passed in GitHub Actions. The patches have not yet been verified in a live Duolingo installation. See "Testing" below.

## Patches

Every patch is independently selectable.

### Lesson progress bar

| Patch | What it does |
|---|---|
| Linear lesson progress | Removes the experiment curve (`log4(1 + 3p)` and variants) that bends the bar's fill, so it fills in proportion to completed steps. |
| Constant speed lesson progress | Animates the lesson bars at constant speed: duration proportional to distance, no start delay, no easing or overshoot. Options: full bar duration (default 5000 ms) and an optional longest-single-update cap (default off). |
| Disable Rive lesson progress bar | Always uses the Android bar instead of the experimental Rive animation, whose easing cannot be changed from code. |

Use all three for genuinely constant-speed progress. Quest and other progress bars are not touched.

### Leagues

| Patch | What it does |
|---|---|
| Remove leagues | Removes Leagues and the Diamond Tournament by making Duolingo treat leaderboards as disabled, and redirects any attempt to open the Leagues tab to Learn. |
| Block leagues notifications | Drops Leagues, leaderboard, tournament and friend-nudge push notifications. |

### Video calls

| Patch | What it does |
|---|---|
| Remove video calls | Removes the video call tab and its promotions: session end, app open, Practice Hub, Plus purchase carousel and Plus checklist. |
| Remove video call path nodes (experimental, off by default) | Removes video call levels from the learning path. Unit completion is untested. |
| Hide video call quests | Hides the two daily quests that require a video call. |

### Not covered

- The league repair and league result screens, the notification settings rows and channel, and background leaderboard fetches.
- The Plus perks list for subscribers, and playable-ad promos.

## Testing

After building, check on a device: a lesson (steady bar, quests unchanged), the home tabs, a session end, the Practice
Hub, the Plus screens and the quests tab. Enable the experimental path node patch separately and complete a unit that
has video call levels.
