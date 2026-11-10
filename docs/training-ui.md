# Training interface

The phone supports planning and reviewing; the watch supports the next action during a workout. Both use a near-black background, warm white text, muted secondary information, and orange for the primary action and progress. Completed exercises in history use dark orange with black marks for contrast. Labels remain in the app's existing Spanish.

## Phone

- Home is minimal: the plan name (tap to switch plan or import another workbook) and a history icon at the top, then centered week and day selectors with ‹ › arrows, and one docked action. The week is a compact "‹ Semana N ›" label; the day is large with arrows at its sides and its status below (✓ Completado, ejercicios done, or Sin empezar). With an active session, home opens on its week and day, marked SESIÓN EN CURSO with series progress; other days and weeks can be browsed, and the action always returns to the open session.
- Training shows a top-left back arrow, a top-right gear for more options, session progress, exercise name, and prescribed reps and load. A pencil on the reps/load card edits reps, load and video in place. Sets form a single row of segments that fill with orange as they are completed, with an undo control at the end of that row. Video and Descanso sit side by side with icons. A bottom action stays visible while the details scroll.
- Dragging horizontally pages between exercises: the neighbouring exercise follows the finger as a preview, and a quarter-width drag or a fling settles it in about 160 ms. Secondary screens (options, exercise list, rest, history) use the top-left back arrow instead of a "Volver" button.
- Editing opens a full screen in the app's style rather than a system dialog: labelled surface fields with an orange focus ring, − / + steppers for numeric reps (1) and load (2.5, keeping the unit), a video field, and a docked Guardar cambios action that stays above the keyboard. Values such as 8-10 are left for manual editing.
- In the exercise list, completed exercises are filled dark orange with black text.
- The exercise list allows jumping to any exercise. The next-exercise action finds an unfinished exercise, including one skipped earlier.
- Effort, undo, reopening, and finishing early live in the exercise options.

The phone home defaults to the highest numbered imported plan. It opens on the current week: the last week with any recorded set, or the next one when that week is complete (all days complete). The day follows the same rule within the selected week; changing week recomputes the day. A thin strip under the week shows one segment per day: orange complete, dim orange started, gray untouched; the selected day is thicker. The action reads Empezar, Seguir or Repetir depending on that day's status.

An exercise counts only when all prescribed sets are recorded; closing early does not count. Progress combines completed exercises from saved sessions and the current session, without counting the same exercise twice. New sessions retain their plan name for phone/watch synchronization. Legacy sessions are included only when their day and exercise list identify exactly one imported plan, avoiding attribution to the wrong month. Ambiguous old sessions remain in history.

## Watch

- Home is a single round face: a thin progress ring at the edge (orange for completed sets), the week as a small spaced orange label (SEMANA N), the day in large light type (Día N), the series count, and a round orange play button with a smaller history button beside it. Without a session it reads TU PLAN / Entrenar and play opens the week picker.
- Starting a session on the watch takes three round picker screens: PLAN (opening on the latest; arrows follow the plan number, with one dot per week), then SEMANA, then SEMANA N · DÍA. Each shows the number large between ‹ › arrows (hidden at the ends), a status dot and label (green Completado, orange En curso, gray Sin empezar), and a round orange action; the edge ring turns green when that week or day is complete. The week screen also shows one dot per day of that week. Both open on the current week and day, using the same rule as the phone. The week's › action continues to the day; the day's play button starts the session. Dragging right or Back goes from day to week to plan to home.
- Vertical scrolling rests on whole screens: a fling, a drag past a fifth of the screen, or one rotary step moves exactly one screen. A drag to the right goes back one screen (exercise → home, rest → continue, list or edit → exercise; from home it closes the app); the system swipe-to-dismiss is disabled so this does not exit the app. Back behaves the same.
- While a session is open the watch runs it as a Wear OS ongoing activity (a silent workout notification from a special-use foreground service, Android 14+), so waking the watch returns to the app instead of the watch face, and the session icon appears on the watch face. It stops when the session finishes. Notification permission is requested at launch while a session is open.
- The app supports ambient mode: with Always On Display enabled, when the screen dims it stays in front, showing a black screen with dim gray text (day and week, exercise, sets done, or Descanso). Interaction restores the normal screen.
- The exercise face uses an outer orange ring for completed sets across the session and an inner cyan ring for completed sets in the current exercise. Both represent actual completed sets against the prescription.
- Previous, check, and next sit in the center below the exercise name, reps, and load; there is no set counter text, as the inner ring shows it. The check immediately records one set. A small undo control removes the most recent set and reopens the exercise. Verde, Amarillo, Rojo, or Negro is requested once when the exercise is complete or closed early, and saved on the exercise. Cancel skips the rating without removing completed sets.
- The second screen below the face is a 2 × 2 grid of icon tiles: timer with the rest length (Descansar), tick (Terminar, closes the exercise), pencil (Editar reps y carga), and gear (Más opciones). After rating a finished exercise, a five-second prompt offers rest; choosing Seguir or letting it expire advances to the next unfinished exercise. Choosing rest defers that advance until Seguir on the rest screen. The last exercise remains visible when no unfinished exercises remain. Terminar serie closes the current exercise early without marking outstanding sets as done; options allow reopening it.
- Controls have a minimum height of 48 dp. Round-screen padding, centered text, short action labels, and rotary scrolling support use during training. Long names occupy up to two lines, with the full name available to accessibility services.
- Más opciones is an overlay over the exercise with only Terminar sesión and Ir al inicio. The panel follows a right drag, uncovering the exercise beneath, and closes past a quarter of the width or on a fling; Back also closes it, retaining the exercise scroll position. Undo, reopening and effort live on the exercise face and rating prompt.
- Dragging left on the exercise reveals the history as a live preview and settles on it; dragging right from the history (or Volver al ejercicio, or Back) previews and returns to the exercise whenever a session is open, however the history was opened.
- Rest defaults to 60 seconds. Modificar descanso lives on the rest screen and offers 30, 60, 90, or 120 seconds. The rest screen offers continuing immediately and adding 30 seconds. A double vibration signals the end while the screen is active.
- Rest uses elapsed time, so returning after backgrounding or rotating recalculates the remaining time. It does not provide a background alarm or notification, and its deadline is local to this device.

## Device validation

Build and lint both variants. Before release, check a small round watch and a phone with gesture navigation:

1. Import a real workbook and start a session on each device.
2. Read a long exercise name and load prescription at normal and enlarged font sizes; scroll to every action and test rotary input.
3. Complete a set and undo using the face control; check that each tap records only one set and undo reopens a completed exercise. Finish or close an exercise, select a color, and test both rest choices and the five-second default. Open options and drag right to verify that the underlying exercise position is retained.
4. Background and rotate during rest, then return. Check elapsed time and haptic feedback.
5. Skip an exercise, complete the last exercise, and confirm that Next returns to unfinished work.
6. Finish a session, check history, and verify synchronization on paired physical devices.

Physical-device checks are required to validate round-screen clipping and tap comfort; build and lint cannot establish either.
