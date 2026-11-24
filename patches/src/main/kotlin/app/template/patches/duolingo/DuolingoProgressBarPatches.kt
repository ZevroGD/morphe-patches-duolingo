package app.template.patches.duolingo

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Makes the lesson progress bar fill in proportion to completed lesson steps.
 *
 * Duolingo feeds the bar `completed / total` through `zkv.a`, which bends every value strictly between 0 and
 * 1 along `log4(1 + 3p)` (or a variant of it, depending on the ProgressCurveIterations experiment). That curve is
 * why the bar rushes ahead early and crawls at the end, independent of how the bar is animated.
 *
 * What it leaves untouched: the animation (see [duolingoConstantSpeedProgressPatch]), the segmented and music
 * lesson bars (they never pass through the curve), and all progress bars outside lessons.
 */
val duolingoLinearProgressMappingPatch = bytecodePatch(
    name = "Linear lesson progress",
    description = "Removes the experiment curve that makes the lesson progress bar fill fast at first and slowly at the end.",
) {
    compatibleWith(duolingoCompatibility)

    execute {
        // zkv.a(float, TreatmentRecord): float. Return the input unchanged.
        ProgressCurveFingerprint.method.addInstructions(0, "return p0")
    }
}

/**
 * Makes the lesson progress bar move at one constant speed, with no easing, overshoot or start delay.
 *
 * `tlv.e` animates every progress bar with a fixed 400 ms duration, an `OvershootInterpolator` and a start
 * delay. For the lesson bar widgets only (`LessonProgressBarAndroidView`, the pieces of the segmented bar and
 * the music bar), this patch instead:
 * - sets the duration to `|clamp(to / goal) - clamp(from / goal)| * fullBarMs`, so every update moves at the same
 *   speed regardless of how far it goes (segmented pieces are scaled by their width relative to the whole bar),
 * - removes the start delay, and
 * - uses a `LinearInterpolator`.
 *
 * Degenerate input is handled: a goal of zero or less keeps the original 400 ms, a zero distance gives a zero
 * duration (an instant update), and the duration never exceeds [fullBarMs] (or the optional step cap).
 *
 * What it leaves untouched: every other progress bar that goes through `tlv.e`, including quest bars.
 */
val duolingoConstantSpeedProgressPatch = bytecodePatch(
    name = "Constant speed lesson progress",
    description = "Animates the lesson progress bar at a constant speed, with no easing, overshoot or start delay.",
) {
    compatibleWith(duolingoCompatibility)

    val fullBarMs by intOption(
        key = "fullBarMs",
        default = 5000,
        title = "Full bar duration (ms)",
        description = "How long the bar takes to travel its whole length. Smaller steps take proportionally less time.",
        validator = { it != null && it in 100..120_000 },
    )

    val maxStepMs by intOption(
        key = "maxStepMs",
        default = 0,
        title = "Longest single update (ms)",
        description = "Caps the duration of one update, for example when resuming a lesson part way through. 0 disables the cap.",
        validator = { it != null && it in 0..120_000 },
    )

    execute {
        val durationIndex = ProgressAnimatorFingerprint.instructionMatches[0].index
        val interpolatorIndex = ProgressAnimatorFingerprint.instructionMatches[2].index
        val method = ProgressAnimatorFingerprint.method

        // Insert the later block first so the earlier index stays valid.
        method.addInstructionsWithLabels(interpolatorIndex, constantSpeedInterpolatorSmali())
        method.addInstructionsWithLabels(
            durationIndex,
            constantSpeedDurationSmali(fullBarMs ?: 5000, maxStepMs ?: 0),
        )
    }
}

/**
 * Runs before `setDuration` in `tlv.e`, where `v2:v3` already holds the default 400 ms and `v0` holds the
 * `float[] {from, to}` of the animator. Leaves a flag in `v0`: 1 for a lesson bar, 0 for any other bar.
 *
 * Registers `v0`, `v4` to `v7`, `p2` and `p5` are unused at this point in 6.98.4 (checked with a liveness
 * analysis of the method), and every register used by a 4-bit instruction format is below 16.
 */
private fun constantSpeedDurationSmali(fullBarMs: Int, maxStepMs: Int): String {
    val capSmali = if (maxStepMs > 0) {
        """
            const v4, ${floatLiteral(maxStepMs.toFloat())}
            invoke-static {v5, v4}, Ljava/lang/Math;->min(FF)F
            move-result v5
        """.trimIndent()
    } else {
        ""
    }

    return """
        instance-of p2, p0, $LESSON_PROGRESS_BAR_ANDROID_VIEW
        if-nez p2, :lesson
        instance-of p2, p0, $SEGMENTED_PIECE_PROGRESS_BAR_VIEW
        if-nez p2, :piece
        instance-of p2, p0, $MUSIC_LESSON_PROGRESS_BAR_VIEW
        if-nez p2, :lesson
        const/4 v0, 0x0
        goto :end
        :piece
        const/4 v7, 0x1
        goto :calc
        :lesson
        const/4 v7, 0x0
        :calc
        invoke-virtual {p0}, Ltlv;->getGoal()F
        move-result p2
        const/4 p5, 0x0
        cmpl-float p5, p2, p5
        if-lez p5, :flag
        const/4 p5, 0x0
        aget v4, v0, p5
        const/4 p5, 0x1
        aget v5, v0, p5
        div-float/2addr v4, p2
        div-float/2addr v5, p2
        const/4 v6, 0x0
        const p5, 0x3f800000
        invoke-static {v4, v6}, Ljava/lang/Math;->max(FF)F
        move-result v4
        invoke-static {v4, p5}, Ljava/lang/Math;->min(FF)F
        move-result v4
        invoke-static {v5, v6}, Ljava/lang/Math;->max(FF)F
        move-result v5
        invoke-static {v5, p5}, Ljava/lang/Math;->min(FF)F
        move-result v5
        sub-float/2addr v5, v4
        invoke-static {v5}, Ljava/lang/Math;->abs(F)F
        move-result v5
        if-eqz v7, :scale
        invoke-virtual {p0}, Landroid/view/View;->getWidth()I
        move-result v6
        invoke-virtual {p0}, Landroid/view/View;->getParent()Landroid/view/ViewParent;
        move-result-object v4
        instance-of p2, v4, Landroid/view/View;
        if-eqz p2, :scale
        check-cast v4, Landroid/view/View;
        invoke-virtual {v4}, Landroid/view/View;->getWidth()I
        move-result v4
        if-lez v4, :scale
        if-lez v6, :scale
        int-to-float v6, v6
        int-to-float v4, v4
        div-float/2addr v6, v4
        mul-float/2addr v5, v6
        :scale
        const v4, ${floatLiteral(fullBarMs.toFloat())}
        mul-float/2addr v5, v4
$capSmali
        float-to-long v2, v5
        :flag
        const/4 v0, 0x1
        :end
        nop
    """.trimIndent()
}

/** Runs before `setInterpolator` in `tlv.e`: for lesson bars (`v0 == 1`) drops the start delay and goes linear. */
private fun constantSpeedInterpolatorSmali() = """
    if-eqz v0, :orig
    const-wide/16 v2, 0x0
    invoke-virtual {p1, v2, v3}, Landroid/animation/ValueAnimator;->setStartDelay(J)V
    new-instance p4, Landroid/view/animation/LinearInterpolator;
    invoke-direct {p4}, Landroid/view/animation/LinearInterpolator;-><init>()V
    :orig
    nop
""".trimIndent()

/**
 * Always uses Duolingo's regular Android lesson progress bar.
 *
 * Users in the AXL_ANDROID_PROGRESS_BAR_RIVE experiment get a Rive animation (`in_lesson_pb_012.riv`) whose
 * easing is baked into the animation file and cannot be changed by code. `LessonProgressBarView.e` is the only
 * reader of that experiment, and it only decides which of its two child bars is shown.
 */
val duolingoDisableRiveProgressBarPatch = bytecodePatch(
    name = "Disable Rive lesson progress bar",
    description = "Always shows the Android lesson progress bar instead of the experimental Rive animation.",
) {
    compatibleWith(duolingoCompatibility)

    execute {
        // The second match is the move-result of StandardCondition.isInExperiment(). Overwrite it with false.
        val moveResult = LessonProgressBarModeFingerprint.instructionMatches[1]
        val register = moveResult.getInstruction<OneRegisterInstruction>().registerA

        LessonProgressBarModeFingerprint.method.addInstructions(moveResult.index + 1, "const/16 v$register, 0x0")
    }
}
