package org.solarframework.discord.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Everything here runs without a gateway connection: the bot is embedded in hosts that configure almost none of it, and none of those paths may throw. */
class BotBuilderTest {

    private BotBuilder aBot() {
        return new BotBuilder("not-a-real-token", "org.solarframework.discord.interaction");
    }

    @Test
    void aBotWithNoGuildAndNoChannelsBindsWithoutThrowing() {
        assertDoesNotThrow(() -> aBot().bindReadyTargets());
    }
    @Test
    void bindingLeavesEveryUnconfiguredTargetNull() {
        aBot().bindReadyTargets();
        assertNull(BotBuilder.BotGuild);
        assertNull(BotBuilder.TemporaryFilesChannel);
        assertNull(BotBuilder.LogChannel);
    }
    @Test
    void theAfterReadyActionRunsWhenOneWasGiven() {
        boolean[] ran = {false};
        BotBuilder b = aBot();
        b.setAfterReadyAction(() -> { ran[0] = true; return null; });
        b.bindReadyTargets();
        assertTrue(ran[0]);
    }


    @Test
    void extraListenersAccumulateBeforeTheBotIsBuilt() {
        BotBuilder b = aBot();
        Object first = new Object(), second = new Object();
        b.addEventListeners(first);
        b.addEventListeners(second);
        assertEquals(List.of(first, second), b.extraListeners);
    }


    @Test
    void shuttingDownABotThatNeverConnectedIsHarmless() {
        assertDoesNotThrow(BotBuilder::shutdown);
        assertNull(BotBuilder.DiscordAccount);
    }
}
