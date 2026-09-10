package com.efficientmortimer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaskCatalogTest
{
	@Test
	public void matchesDisplayedTaskNames()
	{
		assertEquals("abyssal-demon", TaskCatalog.taskIdForName("Abyssal Demons"));
		assertEquals("warped-creatures", TaskCatalog.taskIdForName("  warped creatures  "));
		assertEquals("bloodveld", TaskCatalog.taskIdForName("Bloodveld"));
		assertEquals("custodian-stalker", TaskCatalog.taskIdForName("Custodian Stalkers"));
		assertNull(TaskCatalog.taskIdForName("Unknown task"));
		assertNull(TaskCatalog.taskIdForName(null));
	}

	@Test
	public void validatesCanonicalTaskModifierPairs()
	{
		for (String modifier : new String[]{"quantity", "points", "clue", "xp", "unique"})
		{
			assertTrue(TaskCatalog.isValidKey("abyssal-demon/" + modifier));
		}
		assertFalse(TaskCatalog.isValidKey("abyssal-demon/none"));
		assertFalse(TaskCatalog.isValidKey("Abyssal Demons/xp"));
		assertFalse(TaskCatalog.isValidKey("abyssal-demon/xp/unique"));
		assertFalse(TaskCatalog.isValidKey("abyssal-demon/"));
		assertFalse(TaskCatalog.isValidKey(null));
	}
}
