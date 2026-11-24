package app.template.patches.duolingo

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

// region Lesson progress bar

/** `zkv.a(float, TreatmentRecord)`: maps lesson progress through the ProgressCurveIterations experiment. */
internal object ProgressCurveFingerprint : Fingerprint(
    isStatic = true,
    returnType = "F",
    parameters = listOf("F", "Lcom/duolingo/core/experiments/ExperimentsRepository\$TreatmentRecord;"),
    filters = listOf(
        checkCast("Lcom/duolingo/core/experiments/ProgressCurveIterationsCondition;"),
    ),
)

/**
 * `tlv.e(...)`: builds the ValueAnimator that moves every JuicyProgressBarView.
 * The three matches are, in order, `setDuration`, `setStartDelay` and `setInterpolator`.
 */
internal object ProgressAnimatorFingerprint : Fingerprint(
    definingClass = "Ltlv;",
    isStatic = true,
    returnType = "Landroid/animation/ValueAnimator;",
    parameters = listOf(
        "Ltlv;",
        "F",
        "F",
        "Lkotlin/jvm/functions/Function0;",
        "Landroid/view/animation/LinearInterpolator;",
        "I",
    ),
    filters = listOf(
        methodCall(definingClass = "Landroid/animation/ValueAnimator;", name = "setDuration"),
        methodCall(definingClass = "Landroid/animation/ValueAnimator;", name = "setStartDelay"),
        methodCall(definingClass = "Landroid/animation/ValueAnimator;", name = "setInterpolator"),
    ),
)

/**
 * `LessonProgressBarView.e(onm)`: picks the Rive bar or the Android bar from an experiment.
 * The second match is the `move-result` of `StandardCondition.isInExperiment()`.
 */
internal object LessonProgressBarModeFingerprint : Fingerprint(
    definingClass = "Lcom/duolingo/data/session/progressbar/LessonProgressBarView;",
    returnType = "V",
    parameters = listOf("Lonm;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/duolingo/core/experiments/model/StandardCondition;",
            name = "isInExperiment",
        ),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
    ),
)

// endregion

// region Leagues

/** `a2m.m(User)`: Duolingo's own "leaderboards are enabled for this user" check. */
internal object LeaderboardsEnabledFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf(USER),
    filters = listOf(
        fieldAccess(
            definingClass = "Lcom/duolingo/data/settings/PrivacySetting;",
            name = "DISABLE_LEADERBOARDS",
            opcode = Opcode.SGET_OBJECT,
        ),
    ),
)

/** `ehj.c(TabId)`: stores the selected home tab. Separate objects so each patch matches independently. */
internal object LeaguesTabRouterSetFingerprint : Fingerprint(
    definingClass = "Lehj;",
    name = "c",
    returnType = "V",
    parameters = listOf(TAB_ID),
)

internal object VideoCallTabRouterSetFingerprint : Fingerprint(
    definingClass = "Lehj;",
    name = "c",
    returnType = "V",
    parameters = listOf(TAB_ID),
)

/**
 * `ehj.b(SavedStateHandle)`: restores the selected tab. The second match is the `move-result-object`
 * of `TabId.valueOf(String)`.
 */
internal object LeaguesTabRouterRestoreFingerprint : Fingerprint(
    definingClass = "Lehj;",
    name = "b",
    isStatic = true,
    returnType = TAB_ID,
    filters = listOf(
        methodCall(definingClass = TAB_ID, name = "valueOf"),
        opcode(Opcode.MOVE_RESULT_OBJECT, InstructionLocation.MatchAfterImmediately()),
    ),
)

internal object VideoCallTabRouterRestoreFingerprint : Fingerprint(
    definingClass = "Lehj;",
    name = "b",
    isStatic = true,
    returnType = TAB_ID,
    filters = listOf(
        methodCall(definingClass = TAB_ID, name = "valueOf"),
        opcode(Opcode.MOVE_RESULT_OBJECT, InstructionLocation.MatchAfterImmediately()),
    ),
)

private object SessionEndScreensToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    strings = listOf("SessionEndScreens(leaguesScreenType="),
)

/**
 * `r700.<init>(leaguesScreen, rampUpScreens, familyPlanPromo, videoCallAfterOtherSession)`.
 * Both the Leagues and the video call patch null one of its arguments at index 0.
 */
internal object SessionEndScreensConstructorFingerprint : Fingerprint(
    classFingerprint = SessionEndScreensToStringFingerprint,
    name = "<init>",
)

/**
 * `bwr.A(...)`: shows a received push notification. The second match is the `move-result-object` of the
 * call that reads the notification's [NOTIFICATION_TYPE].
 */
internal object ShowPushNotificationFingerprint : Fingerprint(
    definingClass = "Lbwr;",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Z",
        "L",
        "Ljava/util/Map;",
        "Z",
        "Ljava/util/LinkedHashMap;",
        "L",
    ),
    filters = listOf(
        methodCall(returnType = NOTIFICATION_TYPE),
        opcode(Opcode.MOVE_RESULT_OBJECT, InstructionLocation.MatchAfterImmediately()),
    ),
)

// endregion

// region Video calls

/**
 * `vq50.invokeSuspend`: builds the bottom navigation tab list. The video call case of its tab switch is
 * the single `move v1, v9` that copies the `videoCallTabAvailability` flag.
 */
internal object NavigationTabsFingerprint : Fingerprint(
    definingClass = "Lvq50;",
    name = "invokeSuspend",
    filters = listOf(
        methodCall(definingClass = TAB_ID, name = "getEntries"),
        InstructionFilter { _, instruction ->
            instruction.opcode == Opcode.MOVE &&
                (instruction as TwoRegisterInstruction).let { it.registerA == 1 && it.registerB == 9 }
        },
    ),
)

/**
 * `SessionEndViewModel.F0`: assembles the session end screens. The first match is the read of a boolean
 * `User` field and the second the `LILY_RIVE_ANIMATION` video call promo it guards.
 */
internal object SessionEndVideoCallPromoFingerprint : Fingerprint(
    definingClass = "Lcom/duolingo/sessionend/k;",
    name = "F0",
    filters = listOf(
        fieldAccess(definingClass = USER, opcode = Opcode.IGET_BOOLEAN),
        fieldAccess(
            definingClass = "Lcom/duolingo/ai/videocall/promo/VideoCallPromoScreen;",
            name = "LILY_RIVE_ANIMATION",
            opcode = Opcode.SGET_OBJECT,
            location = InstructionLocation.MatchAfterWithin(6),
        ),
    ),
)

/**
 * `o0y.b(...)`: filters the app open rotating subscription promos. Its second boolean parameter lets
 * promos from video call hooks through; the filter that drops them calls `PlusContext.isFromVCHook()`.
 */
internal object RotatingPromoFilterFingerprint : Fingerprint(
    definingClass = "Lo0y;",
    name = "b",
    isStatic = true,
    returnType = "Ljava/util/List;",
    filters = listOf(
        methodCall(definingClass = "Lcom/duolingo/data/plus/promotions/PlusContext;", name = "isFromVCHook"),
    ),
)

/** `rru.invoke`: derives the Practice Hub header from the server's featured session type. */
internal object PracticeHubHeaderFingerprint : Fingerprint(
    definingClass = "Lrru;",
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(
            definingClass = PRACTICE_HUB_SESSION_TYPE,
            name = "VIDEO_CALL_PRACTICE",
            opcode = Opcode.SGET_OBJECT,
        ),
    ),
)

/**
 * `o25.apply`: picks the featured Practice Hub session. The first match is the `if-eqz` of the flag that
 * forces `VIDEO_CALL_PRACTICE`; the last match is the `if-nez` that tests the server named session.
 */
internal object PracticeHubFeaturedSessionFingerprint : Fingerprint(
    definingClass = "Lo25;",
    name = "apply",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        opcode(Opcode.IF_EQZ),
        fieldAccess(
            definingClass = PRACTICE_HUB_SESSION_TYPE,
            name = "VIDEO_CALL_PRACTICE",
            opcode = Opcode.SGET_OBJECT,
            location = InstructionLocation.MatchAfterImmediately(),
        ),
        methodCall(definingClass = PRACTICE_HUB_SESSION_TYPE, name = "getEntries"),
        checkCast(PRACTICE_HUB_SESSION_TYPE),
        opcode(Opcode.IF_NEZ, InstructionLocation.MatchAfterImmediately()),
    ),
)

/**
 * `rju.a`: picks the showcase of the Max purchase carousel. The second match is the `if-eqz` of the flag
 * that selects the video call showcase.
 */
internal object PurchaseCarouselShowcaseFingerprint : Fingerprint(
    definingClass = "Lrju;",
    name = "a",
    filters = listOf(
        opcode(Opcode.IF_EQZ),
        opcode(Opcode.IF_EQZ, InstructionLocation.MatchAfterImmediately()),
        fieldAccess(
            definingClass = "Lcom/duolingo/plus/purchaseflow/scrollingcarousel/PlusScrollingCarouselUiConverter\$ShowCase;",
            name = "MAX_VIDEO_CALL",
            opcode = Opcode.SGET_OBJECT,
            location = InstructionLocation.MatchAfterImmediately(),
        ),
    ),
)

/**
 * `twv.<clinit>`: the static Plus checklists. The first match is the `UNLIMITED_VIDEO_CALL` element and
 * the last the `filled-new-array` that builds the list containing it.
 */
internal object PlusChecklistFingerprint : Fingerprint(
    definingClass = "Ltwv;",
    name = "<clinit>",
    filters = listOf(
        fieldAccess(definingClass = PLUS_CHECKLIST_ELEMENT, name = "UNLIMITED_VIDEO_CALL", opcode = Opcode.SGET_OBJECT),
        fieldAccess(
            definingClass = PLUS_CHECKLIST_ELEMENT,
            name = "FREE_CHALLENGE_ENTRY",
            opcode = Opcode.SGET_OBJECT,
            location = InstructionLocation.MatchAfterImmediately(),
        ),
        opcode(Opcode.FILLED_NEW_ARRAY, InstructionLocation.MatchAfterImmediately()),
    ),
)

/** `k9t.e(origin, xpBoost, character, animate)`: starts a video call activity. */
internal object StartVideoCallFingerprint : Fingerprint(
    definingClass = "Lk9t;",
    name = "e",
    returnType = "V",
    parameters = listOf(
        "Lcom/duolingo/data/video/call/VideoCallCallOrigin;",
        "Ljava/lang/Integer;",
        "Lcom/duolingo/data/video/call/VideoCallCharacter;",
        "Z",
    ),
)

private object GenericPathUnitToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    strings = listOf("GenericPathUnit(index="),
)

/** `ivh.<init>`: the path unit. The match is the `size()` call that derives a field from the level list. */
internal object PathUnitConstructorFingerprint : Fingerprint(
    classFingerprint = GenericPathUnitToStringFingerprint,
    name = "<init>",
    filters = listOf(
        methodCall(definingClass = "Ljava/util/List;", name = "size"),
    ),
)

// endregion

// region Quests

private object GoalsSchemaResponseToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    strings = listOf("GoalsSchemaResponse(goals="),
)

/** The constructor kotlinx.serialization uses to decode the server's goals schema. */
internal object GoalsSchemaResponseDecodingConstructorFingerprint : Fingerprint(
    classFingerprint = GoalsSchemaResponseToStringFingerprint,
    name = "<init>",
    parameters = listOf("I", PVECTOR, PVECTOR, PVECTOR),
)

// endregion
