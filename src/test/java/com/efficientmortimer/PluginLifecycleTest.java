package com.efficientmortimer;

import com.google.gson.Gson;
import com.google.inject.Guice;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PluginLifecycleTest
{
	private static final String GROUP = EfficientMortimerConfig.GROUP;
	private static final String VALID = export(1);
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final ConfigManager configManager = mock(ConfigManager.class);
	private final EfficientMortimerConfig config = mock(EfficientMortimerConfig.class);
	private final OverlayManager overlayManager = mock(OverlayManager.class);
	private final EfficientMortimerPlugin plugin = new EfficientMortimerPlugin();
	private final Queue<Runnable> pending = new ArrayDeque<>();
	private final Map<Long, Map<String, String>> profiles = new HashMap<>();
	private final Map<Integer, Integer> variables = new HashMap<>();
	private long profileId = 1;

	@Before
	public void setUp()
	{
		ConfigProfile profile = mock(ConfigProfile.class);
		when(profile.getId()).thenAnswer(call -> profileId);
		when(configManager.getProfile()).thenReturn(profile);
		when(config.listMode()).thenReturn(ListMode.CUSTOM);
		when(config.customList()).thenAnswer(call -> settings().getOrDefault("customList", ""));
		doAnswer(call -> {
			pending.add(call.getArgument(0));
			return null;
		}).when(clientThread).invoke(any(Runnable.class));

		Guice.createInjector(binder -> {
			binder.bind(Client.class).toInstance(client);
			binder.bind(ClientThread.class).toInstance(clientThread);
			binder.bind(ConfigManager.class).toInstance(configManager);
			binder.bind(EfficientMortimerConfig.class).toInstance(config);
			binder.bind(Gson.class).toInstance(new Gson());
			binder.bind(OverlayManager.class).toInstance(overlayManager);
			binder.bind(EfficientMortimerPlugin.class).toInstance(plugin);
		});

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(anyInt())).thenAnswer(call -> variables.getOrDefault(call.getArgument(0), 0));
		variables.put(VarbitID.SLAYER_CHOOSE_ROLLED_MASTER, 10);
		variables.put(VarbitID.SLAYER_POINTS, 100);
		Widget content = mock(Widget.class);
		Widget[] children = new Widget[23];
		children[20] = widget(WidgetType.RECTANGLE, "");
		children[21] = widget(WidgetType.TEXT, "Complete 50 tasks with Mortimer to unlock a third choice.");
		when(client.getWidget(InterfaceID.SlayerTaskChoice.CONTENT)).thenReturn(content);
		when(content.getDynamicChildren()).thenReturn(children);
		offer(children, 0, 11, "Abyssal Demons", VarbitID.SLAYER_CHOOSE_TASK_1,
			VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_ID, VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_VALUE);
		offer(children, 10, 22, "Gargoyles", VarbitID.SLAYER_CHOOSE_TASK_2,
			VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_ID, VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_VALUE);
		settings().put("customList", VALID);
	}

	@After
	public void tearDown()
	{
		plugin.shutDown();
	}

	@Test
	public void readsValidRankingFromSettings()
	{
		start();
		assertEquals(VALID, settings().get("customList"));
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
	}

	@Test
	public void mainListWorksWithoutCustomImport()
	{
		settings().clear();
		when(config.listMode()).thenReturn(ListMode.MAIN);
		start();
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
	}

	@Test
	public void ironmanListWorksWithoutCustomImport()
	{
		settings().clear();
		when(config.listMode()).thenReturn(ListMode.IRONMAN);
		start();
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
	}

	@Test
	public void ironmanBarrageUsesItsOwnCutoffWithoutCustomImport()
	{
		settings().clear();
		Widget[] children = client.getWidget(InterfaceID.SlayerTaskChoice.CONTENT).getDynamicChildren();
		offer(children, 0, 11, "Aberrant Spectres", VarbitID.SLAYER_CHOOSE_TASK_1,
			VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_ID, VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_VALUE);
		when(config.listMode()).thenReturn(ListMode.IRONMAN_BARRAGE);
		start();
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
		selectMode(ListMode.IRONMAN);
		assertAction(Recommendation.Action.SKIP);
		selectMode(ListMode.IRONMAN_BARRAGE);
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
	}

	@Test
	public void switchingListsPreservesCustomImport()
	{
		String custom = export(0);
		settings().put("customList", custom);
		start();
		assertAction(Recommendation.Action.SKIP);
		selectMode(ListMode.MAIN);
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
		selectMode(ListMode.IRONMAN);
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
		selectMode(ListMode.IRONMAN_BARRAGE);
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
		selectMode(ListMode.CUSTOM);
		assertAction(Recommendation.Action.SKIP);
		assertEquals(custom, settings().get("customList"));
	}

	@Test
	public void switchingBetweenDefaultsUsesTheirOwnCutoffs()
	{
		Widget[] children = client.getWidget(InterfaceID.SlayerTaskChoice.CONTENT).getDynamicChildren();
		offer(children, 0, 11, "Bloodveld", VarbitID.SLAYER_CHOOSE_TASK_1,
			VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_ID, VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_VALUE);
		when(config.listMode()).thenReturn(ListMode.MAIN);
		start();
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
		selectMode(ListMode.IRONMAN);
		assertAction(Recommendation.Action.SKIP);
		selectMode(ListMode.MAIN);
		assertAction(Recommendation.Action.TAKE);
		assertEquals(0, plugin.getDisplayState().recommendation.getSlot());
	}

	@Test
	public void invalidImportClearsRecommendationAndValidImportRestoresIt()
	{
		start();
		settings().put("customList", "invalid");
		plugin.onConfigChanged(configChanged());
		drain();
		plugin.onClientTick(new ClientTick());
		assertNull(plugin.getDisplayState());
		assertEquals("invalid", settings().get("customList"));
		verify(configManager, never()).setConfiguration(eq(GROUP), anyString(), anyString());
		settings().put("customList", export(0));
		plugin.onConfigChanged(configChanged());
		drain();
		plugin.onClientTick(new ClientTick());
		assertAction(Recommendation.Action.SKIP);
	}

	@Test
	public void invalidSavedInputDoesNotAffectBuiltInLists()
	{
		settings().put("customList", "invalid");
		start();
		assertNull(plugin.getDisplayState());
		assertEquals("invalid", settings().get("customList"));
		selectMode(ListMode.MAIN);
		assertAction(Recommendation.Action.TAKE);
		selectMode(ListMode.CUSTOM);
		assertNull(plugin.getDisplayState());
	}

	@Test
	public void clearingImportClearsRecommendation()
	{
		start();
		settings().put("customList", " ");
		plugin.onConfigChanged(configChanged());
		drain();
		plugin.onClientTick(new ClientTick());
		assertEquals(" ", settings().get("customList"));
		assertNull(plugin.getDisplayState());
	}

	@Test
	public void profileSwitchNeverReusesPreviousRanking()
	{
		start();
		profileId = 2;
		plugin.onProfileChanged(new ProfileChanged());
		plugin.onGameTick(new GameTick());
		plugin.onClientTick(new ClientTick());
		assertNull(plugin.getDisplayState());
		drain();
		plugin.onClientTick(new ClientTick());
		assertNull(plugin.getDisplayState());
	}

	@Test
	public void queuedImportCannotReadOrWriteAnotherProfile()
	{
		start();
		plugin.onConfigChanged(configChanged());
		profileId = 2;
		settings().put("customList", export(0));
		plugin.onProfileChanged(new ProfileChanged());
		clearInvocations(config, configManager);
		pending.remove().run();
		verify(config, never()).customList();
		verify(configManager, never()).setConfiguration(eq(GROUP), anyString(), anyString());
		drain();
		plugin.onClientTick(new ClientTick());
		assertAction(Recommendation.Action.SKIP);
	}

	@Test
	public void profileSwitchWithInvalidInputClearsRecommendation()
	{
		start();
		profileId = 2;
		settings().put("customList", "invalid");
		plugin.onProfileChanged(new ProfileChanged());
		drain();
		plugin.onClientTick(new ClientTick());
		assertNull(plugin.getDisplayState());
	}

	@Test
	public void pointChangesRefreshTheRecommendation()
	{
		settings().put("customList", export(0));
		variables.put(VarbitID.SLAYER_POINTS, 99);
		start();
		assertAction(Recommendation.Action.TAKE);
		variables.put(VarbitID.SLAYER_POINTS, 100);
		plugin.onVarbitChanged(new VarbitChanged());
		plugin.onClientTick(new ClientTick());
		assertAction(Recommendation.Action.SKIP);
	}

	private void selectMode(ListMode mode)
	{
		when(config.listMode()).thenReturn(mode);
		ConfigChanged event = configChanged();
		event.setKey("listMode");
		plugin.onConfigChanged(event);
		drain();
		plugin.onClientTick(new ClientTick());
	}

	private void start()
	{
		plugin.startUp();
		drain();
		plugin.onClientTick(new ClientTick());
	}

	private void drain()
	{
		while (!pending.isEmpty())
		{
			pending.remove().run();
		}
	}

	private Map<String, String> settings()
	{
		return profiles.computeIfAbsent(profileId, id -> new HashMap<>());
	}

	private void assertAction(Recommendation.Action expected)
	{
		assertNotNull(plugin.getDisplayState());
		assertNotNull(plugin.getDisplayState().recommendation);
		assertEquals(expected, plugin.getDisplayState().recommendation.getAction());
	}

	private void offer(Widget[] children, int child, int id, String name, int taskVarbit, int modifierVarbit, int valueVarbit)
	{
		variables.put(taskVarbit, id);
		variables.put(modifierVarbit, 1);
		variables.put(valueVarbit, 15);
		children[child] = widget(WidgetType.RECTANGLE, "");
		children[child + 1] = widget(WidgetType.TEXT, name);
		children[child + 2] = widget(WidgetType.TEXT, "Amount: 80 to 120");
		children[child + 6] = widget(WidgetType.TEXT, "+15 Slayer points");
		when(client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, id))
			.thenReturn(Collections.singletonList(id));
		when(client.getDBTableField(id, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{name});
	}

	private static Widget widget(int type, String text)
	{
		Widget widget = mock(Widget.class);
		when(widget.getType()).thenReturn(type);
		when(widget.getText()).thenReturn(text);
		return widget;
	}

	private static ConfigChanged configChanged()
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(GROUP);
		event.setKey("customList");
		return event;
	}

	private static String export(int acceptedCount)
	{
		return "{\"type\":\"efficient-mortimer\",\"version\":1,\"acceptedCount\":" + acceptedCount
			+ ",\"ranking\":[\"abyssal-demon/points\",\"gargoyle/points\"]}";
	}
}
