package app.template.patches.duolingo

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * Removes Leagues, and with them the Diamond Tournament, by making Duolingo treat every user as one who turned
 * leaderboards off in their privacy settings.
 *
 * `a2m.m(User)` is Duolingo's own "leaderboards are enabled for this user" check. It already returns false for
 * users with `PrivacySetting.DISABLE_LEADERBOARDS`, and every one of its callers already handles that case:
 * the Leagues tab (hidden), the `leaderboard` deep link, the Leagues home message, the profile league info and
 * the Leagues session end screens. Tournament screens are only built inside the Leagues tab, so they go with it.
 *
 * Two small additions close the paths that bypass that check:
 * - Opening the Leagues tab by other means (an old notification, a saved tab) is redirected to the Learn tab.
 * - The session end result object never carries a Leagues screen.
 *
 * What it leaves untouched: the server side privacy setting, quests, streaks, and every other home tab.
 * Leagues notifications are handled by [duolingoBlockLeaguesNotificationsPatch].
 */
val duolingoRemoveLeaguesPatch = bytecodePatch(
    name = "Remove leagues",
    description = "Removes Leagues and the Diamond Tournament: the tab, deep link, session end screens and profile info.",
) {
    compatibleWith(duolingoCompatibility)

    execute {
        // a2m.m(User): Boolean. Always report leaderboards as disabled.
        LeaderboardsEnabledFingerprint.method.returnEarly(false)

        // ehj.c(TabId): never select the Leagues tab.
        LeaguesTabRouterSetFingerprint.method.addInstructionsWithLabels(0, tabRedirectSmali("p1", "LEAGUES"))

        // ehj.b(SavedStateHandle): never restore the Leagues tab. Insert right after TabId.valueOf's move-result.
        val restore = LeaguesTabRouterRestoreFingerprint.instructionMatches[1].index
        LeaguesTabRouterRestoreFingerprint.method.addInstructionsWithLabels(restore + 1, tabRedirectSmali("p0", "LEAGUES"))

        // r700.<init>(leaguesScreen, rampUpScreens, familyPlanPromo, videoCallAfterOtherSession): p1 is the
        // Leagues screen, which is nullable.
        SessionEndScreensConstructorFingerprint.method.addInstructions(0, "const/4 p1, 0x0")
    }
}

/**
 * Drops push notifications about Leagues and the tournament.
 *
 * `bwr.A` shows a received push. It reads the notification's [NOTIFICATION_TYPE] into `v12`; for the four types
 * below it returns before anything is shown. Notifications already delivered before the patch still open the
 * Leagues tab, which is then redirected to Learn by [duolingoRemoveLeaguesPatch].
 */
val duolingoBlockLeaguesNotificationsPatch = bytecodePatch(
    name = "Block leagues notifications",
    description = "Stops Duolingo from showing Leagues, leaderboard and tournament push notifications.",
) {
    compatibleWith(duolingoCompatibility)

    execute {
        val typeMatch = ShowPushNotificationFingerprint.instructionMatches[1]
        val typeRegister = typeMatch.getInstruction<OneRegisterInstruction>().registerA

        // v13 is unused at this point in 6.98.4, and both registers must be below 16 for if-eq / if-ne.
        require(typeRegister == 12) { "Unexpected register layout in the push notification handler: v$typeRegister" }

        ShowPushNotificationFingerprint.method.addInstructionsWithLabels(
            typeMatch.index + 1,
            """
                sget-object v13, $NOTIFICATION_TYPE->LEADERBOARDS:$NOTIFICATION_TYPE
                if-eq v12, v13, :drop
                sget-object v13, $NOTIFICATION_TYPE->LEAGUES:$NOTIFICATION_TYPE
                if-eq v12, v13, :drop
                sget-object v13, $NOTIFICATION_TYPE->TOURNAMENT:$NOTIFICATION_TYPE
                if-eq v12, v13, :drop
                sget-object v13, $NOTIFICATION_TYPE->LEADERBOARDS_FRIEND_NUDGE:$NOTIFICATION_TYPE
                if-ne v12, v13, :keep
                :drop
                return-void
                :keep
                nop
            """.trimIndent(),
        )
    }
}
