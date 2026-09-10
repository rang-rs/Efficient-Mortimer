package com.efficientmortimer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TaskCatalog
{
	private static final Map<String, String> TASK_NAMES = Map.ofEntries(
		Map.entry("aberrant-spectre", "Aberrant Spectres"),
		Map.entry("abyssal-demon", "Abyssal Demons"),
		Map.entry("aquanite", "Aquanites"),
		Map.entry("araxyte", "Araxytes"),
		Map.entry("banshee", "Banshees"),
		Map.entry("basilisks", "Basilisks"),
		Map.entry("bloodveld", "Bloodveld"),
		Map.entry("cave-crawler", "Cave Crawlers"),
		Map.entry("cave-horror", "Cave Horrors"),
		Map.entry("cockatrice", "Cockatrice"),
		Map.entry("crawling-hand", "Crawling Hands"),
		Map.entry("custodian-stalker", "Custodian Stalkers"),
		Map.entry("dark-beast", "Dark Beasts"),
		Map.entry("drake", "Drakes"),
		Map.entry("dust-devil", "Dust Devils"),
		Map.entry("gargoyle", "Gargoyles"),
		Map.entry("gryphons", "Gryphons"),
		Map.entry("hydra", "Hydras"),
		Map.entry("infernal-mage", "Infernal Mages"),
		Map.entry("jelly", "Jellies"),
		Map.entry("kurask", "Kurask"),
		Map.entry("nechryael", "Nechryael"),
		Map.entry("pyrefiend", "Pyrefiends"),
		Map.entry("rockslug", "Rockslugs"),
		Map.entry("smoke-devil", "Smoke Devils"),
		Map.entry("turoth", "Turoth"),
		Map.entry("venator", "Venators"),
		Map.entry("warped-creatures", "Warped Creatures"),
		Map.entry("wyrm", "Wyrms")
	);
	private static final Set<String> MODIFIERS = Set.of("quantity", "points", "clue", "xp", "unique");
	private static final Map<String, String> IDS_BY_NAME = new HashMap<>();

	static
	{
		TASK_NAMES.forEach((id, name) -> IDS_BY_NAME.put(name.toLowerCase(Locale.ROOT), id));
	}

	private TaskCatalog()
	{
	}

	static String taskIdForName(String name)
	{
		return name == null ? null : IDS_BY_NAME.get(name.trim().toLowerCase(Locale.ROOT));
	}

	static boolean isValidKey(String key)
	{
		if (key == null)
		{
			return false;
		}
		int separator = key.indexOf('/');
		return separator > 0
			&& TASK_NAMES.containsKey(key.substring(0, separator))
			&& MODIFIERS.contains(key.substring(separator + 1));
	}

	static int getMaxRankingSize()
	{
		return TASK_NAMES.size() * MODIFIERS.size();
	}
}
