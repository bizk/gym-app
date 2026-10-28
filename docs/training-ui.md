# Training interface

The phone supports planning and reviewing; the watch supports the next action during a workout. Both use a near-black background, warm white text, muted secondary information, and lime for the primary action and progress. Labels remain in the app's existing Spanish.

## Phone

- Home prioritizes resuming an active session, then starting a new one. Import and history remain available below.
- Training shows session progress, exercise name, prescribed reps and load, and individual set status. A bottom action stays visible while the details scroll.
- The exercise list allows jumping to any exercise. The next-exercise action finds an unfinished exercise, including one skipped earlier.
- Videos, editing, effort, undo, and finishing early live in the exercise options.

The phone home screen defaults to the highest numbered imported plan (the workbook's month), with a month/plan picker. Week buttons show one segment per plan day, in day order: green for all exercises completed, yellow for at least one, gray for none. Selecting a week lists its days and completed exercise counts; selecting a day starts that week directly. Active sessions remain available above this overview and must be finished before starting another.

An exercise counts only when all prescribed sets are recorded; closing early does not count. Progress combines completed exercises from saved sessions and the current session, without counting the same exercise twice. New sessions retain their plan name for phone/watch synchronization. Legacy sessions are included only when their day and exercise list identify exactly one imported plan, avoiding attribution to the wrong month. Ambiguous old sessions remain in history.

## Watch

- The exercise face uses an outer lime ring for completed sets across the session and an inner cyan ring for completed sets in the current exercise. Both represent actual completed sets against the prescription.
- Previous, check, and next sit in the center below the exercise name, reps, and load. The check immediately records one set. A small undo control removes the most recent set and reopens the exercise. Verde, Amarillo, Rojo, or Negro is requested once when the exercise is complete or closed early, and saved on the exercise. Cancel skips the rating without removing completed sets.
- Scrolling below the face reveals Descansar, Terminar serie, Modificar carga, and Más opciones. After rating a finished exercise, a five-second prompt offers rest; choosing Seguir or letting it expire advances to the next unfinished exercise. Choosing rest defers that advance until Seguir on the rest screen. The last exercise remains visible when no unfinished exercises remain. Terminar serie closes the current exercise early without marking outstanding sets as done; options allow reopening it.
- Controls have a minimum height of 48 dp. Round-screen padding, centered text, short action labels, and rotary scrolling support use during training. Long names occupy up to two lines, with the full name available to accessibility services.
- Más opciones is an overlay over the exercise; a left swipe or Back closes it while retaining the exercise scroll position. Rest actions are excluded from this overlay.
- Rest defaults to 60 seconds. Modificar descanso lives on the rest screen and offers 30, 60, 90, or 120 seconds. The rest screen offers continuing immediately and adding 30 seconds. A double vibration signals the end while the screen is active.
- Rest uses elapsed time, so returning after backgrounding or rotating recalculates the remaining time. It does not provide a background alarm or notification, and its deadline is local to this device.

## Device validation

Build and lint both variants. Before release, check a small round watch and a phone with gesture navigation:

1. Import a real workbook and start a session on each device.
2. Read a long exercise name and load prescription at normal and enlarged font sizes; scroll to every action and test rotary input.
3. Complete a set and undo using the face control; check that each tap records only one set and undo reopens a completed exercise. Finish or close an exercise, select a color, and test both rest choices and the five-second default. Open options, scroll, and swipe left to verify that the underlying exercise position is retained.
4. Background and rotate during rest, then return. Check elapsed time and haptic feedback.
5. Skip an exercise, complete the last exercise, and confirm that Next returns to unfinished work.
6. Finish a session, check history, and verify synchronization on paired physical devices.

Physical-device checks are required to validate round-screen clipping and tap comfort; build and lint cannot establish either.
