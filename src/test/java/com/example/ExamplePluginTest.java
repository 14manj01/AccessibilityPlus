package com.example;

import com.accessibilityplus.AccessibilityPlusPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Test;

public class ExamplePluginTest
{
	@Test
	public void runClient() throws Exception
	{
		ExternalPluginManager.loadBuiltin(AccessibilityPlusPlugin.class);
		RuneLite.main(new String[]{});

		Thread.currentThread().join();
	}
}
