package com.efficientmortimer;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Efficient Mortimer",
	description = "Highlights efficient task choices for post-99 Slayer",
	tags = {"slayer", "mortimer", "ehp"}
)
public class EfficientMortimerPlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ConfigManager configManager;
	@Inject
	private EfficientMortimerConfig config;
	@Inject
	private Gson gson;
	@Inject
	private MortimerOffers offers;
	@Inject
	private EfficientMortimerOverlay overlay;
	@Inject
	private OverlayManager overlayManager;

	private volatile boolean running;
	private volatile long profileId;
	private boolean refreshNeeded;
	private TaskRanking mainRanking;
	private TaskRanking ironmanRanking;
	private TaskRanking ironmanBarrageRanking;
	private TaskRanking customRanking;
	private volatile DisplayState displayState;

	@Override
	protected void startUp()
	{
		mainRanking = loadBundledRanking("main.json");
		ironmanRanking = loadBundledRanking("ironman.json");
		ironmanBarrageRanking = loadBundledRanking("ironman-barrage.json");
		running = true;
		profileId = configManager.getProfile().getId();
		overlayManager.add(overlay);
		clientThread.invoke(() ->
		{
			if (running)
			{
				reloadCustomList();
				refreshNeeded = true;
			}
		});
	}

	@Override
	protected void shutDown()
	{
		running = false;
		overlayManager.remove(overlay);
		displayState = null;
		mainRanking = null;
		ironmanRanking = null;
		ironmanBarrageRanking = null;
		customRanking = null;
		refreshNeeded = false;
	}

	@Provides
	EfficientMortimerConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(EfficientMortimerConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!EfficientMortimerConfig.GROUP.equals(event.getGroup())
			|| configManager.getProfile().getId() != profileId)
		{
			return;
		}
		if ("customList".equals(event.getKey()) || "listMode".equals(event.getKey()))
		{
			long changedProfile = profileId;
			boolean imported = "customList".equals(event.getKey());
			clientThread.invoke(() ->
			{
				if (running && configManager.getProfile().getId() == changedProfile)
				{
					if (imported)
					{
						reloadCustomList();
					}
					refreshNeeded = true;
				}
			});
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		displayState = null;
		clientThread.invoke(() ->
		{
			if (running)
			{
				reloadCustomList();
				profileId = configManager.getProfile().getId();
				refreshNeeded = true;
			}
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.SLAYER_TASK_CHOICE)
		{
			refreshNeeded = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.SLAYER_TASK_CHOICE)
		{
			displayState = null;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (displayState != null)
		{
			refreshNeeded = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		refreshNeeded = true;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		displayState = null;
		refreshNeeded = event.getGameState() == GameState.LOGGED_IN;
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (running && refreshNeeded)
		{
			refreshNeeded = false;
			refreshRecommendation();
		}
	}

	DisplayState getDisplayState()
	{
		return displayState;
	}

	private TaskRanking loadBundledRanking(String filename)
	{
		try (InputStream input = getClass().getResourceAsStream("/com/efficientmortimer/" + filename))
		{
			if (input == null)
			{
				throw new IllegalStateException("Missing bundled task list: " + filename);
			}
			return TaskRanking.parse(gson, new String(input.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not read bundled task list: " + filename, e);
		}
	}

	private void reloadCustomList()
	{
		try
		{
			customRanking = TaskRanking.parse(gson, config.customList().trim());
		}
		catch (IllegalArgumentException e)
		{
			customRanking = null;
		}
	}

	private void refreshRecommendation()
	{
		if (client.getGameState() != GameState.LOGGED_IN || configManager.getProfile().getId() != profileId)
		{
			displayState = null;
			return;
		}
		MortimerOffers.Snapshot snapshot = offers.read();
		if (snapshot == null)
		{
			displayState = null;
			return;
		}
		if (!snapshot.isComplete())
		{
			displayState = new DisplayState(snapshot, null, snapshot.getMessage());
			return;
		}

		ListMode mode = config.listMode();
		TaskRanking ranking;
		switch (mode)
		{
			case MAIN:
				ranking = mainRanking;
				break;
			case IRONMAN:
				ranking = ironmanRanking;
				break;
			case IRONMAN_BARRAGE:
				ranking = ironmanBarrageRanking;
				break;
			default:
				ranking = customRanking;
		}
		if (ranking == null)
		{
			displayState = null;
			return;
		}
		Recommendation recommendation = ranking.recommend(snapshot.getKeys(), client.getVarbitValue(VarbitID.SLAYER_POINTS));
		String message = null;
		if (recommendation.getAction() == Recommendation.Action.UNAVAILABLE)
		{
			message = mode == ListMode.CUSTOM ? "An offer is missing from this list. Import an updated ranking."
				: "An offer is missing from the " + Text.titleCase(mode) + " list.";
		}
		displayState = new DisplayState(snapshot, recommendation, message);
	}

	static final class DisplayState
	{
		final MortimerOffers.Snapshot snapshot;
		final Recommendation recommendation;
		final String message;

		DisplayState(MortimerOffers.Snapshot snapshot, Recommendation recommendation, String message)
		{
			this.snapshot = snapshot;
			this.recommendation = recommendation;
			this.message = message;
		}
	}
}
