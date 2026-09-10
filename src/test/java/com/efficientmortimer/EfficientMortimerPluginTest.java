package com.efficientmortimer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class EfficientMortimerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(EfficientMortimerPlugin.class);
		RuneLite.main(args);
	}
}
