package machindustry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.function.Function;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Label;
import machindustry.PathTask.PathType;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.DisposeEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsCategory;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable.Setting;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.liquid.LiquidBlock;
import mindustry.world.blocks.storage.CoreBlock;

public class Machindustry extends Mod
{
	private static final String _name = "machindustry";

	private static final String _polygonSafeZoneName = "polygon-safe-zone";
	private static final String _radiusSafeZoneName = "radius-safe-zone";
	private static final String _frequencyName = "time-check-frequency";
	private static final String _buildTimeName = "time-to-build-path";

	private static final String _beamMaskAroundBuildName = "beam-mask-around-build";
	private static final String _beamMaskAroundCoreName = "beam-mask-around-core";
	private static final String _beamMaskAroundLiquidName = "beam-mask-around-liquid";
	private static final String _beamMaskAroundSolidName = "beam-mask-around-solid";

	private static final String _beamBuildTimeName = "beam-time-to-build-path";
	private static final String _beamIgnoreMaskName = "beam-path-ignore-mask";
	private static final String _beamTargetModeName = "beam-path-target-mode";

	private static final String _liquidMaskAroundBuildName = "liquid-mask-around-build";
	private static final String _liquidMaskAroundCoreName = "liquid-mask-around-core";
	private static final String _liquidMaskAroundLiquidName = "liquid-mask-around-liquid";
	private static final String _liquidMaskAroundSolidName = "liquid-mask-around-solid";

	private static final String _liquidBuildTimeName = "liquid-time-to-build-path";
	private static final String _liquidIgnoreMaskName = "liquid-path-ignore-mask";
	private static final String _liquidTargetModeName = "liquid-path-target-mode";
	private static final String _liquidReplaceOneName = "liquid-replace-one";

	private static final String _solidMaskAroundBuildName = "solid-mask-around-build";
	private static final String _solidMaskAroundCoreName = "solid-mask-around-core";
	private static final String _solidMaskAroundLiquidName = "solid-mask-around-liquid";
	private static final String _solidMaskAroundSolidName = "solid-mask-around-solid";

	private static final String _solidBuildTimeName = "solid-time-to-build-path";
	private static final String _solidIgnoreMaskName = "solid-path-ignore-mask";
	private static final String _solidTargetModeName = "solid-path-target-mode";
	private static final String _solidReplaceOneName = "solid-replace-one";
	private static final String _solidReplaceWithName = "solid-replace-with";

	private final InputListener _inputListener = new MachindustryInputListener();
	private final Cons<SettingsTable> _settingsBuilder = t -> BuildSettingsTable(t);

	private final Cons<DisposeEvent> _gameExitEventCons = e -> MachindustryDispose();
	private final Cons<WorldLoadEvent> _worldLoadEventCons = e -> MachindustryUpdate();

	private final Runnable _worldUpdateRunnable = () -> WorldUpdateRunnable();

	private final TaskQueue _taskQueue = new TaskQueue();
	private final Thread _thread;

	/**
	 * Used to stop worker thread
	*/
	private boolean _running = true;

	/**
	 * Used to stop working on old task when new game world is loaded.
	 * It must be incremented AFTER task queue clearing.
	 * It must be locally stored BEFORE task removing from task queue.
	*/
	private long _taskEpoch = 0;

	private KeyCode _beamPathFinderCode = KeyCode.y;
	private KeyCode _liquidPathFinderCode = KeyCode.u;
	private KeyCode _solidPathFinderCode = KeyCode.i;

	private int _height = -1;
	private int _width = -1;
	private int _size = -1;

	private boolean[] _masksMap = null;
	private WorldState _worldState = null;

	private BeamPathFinder _beamPathFinder = null;
	private LiquidPathFinder _liquidPathFinder = null;
	private SolidPathFinder _solidPathFinder = null;

	private Point _beamFirstPoint = null;
	private Point _liquidFirstPoint = null;
	private Point _solidFirstPoint = null;

	private Point _beamLastPoint = null;
	private Point _liquidLastPoint = null;
	private Point _solidLastPoint = null;
	
	private static boolean DoHandle()
	{
		return !Core.scene.hasKeyboard()
			&& !Vars.state.isMenu()
			&& !Vars.ui.about.isShown()
			&& !Vars.ui.admins.isShown()
			&& !Vars.ui.bans.isShown()
			&& !Vars.ui.campaignComplete.isShown()
			&& !Vars.ui.chatfrag.shown()
			&& !Vars.ui.consolefrag.shown()
			&& !Vars.ui.content.isShown()
			&& !Vars.ui.controls.isShown()
			&& !Vars.ui.custom.isShown()
			&& !Vars.ui.database.isShown()
			&& !Vars.ui.discord.isShown()
			&& !Vars.ui.editor.isShown()
			&& !Vars.ui.effects.isShown()
			&& Vars.ui.followUpMenus.size == 0
			&& !Vars.ui.fullText.isShown()
			&& !Vars.ui.hints.shown()
			&& !Vars.ui.host.isShown()
			&& !Vars.ui.join.isShown()
			&& !Vars.ui.language.isShown()
			&& !Vars.ui.load.isShown()
			&& !Vars.ui.logic.isShown()
			&& !Vars.ui.maps.isShown()
			&& !Vars.ui.mods.isShown()
			&& !Vars.ui.paused.isShown()
			&& !Vars.ui.picker.isShown()
			&& !Vars.ui.planet.isShown()
			&& !Vars.ui.research.isShown()
			&& !Vars.ui.restart.isShown()
			&& !Vars.ui.schematics.isShown()
			&& !Vars.ui.settings.isShown()
			&& !Vars.ui.traces.isShown();
	}

	private static String GetReplacerSolidName(int value)
	{
		switch (value)
		{
			case 0:
				return "duct-router";
					
			case 1:
				return "overflow-duct";
					
			case 2:
				return "underflow-duct";

			default:
				return "";
		}
	}

	private static int GetRotate(final int blockX, final int blockY, final int tileX, final int tileY)
	{
		final Tiles tiles = Vars.world.tiles;

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		final Tile tile = tiles.get(blockX, blockY);
		final Block block = tile.block();
		final Building build = tile.build;

		final int buildX = build == null ? (int)tile.x : (int)build.tile.x;
		final int buildY = build == null ? (int)tile.y : (int)build.tile.y;

		if (tileX == buildX + block.sizeOffset + block.size)
			return 0;
		else if (tileY == buildY + block.sizeOffset + block.size)
			return 1;
		else if (tileX == buildX + block.sizeOffset - 1)
			return 2;
		else if (tileY == buildY + block.sizeOffset - 1)
			return 3;
		else
			return -1;
	}

	private static void PrintLine(final String x)
	{
		System.out.println("[Machindustry] " + x);
	}

	private static void ReplaceLiquid(final LinkedList<BuildPlan> buildPlans, final int x, final int y)
	{
		if (Core.settings.getBool(_liquidReplaceOneName))
		{
			final Team team = Vars.player.team();
			final Tiles tiles = Vars.world.tiles;

			if (team == null)
				throw new NullPointerException("Vars.player.team is null");

			if (tiles == null)
				throw new NullPointerException("Vars.world.tiles is null");

			final Tile tile = tiles.get(x, y);
			final Block block = tile.block();
			final Building build = tile.build;

			if (build != null && build.team == team && block == Blocks.reinforcedConduit)
				buildPlans.addLast(new BuildPlan(x, y, build.rotation, Blocks.reinforcedLiquidRouter));
		}
	}

	private static void ReplaceSolid(final LinkedList<BuildPlan> buildPlans, final int x, final int y)
	{
		if (Core.settings.getBool(_solidReplaceOneName))
		{
			final Team team = Vars.player.team();
			final Tiles tiles = Vars.world.tiles;

			if (team == null)
				throw new NullPointerException("Vars.player.team is null");

			if (tiles == null)
				throw new NullPointerException("Vars.world.tiles is null");

			final Tile tile = tiles.get(x, y);
			final Block block = tile.block();
			final Building build = tile.build;

			Block replaceWithBlock;
			switch (Core.settings.getInt(_solidReplaceWithName))
			{
				case 0:
					replaceWithBlock = Blocks.ductRouter;
					break;
					
				case 1:
					replaceWithBlock = Blocks.overflowDuct;
					break;
					
				case 2:
					replaceWithBlock = Blocks.underflowDuct;
					break;

				default:
					replaceWithBlock = block;
					break;
			}

			if (build != null && build.team == team && (block == Blocks.duct || block == Blocks.armoredDuct))
				buildPlans.addLast(new BuildPlan(x, y, build.rotation, replaceWithBlock));
		}
	}

	private static void SortPoints(final ArrayList<Point> edgePoints, final Point a, final Point b)
	{
		edgePoints.sort((p1, p2) ->
		{
			int m1 = Math.abs(p1.x - a.x) + Math.abs(p1.y - a.y);
			int m2 = Math.abs(p2.x - a.x) + Math.abs(p2.y - a.y);

			if (m1 == m2)
			{
				float x = (float)b.x;
				float y = (float)b.y;

				float e1 = Mathf.dst2((float)p1.x, (float)p1.y, x, y);
				float e2 = Mathf.dst2((float)p2.x, (float)p2.y, x, y);

				return Float.compare(e1, e2);
			}
			else
				return Integer.compare(m1, m2);
		});
	}

	private void BuildSettingsTable(SettingsTable mindustrySettingsTable)
	{
		Setting visibleSpace = new SpaceSetting(Color.gold, 25F, 0F, 25F, 0F);
		Setting invisibleSpace = new SpaceSetting(Color.clear, 5F, 0F, 5F, 0F);

		SettingsTable machindustrySettingsTable = new SettingsTable();

		machindustrySettingsTable.pref(visibleSpace);
		machindustrySettingsTable.pref(new TextSetting(Core.bundle.get("machindustry.settings-title")));
		machindustrySettingsTable.pref(invisibleSpace);

		machindustrySettingsTable.checkPref("polygon-safe-zone", true);
		machindustrySettingsTable.sliderPref("radius-safe-zone", 1, 0, 9, 1, v -> Integer.toString(v));
		machindustrySettingsTable.sliderPref("time-check-frequency", 1000, 100, 10000, 100, v -> Integer.toString(v));
		machindustrySettingsTable.sliderPref("time-to-build-path", 100, 10, 1000, 10, v -> Integer.toString(v));

		machindustrySettingsTable.pref(visibleSpace);
		machindustrySettingsTable.pref(new TextSetting(Core.bundle.get("machindustry.beam-title")));
		machindustrySettingsTable.pref(invisibleSpace);

		machindustrySettingsTable.textPref("beam-key", _beamPathFinderCode.name().toUpperCase(), v ->
		{
			KeyCode code = Arrays.stream(KeyCode.values()).filter(k -> k.value.equalsIgnoreCase(v)).findFirst().orElse(null);
			Core.settings.put("beam-key", code == null ? _beamPathFinderCode.name().toUpperCase() : code.name().toUpperCase());
		});

		machindustrySettingsTable.pref(invisibleSpace);

		machindustrySettingsTable.checkPref("beam-mask-around-build", true);
		machindustrySettingsTable.checkPref("beam-mask-around-core", true);
		machindustrySettingsTable.checkPref("beam-mask-around-liquid", false);
		machindustrySettingsTable.checkPref("beam-mask-around-solid", false);

		machindustrySettingsTable.pref(invisibleSpace);

		machindustrySettingsTable.sliderPref("beam-time-to-build-path", 500, 50, 5000, 50, v -> Integer.toString(v));
		machindustrySettingsTable.checkPref("beam-path-ignore-mask", true);
		machindustrySettingsTable.checkPref("beam-path-target-mode", true);

		machindustrySettingsTable.pref(visibleSpace);
		machindustrySettingsTable.pref(new TextSetting(Core.bundle.get("machindustry.liquid-title")));
		machindustrySettingsTable.pref(invisibleSpace);

		machindustrySettingsTable.textPref("liquid-key", _liquidPathFinderCode.name().toUpperCase(), v ->
		{
			KeyCode code = Arrays.stream(KeyCode.values()).filter(k -> k.value.equalsIgnoreCase(v)).findFirst().orElse(null);
			Core.settings.put("liquid-key", code == null ? _liquidPathFinderCode.name().toUpperCase() : code.name().toUpperCase());
		});

		machindustrySettingsTable.pref(invisibleSpace);

		machindustrySettingsTable.checkPref("liquid-mask-around-build", true);
		machindustrySettingsTable.checkPref("liquid-mask-around-core", true);
		machindustrySettingsTable.checkPref("liquid-mask-around-liquid", false);
		machindustrySettingsTable.checkPref("liquid-mask-around-solid", false);

		machindustrySettingsTable.pref(invisibleSpace);

		machindustrySettingsTable.sliderPref("liquid-time-to-build-path", 1000, 100, 10000, 100, v -> Integer.toString(v));
		machindustrySettingsTable.checkPref("liquid-path-ignore-mask", true);
		machindustrySettingsTable.checkPref("liquid-path-target-mode", true);
		machindustrySettingsTable.checkPref("liquid-replace-one", true);

		machindustrySettingsTable.pref(visibleSpace);
		machindustrySettingsTable.pref(new TextSetting(Core.bundle.get("machindustry.solid-title")));
		machindustrySettingsTable.pref(invisibleSpace);

		machindustrySettingsTable.textPref("solid-key", _solidPathFinderCode.name().toUpperCase(), v ->
		{
			KeyCode code = Arrays.stream(KeyCode.values()).filter(k -> k.value.equalsIgnoreCase(v)).findFirst().orElse(null);
			Core.settings.put("solid-key", code == null ? _solidPathFinderCode.name().toUpperCase() : code.name().toUpperCase());
		});

		machindustrySettingsTable.pref(invisibleSpace);

		machindustrySettingsTable.checkPref("solid-mask-around-build", true);
		machindustrySettingsTable.checkPref("solid-mask-around-core", true);
		machindustrySettingsTable.checkPref("solid-mask-around-liquid", false);
		machindustrySettingsTable.checkPref("solid-mask-around-solid", false);

		machindustrySettingsTable.pref(invisibleSpace);

		machindustrySettingsTable.sliderPref("solid-time-to-build-path", 1000, 100, 10000, 100, v -> Integer.toString(v));
		machindustrySettingsTable.checkPref("solid-path-ignore-mask", true);
		machindustrySettingsTable.checkPref("solid-path-target-mode", true);
		machindustrySettingsTable.checkPref("solid-replace-one", true);
		machindustrySettingsTable.sliderPref("solid-replace-with", 0, 0, 2, 1, v -> Core.bundle.get("machindustry.solid-replace-with-" + GetReplacerSolidName(v)));

		machindustrySettingsTable.pref(visibleSpace);

		mindustrySettingsTable.add(machindustrySettingsTable);
	}

	private boolean Expired(long endTime, long taskEpoch)
	{
		return endTime <= System.nanoTime() || taskEpoch != _taskEpoch;
	}

	private void FillMasksMap
	(
		final boolean[] validMap,
		final boolean maskAroundBuild,
		final boolean maskAroundCore,
		final boolean maskAroundLiquid,
		final boolean maskAroundSolid
	)
	{
		if (maskAroundBuild || maskAroundCore || maskAroundLiquid || maskAroundSolid)
		{
			final Team team = Vars.player.team();
			final Tiles tiles = Vars.world.tiles;

			if (team == null)
				throw new NullPointerException("Vars.player.team is null");

			if (tiles == null)
				throw new NullPointerException("Vars.world.tiles is null");

			for (int y = 0, i = 0; y < _height; ++y)
				for (int x = 0; x < _width; ++x, ++i)
					if (!validMap[i])
					{
						final Tile tile = tiles.geti(i);
						final Block block = tile.block();
						final Building build = tile.build;

						if (build != null && build.team == team && build.tile == tile)
						{
							final boolean c = block instanceof CoreBlock;
							final boolean l = block instanceof LiquidBlock;
							final boolean s = block.isDuct;
							final boolean b = !(c || l || s);

							if ((maskAroundBuild && b) || (maskAroundCore && c) || (maskAroundLiquid && l) || (maskAroundSolid && s))
							{
								int x1 = i + (block.sizeOffset - 1) * _width + block.sizeOffset;
								int x2 = x1 + block.size;

								if (y > -block.sizeOffset)
									for (int j = x1; j < x2; ++j)
										_masksMap[j] = true;

								if (y < _height - (block.size + block.sizeOffset))
								{
									final int dx = (block.size + 1) * _width;

									x1 += dx;
									x2 += dx;

									for (int j = x1; j < x2; ++j)
										_masksMap[j] = true;
								}

								int y1 = i + block.sizeOffset * _width + block.sizeOffset - 1;
								int y2 = y1 + block.size * _width;

								if (x > -block.sizeOffset)
									for (int j = y1; j < y2; j += _width)
										_masksMap[j] = true;

								if (x < _width - (block.size + block.sizeOffset))
								{
									final int dy = block.size + 1;

									y1 += dy;
									y2 += dy;

									for (int j = y1; j < y2; j += _width)
										_masksMap[j] = true;
								}
							}
						}
					}
		}
	}

	private LinkedList<BuildPlan> FindPath
	(
		final Function<Pair<Point, Point>, LinkedList<BuildPlan>> function,
		final ArrayList<Point> pointList1,
		final ArrayList<Point> pointList2,
		final long endTime,
		final long taskEpoch
	)
	{
		final int size1 = pointList1.size();
		final int size2 = pointList2.size();

		if (size1 != 0 && size2 != 0)
		{
			final int size = Math.max(size1, size2);

			for (int k = 0; k < size; ++k)
			{
				if (Expired(endTime, taskEpoch))
					return null;

				final LinkedList<BuildPlan> buildPlans = function.apply
				(
					new Pair<Point, Point>(pointList1.get(k % size1), pointList2.get(k % size2))
				);

				if (buildPlans != null)
					return buildPlans;
			}

			for (int i = 0; i < size1; ++i)
				for (int j = 0; j < size2; ++j)
					if (i % size2 != j % size1)
					{
						if (Expired(endTime, taskEpoch))
							return null;

						final LinkedList<BuildPlan> buildPlans = function.apply
						(
							new Pair<Point, Point>(pointList1.get(i), pointList2.get(j))
						);

						if (buildPlans != null)
							return buildPlans;
					}
		}

		return null;
	}

	private LinkedList<BuildPlan> FindPath
	(
		final BeamPathFinder pathFinder,
		final WorldState worldState,
		final int x1,
		final int y1,
		final int x2,
		final int y2,
		final long taskEpoch
	)
	{
		final long endTime = System.nanoTime() + (long)Core.settings.getInt(_beamBuildTimeName) * (long)1000000;

		final boolean ignoreMask = Core.settings.getBool(_beamIgnoreMaskName);
		final boolean targetMode = Core.settings.getBool(_beamTargetModeName);

		worldState.UpdateMap();

		if (Expired(endTime, taskEpoch))
			return null;

		FillMasksMap
		(
			worldState.Map,
			Core.settings.getBool(_beamMaskAroundBuildName),
			Core.settings.getBool(_beamMaskAroundCoreName),
			Core.settings.getBool(_beamMaskAroundLiquidName),
			Core.settings.getBool(_beamMaskAroundSolidName)
		);

		if (Expired(endTime, taskEpoch))
			return null;

		pathFinder.UpdateMap(worldState.Map);

		if (Expired(endTime, taskEpoch))
			return null;

		pathFinder.UpdateMap(worldState.BuildPlans);

		if (Expired(endTime, taskEpoch))
			return null;

		final LinkedList<BuildPlan> buildPlans = pathFinder.BuildPath(x1, y1, x2, y2, targetMode, _masksMap);

		if (buildPlans == null && ignoreMask)
		{
			if (Expired(endTime, taskEpoch))
				return null;

			return pathFinder.BuildPath(x1, y1, x2, y2, targetMode, null);
		}
		else
			return buildPlans;
	}

	private LinkedList<BuildPlan> FindPath
	(
		final LiquidPathFinder pathFinder,
		final WorldState worldState,
		final int x1,
		final int y1,
		final int x2,
		final int y2,
		final long taskEpoch
	)
	{
		final long endTime = System.nanoTime() + (long)Core.settings.getInt(_liquidBuildTimeName) * (long)1000000;

		final boolean ignoreMask = Core.settings.getBool(_liquidIgnoreMaskName);
		final boolean targetMode = Core.settings.getBool(_liquidTargetModeName);

		worldState.UpdateMap();

		if (Expired(endTime, taskEpoch))
			return null;

		FillMasksMap
		(
			worldState.Map,
			Core.settings.getBool(_liquidMaskAroundBuildName),
			Core.settings.getBool(_liquidMaskAroundCoreName),
			Core.settings.getBool(_liquidMaskAroundLiquidName),
			Core.settings.getBool(_liquidMaskAroundSolidName)
		);

		if (Expired(endTime, taskEpoch))
			return null;

		pathFinder.UpdateMap(worldState.Map);

		if (Expired(endTime, taskEpoch))
			return null;

		pathFinder.UpdateMap(worldState.BuildPlans);

		if (Expired(endTime, taskEpoch))
			return null;

		final Pair<ArrayList<Point>, ArrayList<Point>> pair = GetPoints(worldState.Map, x1, y1, x2, y2);

		LinkedList<BuildPlan> buildPlans = FindPath
		(
			(p) -> pathFinder.BuildPath(p.a.x, p.a.y, p.b.x, p.b.y, GetRotate(x1, y1, p.a.x, p.a.y), targetMode, _masksMap),
			pair.a,
			pair.b,
			endTime,
			taskEpoch
		);

		if (buildPlans == null && ignoreMask && !Expired(endTime, taskEpoch))
			buildPlans = FindPath
			(
				(p) -> pathFinder.BuildPath(p.a.x, p.a.y, p.b.x, p.b.y, GetRotate(x1, y1, p.a.x, p.a.y), targetMode, null),
				pair.a,
				pair.b,
				endTime,
				taskEpoch
			);

		if (buildPlans != null)
		{
			ReplaceLiquid(buildPlans, x1, y1);
			return buildPlans;
		}

		return null;
	}

	private LinkedList<BuildPlan> FindPath
	(
		final SolidPathFinder pathFinder,
		final WorldState worldState,
		final int x1,
		final int y1,
		final int x2,
		final int y2,
		final long taskEpoch
	)
	{
		final long endTime = System.nanoTime() + (long)Core.settings.getInt(_solidBuildTimeName) * (long)1000000;

		final boolean ignoreMask = Core.settings.getBool(_solidIgnoreMaskName);
		final boolean targetMode = Core.settings.getBool(_solidTargetModeName);

		worldState.UpdateMap();

		if (Expired(endTime, taskEpoch))
			return null;

		FillMasksMap
		(
			worldState.Map,
			Core.settings.getBool(_solidMaskAroundBuildName),
			Core.settings.getBool(_solidMaskAroundCoreName),
			Core.settings.getBool(_solidMaskAroundLiquidName),
			Core.settings.getBool(_solidMaskAroundSolidName)
		);

		if (Expired(endTime, taskEpoch))
			return null;

		pathFinder.UpdateMap(worldState.Map);

		if (Expired(endTime, taskEpoch))
			return null;

		pathFinder.UpdateMap(worldState.BuildPlans);

		if (Expired(endTime, taskEpoch))
			return null;

		final Pair<ArrayList<Point>, ArrayList<Point>> pair = GetPoints(worldState.Map, x1, y1, x2, y2);

		LinkedList<BuildPlan> buildPlans = FindPath
		(
			(p) -> pathFinder.BuildPath(p.a.x, p.a.y, p.b.x, p.b.y, targetMode, _masksMap),
			pair.a,
			pair.b,
			endTime,
			taskEpoch
		);

		if (buildPlans == null && ignoreMask && !Expired(endTime, taskEpoch))
			buildPlans = FindPath
			(
				(p) -> pathFinder.BuildPath(p.a.x, p.a.y, p.b.x, p.b.y, targetMode, null),
				pair.a,
				pair.b,
				endTime,
				taskEpoch
			);

		if (buildPlans != null)
		{
			ReplaceSolid(buildPlans, x1, y1);
			return buildPlans;
		}

		return null;
	}

	private Point GetCurrentPoint()
	{
		final float tilesize = (float)Vars.tilesize;
		final Vec2 vec2 = Core.input.mouseWorld();

		final int x = (int)Math.floor(vec2.x / tilesize);
		final int y = (int)Math.floor(vec2.y / tilesize);
		final int i = x + y * _width;

		return new Point(x, y, i);
	}

	private ArrayList<Point> GetInnerEdgePoints(final Block b, final int x, final int y, final int i)
	{
		ArrayList<Point> edgePoints;

		if (b.size == 1)
		{
			edgePoints = new ArrayList<Point>(1);
			edgePoints.add(new Point(x, y, i));
		}
		else
		{
			edgePoints = new ArrayList<Point>((b.size - 1) * 4);

			final int dy = b.size - 1;
			final int dx = dy * _width;

			int x1 = i + b.sizeOffset * _width + b.sizeOffset;
			int x2 = x1 + b.size - 1;

			int xx = x + b.sizeOffset;
			int yy = y + b.sizeOffset;

			for (int j = x1, k = xx; j < x2; ++j, ++k)
				edgePoints.add(new Point(k, yy, j));

			x1 += dx;
			x2 += dx;
			yy += dy;

			for (int j = x1 + 1, k = xx + 1; j <= x2; ++j, ++k)
				edgePoints.add(new Point(k, yy, j));

			int y1 = i + b.sizeOffset * _width + b.sizeOffset;
			int y2 = y1 + (b.size - 1) * _width;

			xx = x + b.sizeOffset;
			yy = y + b.sizeOffset;

			for (int j = y1 + _width, k = yy + 1; j <= y2; j += _width, ++k)
				edgePoints.add(new Point(xx, k, j));

			y1 += dy;
			y2 += dy;
			xx += dy;

			for (int j = y1, k = yy; j < y2; j += _width, ++k)
				edgePoints.add(new Point(xx, k, j));
		}

		return edgePoints;
	}

	private ArrayList<Point> GetOuterEdgePoints(final Block b, final int x, final int y, final int i)
	{
		ArrayList<Point> edgePoints = new ArrayList<Point>(b.size * 4);

		final int dy = b.size + 1;
		final int dx = dy * _width;

		int x1 = i + (b.sizeOffset - 1) * _width + b.sizeOffset;
		int x2 = x1 + b.size;

		int xx = x + b.sizeOffset;
		int yy = y + b.sizeOffset - 1;

		if (y > -b.sizeOffset)
			for (int j = x1, k = xx; j < x2; ++j, ++k)
				edgePoints.add(new Point(k, yy, j));

		if (y < _height - (b.size + b.sizeOffset))
		{
			x1 += dx;
			x2 += dx;
			yy += dy;

			for (int j = x1, k = xx; j < x2; ++j, ++k)
				edgePoints.add(new Point(k, yy, j));
		}

		int y1 = i + b.sizeOffset * _width + b.sizeOffset - 1;
		int y2 = y1 + b.size * _width;

		xx = x + b.sizeOffset - 1;
		yy = y + b.sizeOffset;

		if (x > -b.sizeOffset)
			for (int j = y1, k = yy; j < y2; j += _width, ++k)
				edgePoints.add(new Point(xx, k, j));

		if (x < _width - (b.size + b.sizeOffset))
		{
			y1 += dy;
			y2 += dy;
			xx += dy;

			for (int j = y1, k = yy; j < y2; j += _width, ++k)
				edgePoints.add(new Point(xx, k, j));
		}

		return edgePoints;
	}

	private Pair<ArrayList<Point>, ArrayList<Point>> GetPoints
	(
		final boolean[] validMap,
		final int x1,
		final int y1,
		final int x2,
		final int y2
	)
	{
		final Tiles tiles = Vars.world.tiles;

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		final int i1 = x1 + y1 * _width;
		final int i2 = x2 + y2 * _width;

		final Point a = new Point(x1, y1, i1);
		final Point b = new Point(x2, y2, i2);

		Tile tile1 = tiles.geti(i1);
		Tile tile2 = tiles.geti(i2);

		final Block block1 = tile1.block();
		final Block block2 = tile2.block();

		final Building build1 = tile1.build;
		final Building build2 = tile2.build;

		if (build1 != null)
			tile1 = build1.tile;

		if (build2 != null)
			tile2 = build2.tile;

		final ArrayList<Point> p1 = GetOuterEdgePoints(block1, (int)tile1.x, (int)tile1.y, i1);
		final ArrayList<Point> p2 = GetInnerEdgePoints(block2, (int)tile2.x, (int)tile2.y, i2);

		final int s1 = p1.size();
		final int s2 = p2.size();

		final ArrayList<Point> pointList1 = new ArrayList<Point>(s1);
		final ArrayList<Point> pointList2 = new ArrayList<Point>(s2);

		for (int i = 0; i < s1; ++i)
		{
			Point point = p1.get(i);

			if (!validMap[point.i])
				pointList1.add(point);
		}

		for (int i = 0; i < s2; ++i)
		{
			Point point = p2.get(i);

			if ((point.x < _width - 1 && !validMap[point.i + 1])
				|| (point.y < _height - 1 && !validMap[point.i + _width])
				|| (point.x > 0 && !validMap[point.i - 1])
				|| (point.y > 0 && !validMap[point.i - _width]))
				pointList2.add(point);
		}

		SortPoints(pointList1, a, b);
		SortPoints(pointList2, b, a);

		return new Pair<ArrayList<Point>, ArrayList<Point>>(pointList1, pointList2);
	}

	private void MachindustryDispose()
	{
		// DO NOT EVEN THINK
		// Events.remove(DisposeEvent.class, _gameExitEventCons);
		Events.remove(WorldLoadEvent.class, _worldLoadEventCons);

		_taskQueue.ClearTasks();
		++_taskEpoch;

		if (_worldState != null)
			_worldState.close();

		try
		{
			_running = false;
			_thread.join();
		}
		catch (InterruptedException e) {}
	}

	private void MachindustryUpdate()
	{
		_height = Vars.world.height();
		_width = Vars.world.width();
		_size = _height * _width;

		final boolean polygonSZ = Core.settings.getBool(_polygonSafeZoneName);
		final float radiusSZ = (float)Core.settings.getInt(_radiusSafeZoneName);

		final long freq = (long)Core.settings.getInt(_frequencyName);
		final long time = (long)Core.settings.getInt(_buildTimeName) * (long)1000000;

		_taskQueue.ClearTasks();
		++_taskEpoch;

		if (_worldState != null)
			_worldState.close();

		_masksMap = new boolean[_size];
		_worldState = new WorldState(_height, _width, polygonSZ, radiusSZ, _worldUpdateRunnable, null);

		_beamPathFinder = new BeamPathFinder(_height, _width, freq, time);
		_liquidPathFinder = new LiquidPathFinder(_height, _width, freq, time);
		_solidPathFinder = new SolidPathFinder(_height, _width, freq, time);
	}

	private void TaskWorker()
	{
		while (_running)
		{
			if (Vars.state.isMenu())
				_taskQueue.ClearTasks();

			final long taskEpoch = _taskEpoch;
			final PathTask task = _taskQueue.RemoveTask();

			if (task == null)
			{
				try
				{
					Thread.sleep((long)1);
				}
				catch (InterruptedException e) {}
			}
			else
			{
				LinkedList<BuildPlan> buildPlans = null;

				try
				{
					switch (task.type)
					{
						case BEAM:
							buildPlans = FindPath(_beamPathFinder, _worldState, task.x1, task.y1, task.x2, task.y2, taskEpoch);
							break;
					
						case LIQUID:
							buildPlans = FindPath(_liquidPathFinder, _worldState, task.x1, task.y1, task.x2, task.y2, taskEpoch);
							break;
					
						case SOLID:
							buildPlans = FindPath(_solidPathFinder, _worldState, task.x1, task.y1, task.x2, task.y2, taskEpoch);
							break;

						default:
							break;
					}
				}
				catch (Exception e)
				{
					PrintLine
					(
						"Exception catched when working on task" + 
						" x1 = " + task.x1 +
						", y1 = " + task.y1 +
						", x2 = " + task.x2 +
						", y2 = " + task.y2 +
						", type = " + task.type +
						", epoch = " + taskEpoch +
						", global epoch = " + _taskEpoch +
						", exception = '" + e.getMessage() + "'"
					);

					e.printStackTrace();
				}

				if (buildPlans != null)
				{
					BuildPlan[] buildPlansArray = buildPlans.toArray(new BuildPlan[buildPlans.size()]);

					while (_running && !_worldState.BuildPlansMachinary.compareAndSet(null, buildPlansArray))
					{
						try
						{
							Thread.sleep((long)1);
						}
						catch (InterruptedException e) {}
					}
				}
			}
		}
	}

	private void WorldUpdateRunnable()
	{
	}

	public Machindustry()
	{
		super();

		Events.on(DisposeEvent.class, _gameExitEventCons);
		Events.on(WorldLoadEvent.class, _worldLoadEventCons);

		_thread = new Thread(() -> TaskWorker(), "Machindustry worker thread");
		_thread.setDaemon(true);
		_thread.start();
	}

	@Override
	public void init()
	{
		if (!Core.settings.getBool(_name))
		{
			Core.settings.put(_name, true);
		}

		Vars.ui.settings.getCategories().add(new SettingsCategory
		(
			Core.bundle.get("machindustry.title"),
			new TextureRegionDrawable(Core.atlas.find("machindustry-machindustry")),
			_settingsBuilder
		));
		Core.scene.addListener(_inputListener);
	}

	private class MachindustryInputListener extends InputListener
	{
		@Override
		public boolean keyDown(InputEvent event, KeyCode code)
		{
			if (DoHandle())
			{
				if (code == _beamPathFinderCode)
					_beamFirstPoint = GetCurrentPoint();
				else if (code == _liquidPathFinderCode)
					_liquidFirstPoint = GetCurrentPoint();
				else if (code == _solidPathFinderCode)
					_solidFirstPoint = GetCurrentPoint();
				else
					return false;

				return true;
			}
			else
				return false;
		}

		@Override
		public boolean keyUp(InputEvent event, KeyCode code)
		{
			if (DoHandle())
			{
				if (code == _beamPathFinderCode)
				{
					_beamLastPoint = GetCurrentPoint();
					_taskQueue.AddTask(new PathTask
					(
						_beamFirstPoint.x,
						_beamFirstPoint.y,
						_beamLastPoint.x,
						_beamLastPoint.y,
						PathType.BEAM
					));

					_beamFirstPoint = null;
					_beamLastPoint = null;
				}
				else if (code == _liquidPathFinderCode)
				{
					_liquidLastPoint = GetCurrentPoint();
					_taskQueue.AddTask(new PathTask
					(
						_liquidFirstPoint.x,
						_liquidFirstPoint.y,
						_liquidLastPoint.x,
						_liquidLastPoint.y,
						PathType.LIQUID
					));

					_liquidFirstPoint = null;
					_liquidLastPoint = null;
				}
				else if (code == _solidPathFinderCode)
				{
					_solidLastPoint = GetCurrentPoint();
					_taskQueue.AddTask(new PathTask
					(
						_solidFirstPoint.x,
						_solidFirstPoint.y,
						_solidLastPoint.x,
						_solidLastPoint.y,
						PathType.SOLID
					));

					_solidFirstPoint = null;
					_solidLastPoint = null;
				}
				else
					return false;

				return true;
			}
			else
				return false;
		}
	}

	private class Pair<A, B>
	{
		public final A a;
		public final B b;

		public Pair(A a, B b)
		{
			this.a = a;
			this.b = b;
		}
	};

	private static class SpaceSetting extends Setting
	{
		private final Color _color;

		private final float _top;
		private final float _left;
		private final float _bottom;
		private final float _right;

		public SpaceSetting(Color color, float top, float left, float bottom, float right)
		{
			super(null);

			_color = color;

			_top = top;
			_left = left;
			_bottom = bottom;
			_right = right;
		}

		@Override
		public void add(SettingsTable table)
		{
			table.image().growX().pad(_top, _left, _bottom, _right).color(_color);
			table.row();
		}
	}

	private static class TextSetting extends Setting
	{
		private final String _text;

		public TextSetting(String text)
		{
			super(null);

			_text = text;
		}

		@Override
		public void add(SettingsTable table)
		{
			final Label label = table.labelWrap(_text).fillX().get();
			label.setWrap(true);
			label.setAlignment(1);
			table.row();
		}
	}
}