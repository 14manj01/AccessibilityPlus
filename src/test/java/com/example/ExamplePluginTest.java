package com.example;

import com.sandcrabcombatnotifier.SandCrabCombatNotifierPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Test;

public class ExamplePluginTest
{
	@Test
	public void runClient() throws Exception
	{
		ExternalPluginManager.loadBuiltin(SandCrabCombatNotifierPlugin.class);
		RuneLite.main(new String[]{});

		// Keep the JVM alive so the client stays open when run under Gradle test
		Thread.currentThread().join();
	}
}
