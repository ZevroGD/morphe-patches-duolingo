package app.template.patches.duolingo

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode

/**
 * Hides the two daily quests that require a video call, so removing video calls does not leave quests that
 * can never be completed.
 *
 * Quests arrive from the server as `GoalsGoalSchema` entries inside a `GoalsSchemaResponse`, which every quest
 * consumer reads. Right after kotlinx.serialization decodes the response, this removes the entries whose goal id
 * is `video_call_core_daily_quest` or `video_call_hard_daily_quest`.
 *
 * What it leaves untouched: every other quest, the quest UI, and progress data. If the server counts the hidden
 * quests towards an "all quests complete" reward, that reward may not be reachable; that cannot be fixed here.
 */
val duolingoHideVideoCallQuestsPatch = bytecodePatch(
    name = "Hide video call quests",
    description = "Hides the daily quests that require a video call.",
) {
    compatibleWith(duolingoCompatibility)

    execute {
        // The decoding constructor ends in two `return-void`s. All five registers v0 and p1..p4 are unused at
        // both, and `a` (the goals) has been assigned on both paths. Rebuild the goals vector without the two
        // video call goals and store it back. Insert from the last return so earlier indices stay valid.
        val method = GoalsSchemaResponseDecodingConstructorFingerprint.method

        method.findInstructionIndicesReversedOrThrow(opcode(Opcode.RETURN_VOID)).forEach { returnIndex ->
            method.addInstructionsWithLabels(
                returnIndex,
                """
                    iget-object p2, p0, $GOALS_SCHEMA_RESPONSE->a:$PVECTOR
                    invoke-interface {p2}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;
                    move-result-object v0
                    :loop
                    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
                    move-result p1
                    if-eqz p1, :done
                    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                    move-result-object p1
                    check-cast p1, $GOALS_GOAL_SCHEMA
                    iget-object p3, p1, $GOALS_GOAL_SCHEMA->b:Ljava/lang/String;
                    const-string p4, "$VIDEO_CALL_CORE_QUEST_ID"
                    invoke-virtual {p4, p3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result p4
                    if-nez p4, :remove
                    const-string p4, "$VIDEO_CALL_HARD_QUEST_ID"
                    invoke-virtual {p4, p3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result p4
                    if-eqz p4, :loop
                    :remove
                    invoke-interface {p2, p1}, $PVECTOR->minus(Ljava/lang/Object;)$PERSISTENT_LIST_BACKED_VECTOR
                    move-result-object p2
                    goto :loop
                    :done
                    iput-object p2, p0, $GOALS_SCHEMA_RESPONSE->a:$PVECTOR
                """.trimIndent(),
            )
        }
    }
}
