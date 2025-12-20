package com.sandcrabcombatnotifier;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("sandcrabcombatnotifier")
public interface SandCrabCombatNotifierConfig extends Config
{
    @ConfigItem(
            keyName = "onlyWhenSandCrabsNearby",
            name = "Only when sand crabs nearby",
            description = "Only run and show overlay when a Sand Crab or Sandy rock is nearby."
    )
    default boolean onlyWhenSandCrabsNearby()
    {
        return true;
    }

    @ConfigItem(
            keyName = "outOfCombatAfterTicks",
            name = "Out of combat after (ticks)",
            description = "Notify after this many ticks without combat activity. One tick is 0.6 seconds."
    )
    default int outOfCombatAfterTicks()
    {
        return 8;
    }

    @ConfigItem(
            keyName = "enableAggroWarning",
            name = "Enable aggro warning",
            description = "Send a notification when approaching the crab aggro timeout."
    )
    default boolean enableAggroWarning()
    {
        return true;
    }

    @ConfigItem(
            keyName = "aggroWarnMinutes",
            name = "Aggro warning (minutes)",
            description = "Warn this many minutes after aggro starts."
    )
    default int aggroWarnMinutes()
    {
        return 9;
    }

    @ConfigItem(
            keyName = "showProgressBar",
            name = "Show progress bar",
            description = "Show a small progress bar for remaining aggro time."
    )
    default boolean showProgressBar()
    {
        return true;
    }

    @ConfigItem(
            keyName = "flashOnExpire",
            name = "Flash on expire",
            description = "Briefly flash the overlay when aggro reaches 0."
    )
    default boolean flashOnExpire()
    {
        return true;
    }

    @ConfigItem(
            keyName = "persistAggroTimer",
            name = "Persist aggro timer",
            description = "Attempt to keep the aggro timer across quick logout or world hop using wall clock time."
    )
    default boolean persistAggroTimer()
    {
        return true;
    }

    @ConfigItem(
            keyName = "resetTimerWhenLeavingCrabArea",
            name = "Reset timer when leaving crab area",
            description = "Resets the aggro timer when no sand crabs are detected nearby."
    )
    default boolean resetTimerWhenLeavingCrabArea()
    {
        return true;
    }

    @ConfigItem(
            keyName = "resetDistanceTiles",
            name = "Reset distance (tiles)",
            description = "If you move this many tiles away from where the aggro timer started, reset the timer."
    )
    default int resetDistanceTiles()
    {
        return 12;
    }
}
