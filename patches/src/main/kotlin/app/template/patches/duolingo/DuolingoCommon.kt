package app.template.patches.duolingo

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * Every patch in this repository was written against, and verified on paper against the decompiled
 * source of, Duolingo 6.98.4 (2463). Most of Duolingo's own classes and members are obfuscated by
 * R8, so the fingerprints that use obfuscated names (for example `Ltlv;`) are only valid for this
 * exact version and will fail loudly on any other.
 */
internal val duolingoCompatibility = Compatibility(
    name = "Duolingo",
    packageName = "com.duolingo",
    targets = listOf(AppTarget(version = "6.98.4")),
)

// Class descriptors used in smali snippets and fingerprints. None of these are obfuscated.
internal const val TAB_ID = "Lcom/duolingo/data/home/TabId;"
internal const val NOTIFICATION_TYPE = "Lcom/duolingo/data/notifications/NotificationType;"
internal const val PRACTICE_HUB_SESSION_TYPE = "Lcom/duolingo/data/plus/practicehub/PracticeHubSessionType;"
internal const val PATH_LEVEL_TYPE = "Lcom/duolingo/data/path/common/PathLevelType;"
internal const val PVECTOR = "Lcom/duolingo/core/pcollections/migration/PVector;"
internal const val PERSISTENT_LIST_BACKED_VECTOR =
    "Lcom/duolingo/core/pcollections/migration/PersistentListBackedVector;"
internal const val GOALS_GOAL_SCHEMA = "Lcom/duolingo/data/goals/models/GoalsGoalSchema;"
internal const val GOALS_SCHEMA_RESPONSE = "Lcom/duolingo/data/goals/models/GoalsSchemaResponse;"
internal const val USER = "Lcom/duolingo/data/user/User;"
internal const val PLUS_CHECKLIST_ELEMENT = "Lcom/duolingo/data/plus/purchaseflow/checklist/PlusChecklistElement;"

// The three lesson progress bar widgets that animate through the shared `Ltlv;` base class.
internal const val LESSON_PROGRESS_BAR_ANDROID_VIEW =
    "Lcom/duolingo/data/session/progressbar/LessonProgressBarAndroidView;"
internal const val SEGMENTED_PIECE_PROGRESS_BAR_VIEW = "Lcom/duolingo/core/ui/SegmentedPieceProgressBarView;"
internal const val MUSIC_LESSON_PROGRESS_BAR_VIEW = "Lcom/duolingo/music/ui/MusicLessonProgressBarView;"

/** Goal ids of the two daily quests that require a video call. */
internal const val VIDEO_CALL_CORE_QUEST_ID = "video_call_core_daily_quest"
internal const val VIDEO_CALL_HARD_QUEST_ID = "video_call_hard_daily_quest"

/**
 * Smali that redirects a [TabId] held in [tabRegister] to `LEARN` when it equals [tabName].
 *
 * Uses `v0` as scratch, so it must only be inserted where `v0` is free. The block ends with a `nop`
 * because added smali may not end in a label.
 */
internal fun tabRedirectSmali(tabRegister: String, tabName: String) = """
    sget-object v0, $TAB_ID->$tabName:$TAB_ID
    if-ne $tabRegister, v0, :keep
    sget-object $tabRegister, $TAB_ID->LEARN:$TAB_ID
    :keep
    nop
""".trimIndent()

/** The smali literal of [value] as the raw bits of an IEEE-754 float, for use with `const`. */
internal fun floatLiteral(value: Float): String = "0x%08x".format(java.lang.Float.floatToRawIntBits(value))
