package com.efficientmortimer;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MortimerOffersTest
{
	private static final int[][] VARBITS = {
		{VarbitID.SLAYER_CHOOSE_TASK_1, VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_ID,
			VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_VALUE, VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_NEGATIVE},
		{VarbitID.SLAYER_CHOOSE_TASK_2, VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_ID,
			VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_VALUE, VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_NEGATIVE},
		{VarbitID.SLAYER_CHOOSE_TASK_3, VarbitID.SLAYER_CHOOSE_TASK_3_MODIFIER_ID,
			VarbitID.SLAYER_CHOOSE_TASK_3_MODIFIER_VALUE, VarbitID.SLAYER_CHOOSE_TASK_3_MODIFIER_NEGATIVE}
	};

	private final Client client = mock(Client.class);
	private final Widget content = mock(Widget.class);
	private final Map<Integer, Integer> variables = new HashMap<>();
	private final Map<Integer, String> taskNames = new HashMap<>();
	private final MortimerOffers reader = new MortimerOffers(client);
	private Widget[] children;

	@Before
	public void setUp()
	{
		variables.put(VarbitID.SLAYER_CHOOSE_ROLLED_MASTER, 10);
		when(client.getVarbitValue(anyInt())).thenAnswer(call -> variables.getOrDefault(call.getArgument(0), 0));
		when(client.getWidget(InterfaceID.SlayerTaskChoice.CONTENT)).thenReturn(content);
		when(content.getBounds()).thenReturn(new Rectangle(50, 100, 450, 300));
		when(content.getDynamicChildren()).thenAnswer(call -> children);
		when(content.getChild(anyInt())).thenAnswer(call -> children[(int) call.getArgument(0)]);
		when(client.getDBRowsByValue(eq(DBTableID.SlayerTask.ID), eq(DBTableID.SlayerTask.COL_ID), eq(0), anyInt()))
			.thenAnswer(call -> taskNames.containsKey(call.getArgument(3))
				? Collections.singletonList(call.getArgument(3)) : Collections.emptyList());
		when(client.getDBTableField(anyInt(), eq(DBTableID.SlayerTask.COL_NAME_UPPERCASE), eq(0)))
			.thenAnswer(call -> new Object[]{taskNames.get(call.getArgument(0))});
		children = new Widget[30];
		for (int i = 0; i < children.length; i++)
		{
			children[i] = widget(WidgetType.GRAPHIC, "", new Rectangle());
		}
		offer(0, 11, "Abyssal Demons", 1, 15, 0, "+15 Slayer points");
		offer(1, 22, "Gargoyles", 2, 20, 1, "-20 Assigned");
		offer(2, 33, "Hydras", 5, 30, 0, "+30% Slayer XP");
	}

	@Test
	public void readsOrderedTaskAndModifierKeys()
	{
		MortimerOffers.Snapshot snapshot = reader.read();
		assertTrue(snapshot.isComplete());
		assertEquals(Arrays.asList("abyssal-demon/points", "gargoyle/quantity", "hydra/xp"), snapshot.getKeys());
		assertEquals(new Rectangle(50, 200, 450, 100), reader.getBounds(snapshot, 1));
	}

	@Test
	public void readsClueAndUniqueModifiers()
	{
		offer(0, 11, "Abyssal Demons", 3, 30, 0, "+30% Clue chance");
		offer(1, 22, "Gargoyles", 4, 5, 0, "+5% Superior unique chance");
		assertEquals(Arrays.asList("abyssal-demon/clue", "gargoyle/unique", "hydra/xp"), reader.read().getKeys());
	}

	@Test
	public void supportsExplicitlyLockedThirdChoice()
	{
		lockThirdChoice("Complete 50 tasks with Mortimer to unlock a third choice.");
		MortimerOffers.Snapshot snapshot = reader.read();
		assertTrue(snapshot.isComplete());
		assertEquals(Arrays.asList("abyssal-demon/points", "gargoyle/quantity"), snapshot.getKeys());
		assertNull(reader.getBounds(snapshot, 2));
	}

	@Test
	public void supportsPartnerLockedThirdChoice()
	{
		lockThirdChoice("You and your Slayer partner must complete 50 tasks with Mortimer to unlock a third choice.");
		assertTrue(reader.read().isComplete());
	}

	@Test
	public void missingThirdOfferWithoutLockIsIncomplete()
	{
		lockThirdChoice("");
		assertFalse(reader.read().isComplete());
		assertTrue(reader.read().getKeys().isEmpty());
	}

	@Test
	public void unknownTaskPreventsPartialRecommendation()
	{
		taskNames.put(33, "Unknown Creature");
		assertFalse(reader.read().isComplete());
		assertTrue(reader.read().getKeys().isEmpty());
	}

	@Test
	public void unavailableTaskDatabasePreventsPartialRecommendation()
	{
		when(client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, 33)).thenReturn(null);
		assertFalse(reader.read().isComplete());
	}

	@Test
	public void unavailableTaskNamePreventsPartialRecommendation()
	{
		when(client.getDBTableField(33, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(null);
		assertFalse(reader.read().isComplete());
	}

	@Test
	public void noModifierPreventsPartialRecommendation()
	{
		variables.put(VARBITS[2][1], 0);
		assertFalse(reader.read().isComplete());
	}

	@Test
	public void waitsForWidgetTextToMatchUpdatedVariables()
	{
		MortimerOffers.Snapshot previous = reader.read();
		variables.put(VARBITS[0][1], 5);
		assertNull(reader.getBounds(previous, 0));
		assertFalse(reader.isCurrent(previous));
		assertFalse(reader.read().isComplete());
		offer(0, 11, "Abyssal Demons", 5, 15, 0, "+15% Slayer XP");
		assertTrue(reader.read().isComplete());
	}

	@Test
	public void modifierAmountChangeInvalidatesPreviousSnapshot()
	{
		MortimerOffers.Snapshot previous = reader.read();
		variables.put(VARBITS[0][2], 25);
		assertNull(reader.getBounds(previous, 0));
		assertFalse(reader.read().isComplete());
	}

	@Test
	public void changedCardInstanceInvalidatesPreviousSnapshot()
	{
		MortimerOffers.Snapshot previous = reader.read();
		children[0] = widget(WidgetType.RECTANGLE, "", new Rectangle(50, 100, 450, 100));
		assertNull(reader.getBounds(previous, 0));
	}

	@Test
	public void changedVisibleTitleInvalidatesPreviousSnapshot()
	{
		MortimerOffers.Snapshot previous = reader.read();
		when(children[1].getText()).thenReturn("Hydras");
		assertFalse(reader.isCurrent(previous));
		assertFalse(reader.read().isComplete());
	}

	@Test
	public void boundsFollowCurrentLayout()
	{
		MortimerOffers.Snapshot snapshot = reader.read();
		Rectangle moved = new Rectangle(150, 200, 450, 100);
		when(children[0].getBounds()).thenReturn(moved);
		assertEquals(moved, reader.getBounds(snapshot, 0));
		assertNotSame(moved, reader.getBounds(snapshot, 0));
	}

	@Test
	public void closingInterfaceClearsAllBounds()
	{
		MortimerOffers.Snapshot snapshot = reader.read();
		when(content.isHidden()).thenReturn(true);
		assertNull(reader.read());
		assertNull(reader.getBounds(snapshot, 0));
		assertNull(reader.getContentBounds(snapshot));
	}

	@Test
	public void ignoresOffersFromAnotherMaster()
	{
		variables.put(VarbitID.SLAYER_CHOOSE_ROLLED_MASTER, 9);
		assertNull(reader.read());
	}

	private void offer(int slot, int taskId, String taskName, int modifier, int value, int negative, String modifierText)
	{
		variables.put(VARBITS[slot][0], taskId);
		variables.put(VARBITS[slot][1], modifier);
		variables.put(VARBITS[slot][2], value);
		variables.put(VARBITS[slot][3], negative);
		taskNames.put(taskId, taskName);
		int child = slot * 10;
		Rectangle bounds = new Rectangle(50, 100 + slot * 100, 450, 100);
		children[child] = widget(WidgetType.RECTANGLE, "", bounds);
		children[child + 1] = widget(WidgetType.TEXT, "<u=ff981f>" + taskName, bounds);
		children[child + 2] = widget(WidgetType.TEXT, "Amount: 80 to 120", bounds);
		children[child + 6] = widget(WidgetType.TEXT, modifierText, bounds);
	}

	private void lockThirdChoice(String message)
	{
		variables.put(VARBITS[2][0], 0);
		children = Arrays.copyOf(children, 23);
		children[21] = widget(WidgetType.TEXT, "<col=b2b2b2>" + message, new Rectangle());
	}

	private static Widget widget(int type, String text, Rectangle bounds)
	{
		Widget widget = mock(Widget.class);
		when(widget.getType()).thenReturn(type);
		when(widget.getText()).thenReturn(text);
		when(widget.getBounds()).thenReturn(bounds);
		return widget;
	}
}
