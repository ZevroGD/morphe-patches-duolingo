package app.template.patches.duolingo

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/**
 * Removes video calls and their promotions: the bottom navigation tab, the session end promos, the app open
 * rotating promo, the Practice Hub entries, the Plus purchase carousel showcase and the Plus checklist item.
 *
 * Every surface is closed where Duolingo decides to show it, so nothing is left as a dead button. As a safety
 * net the single "start a video call" function does nothing.
 *
 * What it leaves untouched: quests and streaks, normal lessons, and the other tabs. Video call nodes on the
 * learning path are handled separately by [duolingoRemoveVideoCallPathNodesPatch], and the two daily quests that
 * require a video call by [duolingoHideVideoCallQuestsPatch].
 */
val duolingoRemoveVideoCallPatch = bytecodePatch(
    name = "Remove video calls",
    description = "Removes the video call tab and its promotions: session end, app open, Practice Hub and Plus screens.",
) {
    compatibleWith(duolingoCompatibility)

    execute {
        // region Bottom navigation tab

        // vq50.invokeSuspend builds the tab list. The video call tab is shown when v9 (the
        // videoCallTabAvailability flow's value) is true: its switch case is a single `move v1, v9`.
        // Overwrite the result with false, before the case jumps on.
        val tabCase = NavigationTabsFingerprint.instructionMatches[1]
        val tabCaseRegister = tabCase.getInstruction<TwoRegisterInstruction>().registerA
        NavigationTabsFingerprint.method.addInstructions(tabCase.index + 1, "const/16 v$tabCaseRegister, 0x0")

        // ehj.c(TabId) and ehj.b(SavedStateHandle): never select or restore the video call tab.
        VideoCallTabRouterSetFingerprint.method.addInstructionsWithLabels(0, tabRedirectSmali("p1", "VIDEO_CALL"))
        val restore = VideoCallTabRouterRestoreFingerprint.instructionMatches[1].index
        VideoCallTabRouterRestoreFingerprint.method.addInstructionsWithLabels(
            restore + 1,
            tabRedirectSmali("p0", "VIDEO_CALL"),
        )

        // endregion

        // region Session end

        // r700.<init>: p4 is the nullable "video call after another session" screen.
        SessionEndScreensConstructorFingerprint.method.addInstructions(0, "const/4 p4, 0x0")

        // SessionEndViewModel.F0 adds the Lily video call promo when a boolean User flag is set.
        // Overwrite the flag, between its read and its test, with false.
        val promoFlag = SessionEndVideoCallPromoFingerprint.instructionMatches[0]
        val promoFlagRegister = promoFlag.getInstruction<TwoRegisterInstruction>().registerA
        SessionEndVideoCallPromoFingerprint.method.addInstructions(promoFlag.index + 1, "const/16 v$promoFlagRegister, 0x0")

        // endregion

        // region App open rotating promo

        // o0y.b(Z, Z, ...): when its second parameter is false, Duolingo itself drops every rotating promo
        // whose PlusContext.isFromVCHook() is true, which is exactly the two video call promos.
        RotatingPromoFilterFingerprint.method.addInstructions(0, "const/4 p1, 0x0")

        // endregion

        // region Practice Hub

        // rru.invoke: the video call header, the Lily style and the video call card all derive from the
        // server's featured session type being VIDEO_CALL_PRACTICE. Treat that as "no featured session",
        // a case the code already handles. The inserted code runs just before the original sget of the
        // constant, whose destination register is therefore free to use here.
        val headerSget = PracticeHubHeaderFingerprint.instructionMatches[0]
        val scratch = headerSget.getInstruction<OneRegisterInstruction>().registerA
        val compare = PracticeHubHeaderFingerprint.method.implementation!!.instructions[headerSget.index + 1]
        val featured = (compare as TwoRegisterInstruction).registerA
        require(compare.registerB == scratch) { "Unexpected featured session comparison in the Practice Hub header" }

        PracticeHubHeaderFingerprint.method.addInstructionsWithLabels(
            headerSget.index,
            """
                sget-object v$scratch, $PRACTICE_HUB_SESSION_TYPE->VIDEO_CALL_PRACTICE:$PRACTICE_HUB_SESSION_TYPE
                if-ne v$featured, v$scratch, :keep
                const/16 v$featured, 0x0
                :keep
                nop
            """.trimIndent(),
        )

        // o25.apply picks the featured session in two ways: a flag that forces VIDEO_CALL_PRACTICE, and the
        // session type the server names. Disable the flag and null the server's choice if it is the video call.
        // The server's choice is tested by the last match's `if-nez`; v6 is unused there in 6.98.4.
        val flagTest = PracticeHubFeaturedSessionFingerprint.instructionMatches[0]
        val flagRegister = flagTest.getInstruction<OneRegisterInstruction>().registerA
        val serverChoiceTest = PracticeHubFeaturedSessionFingerprint.instructionMatches[4]
        val serverChoice = serverChoiceTest.getInstruction<OneRegisterInstruction>().registerA

        // Insert the later block first so the earlier index stays valid.
        PracticeHubFeaturedSessionFingerprint.method.addInstructionsWithLabels(
            serverChoiceTest.index,
            """
                sget-object v6, $PRACTICE_HUB_SESSION_TYPE->VIDEO_CALL_PRACTICE:$PRACTICE_HUB_SESSION_TYPE
                if-ne v$serverChoice, v6, :keep
                const/16 v$serverChoice, 0x0
                :keep
                nop
            """.trimIndent(),
        )
        PracticeHubFeaturedSessionFingerprint.method.addInstructions(flagTest.index, "const/16 v$flagRegister, 0x0")

        // endregion

        // region Plus purchase screens

        // rju.a: the Max purchase carousel shows the video call showcase when a flag is set. Clear the flag
        // just before its test; the ordinary Max showcase is used instead.
        val showcaseTest = PurchaseCarouselShowcaseFingerprint.instructionMatches[1]
        val showcaseRegister = showcaseTest.getInstruction<OneRegisterInstruction>().registerA
        PurchaseCarouselShowcaseFingerprint.method.addInstructions(showcaseTest.index, "const/16 v$showcaseRegister, 0x0")

        // twv.<clinit>: one of the static Plus checklists contains UNLIMITED_VIDEO_CALL. Rebuild its
        // filled-new-array without that element's register.
        val checklistElement = PlusChecklistFingerprint.instructionMatches[0]
        val checklistElementRegister = checklistElement.getInstruction<OneRegisterInstruction>().registerA
        val checklistArray = PlusChecklistFingerprint.instructionMatches[2]
        val array = checklistArray.getInstruction<FiveRegisterInstruction>()
        val keptRegisters = listOf(array.registerC, array.registerD, array.registerE, array.registerF, array.registerG)
            .take(array.registerCount)
            .filter { it != checklistElementRegister }
        require(keptRegisters.size == array.registerCount - 1) { "Unexpected Plus checklist layout" }
        val arrayType = ((array as ReferenceInstruction).reference as TypeReference).type

        PlusChecklistFingerprint.method.replaceInstruction(
            checklistArray.index,
            "filled-new-array {${keptRegisters.joinToString(", ") { "v$it" }}}, $arrayType",
        )

        // endregion

        // Safety net: k9t.e(origin, xpBoost, character, animate) is the one function that starts a video call.
        StartVideoCallFingerprint.method.returnEarly()
    }
}

/**
 * Experimental. Removes video call levels from the learning path by dropping them from each path unit's
 * level list as the unit is constructed.
 *
 * Everything derived from the list (the unit's level counts and layout cycle) is computed from the filtered
 * list, but the server's own progress data may still count those levels, so this could affect how units
 * complete or unlock. It is off by default: enable it, then check a course that has video call levels.
 */
val duolingoRemoveVideoCallPathNodesPatch = bytecodePatch(
    name = "Remove video call path nodes (experimental)",
    description = "Removes video call levels from the learning path. Untested against unit completion; off by default.",
    default = false,
) {
    compatibleWith(duolingoCompatibility)

    execute {
        // ivh.<init>(unitIndex, levels, ...): the match is the List.size() call that derives a field from p2.
        // At that point p1, p5, p6 and p7 are unused and p2 is the level list (a PVector). Remove every level
        // whose type is VIDEO_CALL, then store the filtered list back into the field.
        val sizeCall = PathUnitConstructorFingerprint.instructionMatches[0]

        PathUnitConstructorFingerprint.method.addInstructionsWithLabels(
            sizeCall.index,
            """
                invoke-interface {p2}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;
                move-result-object p5
                :loop
                invoke-interface {p5}, Ljava/util/Iterator;->hasNext()Z
                move-result p6
                if-eqz p6, :done
                invoke-interface {p5}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object p6
                check-cast p6, Lgvh;
                iget-object p7, p6, Lgvh;->l:$PATH_LEVEL_TYPE
                sget-object p1, $PATH_LEVEL_TYPE->VIDEO_CALL:$PATH_LEVEL_TYPE
                if-ne p7, p1, :loop
                invoke-interface {p2, p6}, $PVECTOR->minus(Ljava/lang/Object;)$PERSISTENT_LIST_BACKED_VECTOR
                move-result-object p2
                goto :loop
                :done
                iput-object p2, p0, Livh;->b:$PVECTOR
            """.trimIndent(),
        )
    }
}
