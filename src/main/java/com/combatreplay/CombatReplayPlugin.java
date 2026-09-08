package com.combatreplay;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.DecorativeObject;
import net.runelite.api.WallObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.TileObject;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Combat Replay",
	description = "Records combat observations and replays encounters on a 2D timeline",
	tags = {"combat", "replay", "boss", "pvm", "recording"}
)
public class CombatReplayPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(CombatReplayPlugin.class);

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar clientToolbar;
	@Inject private CombatReplayPanel panel;
	@Inject private CombatRecorder recorder;
	@Inject private RecordingStore store;
	@Inject private ScheduledExecutorService executor;
	@Inject private CombatReplayConfig config;
	@Inject private ConfigManager configManager;

	private NavigationButton navigationButton;
	private volatile boolean active;
	private final java.util.Map<String, ReplayUploadQueue> uploadQueues = new java.util.HashMap<>();

	@Provides
	CombatReplayConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(CombatReplayConfig.class);
	}

	@Override
	protected void startUp()
	{
		active = true;
		BufferedImage icon = loadPanelIcon();
		navigationButton = NavigationButton.builder()
			.tooltip("Combat Replay")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		panel.setToggleRecording(() -> clientThread.invoke(this::toggleRecording));
		panel.setPairDevice(this::pairDevice);
		panel.setUpload(selected -> queueUpload(selected.path, selected.recording.recordingId, true));
		panel.setUploadStatus(selected -> executor.execute(() ->
		{
			try
			{
				UploadSidecarState state = new UploadSidecarStore(new Gson(), store.directory())
					.load(selected.recording.recordingId);
				if (active) panel.uploadStatusChanged(ReplayUploadQueue.message(state));
			}
			catch (IOException exception)
			{
				if (active) panel.uploadStatusChanged("Could not read upload status");
			}
		}));
		log.debug("Combat Replay started");
	}

	@Override
	protected void shutDown()
	{
		active = false;
		closeUploadQueues();
		if (recorder.isRecording())
		{
			save(recorder.stop(), false);
		}
		panel.reset();
		clientToolbar.removeNavigation(navigationButton);
		navigationButton = null;
		log.debug("Combat Replay stopped");
	}

	private BufferedImage loadPanelIcon()
	{
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/panel_icon.png");
		if (icon != null)
		{
			return icon;
		}

		// JAR replacement can invalidate URLClassLoader resource caches in the local hot-reload harness.
		// Keep the panel reachable even when that development-only resource lookup fails.
		icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(new Color(45, 45, 45));
			graphics.fillOval(1, 1, 14, 14);
			graphics.setColor(new Color(255, 152, 31));
			graphics.drawOval(1, 1, 14, 14);
			graphics.fill(new Polygon(new int[]{6, 6, 12}, new int[]{4, 12, 8}, 3));
		}
		finally
		{
			graphics.dispose();
		}
		return icon;
	}

	private void toggleRecording()
	{
		if (recorder.isRecording())
		{
			CombatRecording recording = recorder.stop();
			panel.recordingSaving(recording);
			save(recording, true);
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			panel.recordingStopped(null, "Log in before recording", false);
			return;
		}
		recorder.start();
		panel.recordingStarted();
	}

	private void pairDevice(String code)
	{
		final PairingClient pairingClient;
		try
		{
			pairingClient = new PairingClient(URI.create(config.webAddress()));
		}
		catch (IllegalArgumentException exception)
		{
			panel.pairingFinished(false);
			return;
		}
		String pluginVersion = getClass().getPackage().getImplementationVersion();
		pairingClient.exchange(code, "RuneLite desktop",
			pluginVersion == null ? "development" : pluginVersion,
			RuneLiteProperties.getVersion() == null ? "unknown" : RuneLiteProperties.getVersion())
			.whenComplete((credentials, error) ->
			{
				if (error != null)
				{
					panel.pairingFinished(false);
					return;
				}
				if (!active) return;
				closeUploadQueues();
				configManager.setConfiguration("combatreplay", "deviceToken", credentials.getToken());
				configManager.setConfiguration("combatreplay", "uploadEnabled", true);
				panel.pairingFinished(true);
			});
	}

	private void save(CombatRecording recording, boolean updatePanel)
	{
		if (recording == null || recording.ticks.isEmpty())
		{
			if (updatePanel)
			{
				panel.recordingStopped(recording, "Nothing was captured", false);
			}
			return;
		}
		executor.execute(() ->
		{
			try
			{
				Path path = store.save(recording);
				log.debug("Saved combat recording to {}", path);
				queueUpload(path, recording.recordingId);
				if (updatePanel)
				{
					panel.recordingStopped(recording, "Saved " + path.getFileName(), true);
				}
			}
			catch (IOException | RuntimeException exception)
			{
				log.warn("Unable to save combat recording", exception);
				if (updatePanel)
				{
					panel.recordingStopped(recording, "Could not save recording", false);
				}
			}
		});
	}

	private void queueUpload(Path path, String recordingId)
	{
		queueUpload(path, recordingId, false);
	}

	private synchronized void closeUploadQueues()
	{
		for (ReplayUploadQueue queue : uploadQueues.values()) queue.close();
		uploadQueues.clear();
	}

	private synchronized void queueUpload(Path path, String recordingId, boolean manualRetry)
	{
		if (!active) return;
		String token = config.deviceToken();
		if (!config.uploadEnabled() || token == null || token.trim().isEmpty())
		{
			if (manualRetry) panel.uploadStatusChanged("Pair a web device and enable uploads first");
			return;
		}
		try
		{
			ReplayUploadQueue queue = uploadQueues.get(recordingId);
			if (queue == null)
			{
				ReplayUploadClient client = new ReplayUploadClient(URI.create(config.webAddress()), executor);
				UploadSidecarStore sidecars = new UploadSidecarStore(new Gson(), store.directory());
				queue = new ReplayUploadQueue(client, sidecars, executor, token, panel::uploadStatusChanged);
				uploadQueues.put(recordingId, queue);
			}
			queue.enqueue(path, recordingId, manualRetry);
		}
		catch (IllegalArgumentException exception)
		{
			log.warn("Combat Replay web address is invalid");
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		recorder.captureTick();
		if (recorder.isRecording())
		{
			panel.updateRecording(recorder.snapshot());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		recorder.captureContainerChange(event.getContainerId(), event.getItemContainer());
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		recorder.upsertGroundItem(event.getTile(), event.getItem(), event.getItem().getQuantity());
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		recorder.upsertGroundItem(event.getTile(), event.getItem(), event.getNewQuantity());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		recorder.removeGroundItem(event.getItem());
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!recorder.isRecording()) return;
		String option = sanitized(event.getMenuOption());
		String targetText = sanitized(event.getMenuTarget());
		MenuAction action = event.getMenuAction();
		Actor targetActor = event.getMenuEntry().getNpc() != null
			? event.getMenuEntry().getNpc() : event.getMenuEntry().getPlayer();
		String kind = null;
		Integer itemId = null, widgetId = null, objectId = null;
		LocalPoint location = null;
		boolean objectAction = action == MenuAction.ITEM_USE_ON_GAME_OBJECT
			|| action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT;
		boolean actorItemAction = action == MenuAction.ITEM_USE_ON_NPC
			|| action == MenuAction.ITEM_USE_ON_PLAYER;
		boolean actorSpellAction = action == MenuAction.WIDGET_TARGET_ON_NPC
			|| action == MenuAction.WIDGET_TARGET_ON_PLAYER;
		if ("Attack".equalsIgnoreCase(option) && targetActor != null) kind = "attack_actor";
		else if (actorSpellAction && "Cast".equalsIgnoreCase(option)) kind = "cast_on_actor";
		else if (action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT && "Cast".equalsIgnoreCase(option)) kind = "cast_on_object";
		else if (action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM && "Cast".equalsIgnoreCase(option)) kind = "cast_on_ground_item";
		else if (actorItemAction) kind = "use_item_on_actor";
		else if (action == MenuAction.ITEM_USE_ON_GAME_OBJECT) kind = "use_item_on_object";
		else if ("Take".equalsIgnoreCase(option) && isGroundItemAction(action))
		{
			kind = "take_ground_item";
			itemId = event.getId();
		}
		else if (event.isItemOp() && isCombatItemOption(option))
		{
			kind = isEquipOption(option) ? "equip_item" : "use_inventory_item";
			itemId = event.getItemId();
		}
		if (kind == null) return;
		if (objectAction)
		{
			objectId = event.getId();
			location = LocalPoint.fromScene(event.getParam0(), event.getParam1());
		}
		else if (kind.endsWith("ground_item"))
		{
			location = LocalPoint.fromScene(event.getParam0(), event.getParam1());
		}
		if (event.getMenuEntry().getWidget() != null) widgetId = event.getMenuEntry().getWidget().getId();
		recorder.addActionAttempt(kind, targetActor, option, targetText, action.name(),
			itemId, widgetId, objectId, location);
	}

	private static boolean isGroundItemAction(MenuAction action)
	{
		return action == MenuAction.GROUND_ITEM_FIRST_OPTION || action == MenuAction.GROUND_ITEM_SECOND_OPTION
			|| action == MenuAction.GROUND_ITEM_THIRD_OPTION || action == MenuAction.GROUND_ITEM_FOURTH_OPTION
			|| action == MenuAction.GROUND_ITEM_FIFTH_OPTION;
	}

	private static boolean isCombatItemOption(String option)
	{
		return isEquipOption(option) || "Eat".equalsIgnoreCase(option) || "Drink".equalsIgnoreCase(option);
	}

	private static boolean isEquipOption(String option)
	{
		return "Wield".equalsIgnoreCase(option) || "Wear".equalsIgnoreCase(option)
			|| "Equip".equalsIgnoreCase(option);
	}

	private static String sanitized(String text)
	{
		String value = Text.removeTags(text == null ? "" : text).trim();
		return value.length() <= 300 ? value : value.substring(0, 300);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		recorder.addEvent("HITSPLAT", event.getActor(), null,
			event.getHitsplat().getHitsplatType(), event.getHitsplat().getAmount(), null,
			event.getHitsplat().isMine() ? "mine" : event.getHitsplat().isOthers() ? "other" : null);
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile projectile = event.getProjectile();
		recorder.captureProjectile(projectile, event.getPosition());
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		GraphicsObject graphic = event.getGraphicsObject();
		recorder.addEvent("GRAPHIC", null, null, graphic.getId(), graphic.getStartCycle(),
			graphic.getLocation(), null);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		recorder.addEvent("ANIMATION", event.getActor(), null, event.getActor().getAnimation(), 0, null, null);
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		recorder.addEvent("ACTOR_GRAPHIC", event.getActor(), null, event.getActor().getGraphic(), 0, null, null);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		recorder.addEvent("INTERACTING", event.getSource(), event.getTarget(), 0, 0, null, null);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		recorder.addEvent("DEATH", event.getActor(), null, 0, 0, null, null);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		recorder.addEvent("NPC_SPAWN", event.getNpc(), null, event.getNpc().getId(), 0,
			event.getNpc().getLocalLocation(), null);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		recorder.addEvent("NPC_DESPAWN", event.getNpc(), null, event.getNpc().getId(), 0,
			event.getNpc().getLocalLocation(), null);
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		recorder.captureNpcTransform(event.getNpc(), event.getOld());
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		recorder.addEvent("PLAYER_SPAWN", event.getPlayer(), null, 0, 0,
			event.getPlayer().getLocalLocation(), null);
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		recorder.addEvent("PLAYER_DESPAWN", event.getPlayer(), null, 0, 0,
			event.getPlayer().getLocalLocation(), null);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		recordObject("GAME_OBJECT_SPAWN", event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		recordObject("GAME_OBJECT_DESPAWN", event.getGameObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		recordObject("GROUND_OBJECT_SPAWN", event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		recordObject("GROUND_OBJECT_DESPAWN", event.getGroundObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		recordObject("DECORATIVE_OBJECT_SPAWN", event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		recordObject("DECORATIVE_OBJECT_DESPAWN", event.getDecorativeObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		recordObject("WALL_OBJECT_SPAWN", event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		recordObject("WALL_OBJECT_DESPAWN", event.getWallObject());
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		Actor actor = event.getActor();
		if (actor instanceof NPC)
		{
			recorder.addEvent("OVERHEAD_TEXT", actor, null, 0, 0, null, actor.getOverheadText());
		}
	}

	private void recordObject(String type, TileObject object)
	{
		String category;
		Integer orientation = null, configuration = null;
		if (object instanceof GameObject)
		{
			category = "game_object";
			orientation = ((GameObject) object).getOrientation();
			configuration = ((GameObject) object).getConfig();
		}
		else if (object instanceof WallObject)
		{
			category = "wall";
			orientation = ((WallObject) object).getOrientationA();
			configuration = ((WallObject) object).getConfig();
		}
		else if (object instanceof GroundObject)
		{
			category = "ground";
			configuration = ((GroundObject) object).getConfig();
		}
		else
		{
			category = "decorative";
			if (object instanceof DecorativeObject) configuration = ((DecorativeObject) object).getConfig();
		}
		recorder.captureObjectEvent(type, object, category, orientation, configuration);
	}
}
