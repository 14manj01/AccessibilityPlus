package com.example;

import com.sandcrabcombatnotifier.SandCrabCombatNotifierPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DevLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(SandCrabCombatNotifierPlugin.class);
        RuneLite.main(args);
    }
}
