package machindustry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.input.InputProcessor;
import arc.input.KeyCode;
import arc.math.Mat;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Http;
import arc.util.Scaling;
import arc.util.serialization.Jval;
import machindustry.PathTask.PathType;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.DisposeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.mod.Mod;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsCategory;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable.Setting;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.liquid.LiquidBlock;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.Attribute;
import mindustry.world.meta.BlockFlag;

public class Machindustry extends Mod
{
	private static final int RIGHT = 0;
	private static final int UPPER = 1;
	private static final int LEFT = 2;
	private static final int BOTTOM = 3;

	private static final Color _beamPointColor = new Color(1F, 0F, 0F, 0.5F);
	private static final Color _liquidPointColor = new Color(0F, 1F, 0F, 0.5F);
	private static final Color _solidPointColor = new Color(0F, 0F, 1F, 0.5F);
	private static final Color _turbinePointColor = new Color(1F, 1F, 1F, 1F);
	private static final Color _takeToTheTopPointColor = new Color(0F, 1F, 1F, 1F);

	private static final String _name = "machindustry";

	private static final String _buttonsName = "buttons";
	private static final String _previewName = "preview";

	private static final String _polygonSafeZoneName = "polygon-safe-zone";
	private static final String _radiusSafeZoneName = "radius-safe-zone";

	private static final String _buildAtmosphericConcentratorName = "build-atmospheric-concentrator";
	private static final String _buildBeamNodeName = "build-beam-node";
	private static final String _buildBuildTowerName = "build-build-tower";
	private static final String _buildElectricHeaterName = "build-electric-heater";
	private static final String _buildLiquidTransportName = "build-liquid-transport";

	private static final String _beamFrequencyName = "beam-time-check-frequency";
	private static final String _beamBuildTimeName = "beam-time-to-build-path";
	private static final String _beamBuildTotalTimeName = "beam-total-time-to-build-path";

	private static final String _beamMaskAroundBuildName = "beam-mask-around-build";
	private static final String _beamMaskAroundCoreName = "beam-mask-around-core";
	private static final String _beamMaskAroundLiquidName = "beam-mask-around-liquid";
	private static final String _beamMaskAroundSolidName = "beam-mask-around-solid";

	private static final String _beamIgnoreMaskName = "beam-path-ignore-mask";
	private static final String _beamTargetModeName = "beam-path-target-mode";

	private static final String _liquidFrequencyName = "liquid-time-check-frequency";
	private static final String _liquidBuildTimeName = "liquid-time-to-build-path";
	private static final String _liquidBuildTotalTimeName = "liquid-total-time-to-build-path";

	private static final String _liquidMaskAroundBuildName = "liquid-mask-around-build";
	private static final String _liquidMaskAroundCoreName = "liquid-mask-around-core";
	private static final String _liquidMaskAroundLiquidName = "liquid-mask-around-liquid";
	private static final String _liquidMaskAroundSolidName = "liquid-mask-around-solid";

	private static final String _liquidIgnoreMaskName = "liquid-path-ignore-mask";
	private static final String _liquidTargetModeName = "liquid-path-target-mode";
	private static final String _liquidReplaceOneName = "liquid-replace-one";

	private static final String _solidFrequencyName = "solid-time-check-frequency";
	private static final String _solidBuildTimeName = "solid-time-to-build-path";
	private static final String _solidBuildTotalTimeName = "solid-total-time-to-build-path";

	private static final String _solidMaskAroundBuildName = "solid-mask-around-build";
	private static final String _solidMaskAroundCoreName = "solid-mask-around-core";
	private static final String _solidMaskAroundLiquidName = "solid-mask-around-liquid";
	private static final String _solidMaskAroundSolidName = "solid-mask-around-solid";

	private static final String _solidIgnoreMaskName = "solid-path-ignore-mask";
	private static final String _solidTargetModeName = "solid-path-target-mode";
	private static final String _solidDisableSorterName = "solid-disable-sorter";
	private static final String _solidReplaceOneName = "solid-replace-one";
	private static final String _solidReplaceWithName = "solid-replace-with";

	private final InputProcessor _inputListener = new MachindustryInputProcessor();

	private final Cons<Table> _buttonsBuilder = t -> BuildButtonsTable(t);
	private final Cons<SettingsTable> _settingsBuilder = t -> BuildSettingsTable(t);

	private final Cons<DisposeEvent> _gameExitEventCons = e -> MachindustryDispose();
	private final Cons<WorldLoadEvent> _worldLoadEventCons = e -> MachindustryUpdate();

	private final Runnable _worldDrawRunnable = () -> WorldDrawRunnable();
	private final Runnable _worldUpdateRunnable = () -> WorldUpdateRunnable();

	private final QueueSPSC<PathTask> _taskQueue = new QueueSPSC<PathTask>(100);
	private final Thread _thread;

	private String _failureMessage = null;
	private String _successMessage = null;

	private String _resultMessage1 = null;
	private String _resultMessage2 = null;
	private String _resultMessage3 = null;
	private String _resultMessage4 = null;

	private boolean _touch = false;

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

	private KeyCode _beamPathFinderCode = KeyCode.i;
	private KeyCode _liquidPathFinderCode = KeyCode.u;
	private KeyCode _solidPathFinderCode = KeyCode.y;
	private KeyCode _takeToTheTopCode = KeyCode.k;
	private KeyCode _ventBuilderCode = KeyCode.o;

	private int _height = -1;
	private int _width = -1;
	private int _size = -1;

	private boolean[] _buildMap = null;
	private boolean[] _masksMap = null;
	private WorldState _worldState = null;

	private BeamPathFinder _beamPathFinder = null;
	private LiquidPathFinder _liquidPathFinder = null;
	private SolidPathFinder _solidPathFinder = null;

	private boolean _beamButton = false;
	private boolean _liquidButton = false;
	private boolean _solidButton = false;
	private boolean _takeButton = false;
	private boolean _ventButton = false;

	private Point _beamFirstPoint = null;
	private Point _liquidFirstPoint = null;
	private Point _solidFirstPoint = null;
	private Point _takeFirstPoint = null;
	private Point _ventFirstPoint = null;

	private Point _beamLastPoint = null;
	private Point _liquidLastPoint = null;
	private Point _solidLastPoint = null;
	private Point _takeLastPoint = null;
	private Point _ventLastPoint = null;

	private boolean _resultFailure = false;
	private boolean _resultSuccess = false;

	private long _resultTimeAlgorithm = -1;
	private long _resultTimeTotal = -1;

	private static void CheckUpdates()
	{
		try
		{
			Http.get(Vars.ghApi + "/repos/urij19cc7e4/Machindustry/releases/latest", result ->
			{
				if (!Jval.read(result.getResult()).getString("tag_name").replaceAll("[^0-9.]", "").equalsIgnoreCase
					(Vars.mods.getMod(Machindustry.class).meta.version.replaceAll("[^0-9.]", "")))
					Vars.ui.showInfo(Core.bundle.get("machindustry.update-available"));
			});
		}
		catch (Exception e)
		{
			PrintLine("Exception catched when checking updates: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private static void DisableRouterSorter(final int x, final int y)
	{
		if (Core.settings.getBool(_solidDisableSorterName))
		{
			final Team team = Vars.player.team();
			final Tiles tiles = Vars.world.tiles;

			if (team == null)
				throw new NullPointerException("Vars.player.team is null");

			if (tiles == null)
				throw new NullPointerException("Vars.world.tiles is null");

			BuildPlan buildPlan = GetPlanIntersection(Vars.player.unit().plans, x, y);

			if (buildPlan == null)
				buildPlan = GetPlanIntersection(Vars.control.input.selectPlans, x, y);

			final Tile tile = tiles.get(x, y);
			final Block block = buildPlan == null ? tile.block() : buildPlan.block;

			if (block == Blocks.ductRouter)
			{
				if (buildPlan == null)
				{
					final Building build = tile.build;

					if (build != null && build.team == team)
						build.configure(null);
				}
				else
					buildPlan.config = null;
			}
		}
	}

	private static boolean DoHandle()
	{
		return !Core.scene.hasKeyboard()
			&& !Vars.control.input.commandMode
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

	private static BuildPlan GetPlanIntersection(final BuildPlan[] buildPlans, final int x, final int y)
	{
		for (final BuildPlan buildPlan : buildPlans)
		{
			final Block block = buildPlan.block;

			final int x1 = buildPlan.x + block.sizeOffset;
			final int x2 = x1 + block.size - 1;

			final int y1 = buildPlan.y + block.sizeOffset;
			final int y2 = y1 + block.size - 1;

			if (x1 <= x && x <= x2 && y1 <= y && y <= y2)
				return buildPlan;
		}

		return null;
	}

	private static BuildPlan GetPlanIntersection(final Queue<BuildPlan> buildPlans, final int x, final int y)
	{
		for (final BuildPlan buildPlan : buildPlans)
		{
			final Block block = buildPlan.block;

			final int x1 = buildPlan.x + block.sizeOffset;
			final int x2 = x1 + block.size - 1;

			final int y1 = buildPlan.y + block.sizeOffset;
			final int y2 = y1 + block.size - 1;

			if (x1 <= x && x <= x2 && y1 <= y && y <= y2)
				return buildPlan;
		}

		return null;
	}

	private static BuildPlan GetPlanIntersection(final Seq<BuildPlan> buildPlans, final int x, final int y)
	{
		for (final BuildPlan buildPlan : buildPlans)
		{
			final Block block = buildPlan.block;

			final int x1 = buildPlan.x + block.sizeOffset;
			final int x2 = x1 + block.size - 1;

			final int y1 = buildPlan.y + block.sizeOffset;
			final int y2 = y1 + block.size - 1;

			if (x1 <= x && x <= x2 && y1 <= y && y <= y2)
				return buildPlan;
		}

		return null;
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
			return RIGHT;
		else if (tileY == buildY + block.sizeOffset + block.size)
			return UPPER;
		else if (tileX == buildX + block.sizeOffset - 1)
			return LEFT;
		else if (tileY == buildY + block.sizeOffset - 1)
			return BOTTOM;
		else
			return -1;
	}

	private static int NotRotate(final int rotate)
	{
		switch (rotate)
		{
			case RIGHT:
				return LEFT;

			case UPPER:
				return BOTTOM;

			case LEFT:
				return RIGHT;

			case BOTTOM:
				return UPPER;

			default:
				return -1;
		}
	}

	private static void PrintLine(final String x)
	{
		System.out.println("[Machindustry] " + x);
	}

	private static void ReplaceLiquid
	(
		final BuildPlan[] playerBuildPlans,
		final LinkedList<BuildPlan> buildPlans,
		final int x1,
		final int y1,
		final int x,
		final int y
	)
	{
		final Team team = Vars.player.team();
		final Tiles tiles = Vars.world.tiles;

		if (team == null)
			throw new NullPointerException("Vars.player.team is null");

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		final BuildPlan buildPlan = GetPlanIntersection(playerBuildPlans, x, y);

		final Tile tile = tiles.get(x, y);
		final Block block = buildPlan == null ? tile.block() : buildPlan.block;
		final Building build = tile.build;

		final int rotation = buildPlan == null ? (build == null ? -1 : build.rotation) : buildPlan.rotation;

		if ((buildPlan != null || (build != null && build.team == team)) && block == Blocks.reinforcedConduit)
			buildPlans.addLast(new BuildPlan(x, y, rotation, Blocks.reinforcedLiquidRouter));
	}

	private static void ReplaceSolid
	(
		final BuildPlan[] playerBuildPlans,
		final LinkedList<BuildPlan> buildPlans,
		final int x1,
		final int y1,
		final int x,
		final int y
	)
	{
		final Team team = Vars.player.team();
		final Tiles tiles = Vars.world.tiles;

		if (team == null)
			throw new NullPointerException("Vars.player.team is null");

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		final BuildPlan buildPlan = GetPlanIntersection(playerBuildPlans, x, y);

		final Tile tile = tiles.get(x, y);
		final Block block = buildPlan == null ? tile.block() : buildPlan.block;
		final Building build = tile.build;

		final int rotation = buildPlan == null ? (build == null ? -1 : build.rotation) : buildPlan.rotation;

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

		if ((buildPlan != null || (build != null && build.team == team)) && (block == Blocks.armoredDuct || block == Blocks.duct))
			buildPlans.addLast(new BuildPlan(x, y, rotation, replaceWithBlock));
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

	private static void Tutorial()
	{
		final float padI = 100F;
		final float padT = 25F;

		final Scaling scaling = Scaling.fit;
		final float width = Core.scene.getWidth() * 0.9F;

		final BaseDialog baseDialog = new BaseDialog(Core.bundle.get("machindustry.welcome-title"));
		final Table table = new Table();

		table.labelWrap(Core.bundle.get("machindustry.welcome-text")).maxWidth(Core.scene.getWidth() * 0.5F).fillX().get().setAlignment(1);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-01-turbine-stage-1"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-01-turbine-stage-2"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-01-turbine-stage-3"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-02-beryllium-stage-1"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-02-beryllium-stage-2"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-03-electrolyzer-stage-1"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-03-electrolyzer-stage-2"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-04-hydrogen-stage-1"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-04-hydrogen-stage-2"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-05-beryllium-electrolyzer-hydrogen-stage-3"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-06-graphite-stage-1"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-06-graphite-stage-2"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-07-hydrogen-stage-1"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-07-hydrogen-stage-2"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-08-graphite-hydrogen-stage-3"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-09-graphite-stage-1"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-09-graphite-stage-2"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-10-silicon-stage-1"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-10-silicon-stage-2"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-11-graphite-silicon-stage-3"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-12-turbine-stage-1"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-12-turbine-stage-2"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-12-turbine-stage-3"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-12-turbine-stage-4"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		table.image(new TextureRegionDrawable(Core.atlas.find("machindustry-12-turbine-stage-5"))).maxWidth(width).scaling(scaling).padTop(padI).get().setWidth(width);
		table.row();

		ScrollPane scrollPane = new ScrollPane(table);
		baseDialog.cont.top().add(scrollPane).growX().pad(padT, padT, padT, padT);

		baseDialog.addCloseButton();
		baseDialog.show();
	}

	private void BuildButtonsTable(Table machindustryButtonsTable)
	{
		machindustryButtonsTable.name = "machindustry-buttons";
		machindustryButtonsTable.right().bottom();
		machindustryButtonsTable.visible(() -> DoHandle());

		machindustryButtonsTable.table(Tex.buttonEdge2, table ->
		{
			table.defaults().size(50F).right().bottom();
			table.button(Icon.distribution, Styles.clearNonei, () -> HandleSolidButton()).row();
			table.button(Icon.liquid, Styles.clearNonei, () -> HandleLiquidButton()).row();
			table.button(Icon.power, Styles.clearNonei, () -> HandleBeamButton()).row();
			table.button(Icon.star, Styles.clearNonei, () -> HandleTakeButton()).row();
			table.button(Icon.map, Styles.clearNonei, () -> HandleVentButton()).row();
		}).update(table ->
		{
			Element mindustryButtonsTable = Vars.ui.hudGroup.find("inputTable");

			if (mindustryButtonsTable != null)
			{
				mindustryButtonsTable = mindustryButtonsTable.parent.parent.parent;
				table.setTranslation(Scl.scl(4F) - mindustryButtonsTable.getWidth(), 0F);
			}
		}).height(254F);
	}

	private void BuildSettingsTable(SettingsTable mindustrySettingsTable)
	{
		Setting visibleSpace = new SpaceSetting(Color.gold, 25F, 0F, 25F, 0F);
		Setting invisibleSpace = new SpaceSetting(Color.clear, 10F, 0F, 10F, 0F);

		SettingsTable machindustrySettingsTable = new SettingsTable();

		machindustrySettingsTable.pref(visibleSpace);
		machindustrySettingsTable.pref(new TextSetting(Core.bundle.get("machindustry.settings-title")));

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.checkPref(_buttonsName, Vars.mobile);
		machindustrySettingsTable.checkPref(_previewName, Vars.mobile);

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.checkPref(_polygonSafeZoneName, true);
		machindustrySettingsTable.sliderPref(_radiusSafeZoneName, 1, 0, 9, 1, v -> Integer.toString(v));

		machindustrySettingsTable.pref(visibleSpace);
		machindustrySettingsTable.pref(new TextSetting(Core.bundle.get("machindustry.turbine-title")));

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.textPref("turbine-key", _ventBuilderCode.name().toUpperCase(), v ->
		{
			KeyCode code = Arrays.stream(KeyCode.values()).filter(k -> k.value.equalsIgnoreCase(v)).findFirst().orElse(null);
			Core.settings.put("turbine-key", code == null ? _ventBuilderCode.name().toUpperCase() : code.name().toUpperCase());
		});

		machindustrySettingsTable.pref(visibleSpace);
		machindustrySettingsTable.pref(new TextSetting(Core.bundle.get("machindustry.build-title")));

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.textPref("build-key", _takeToTheTopCode.name().toUpperCase(), v ->
		{
			KeyCode code = Arrays.stream(KeyCode.values()).filter(k -> k.value.equalsIgnoreCase(v)).findFirst().orElse(null);
			Core.settings.put("build-key", code == null ? _takeToTheTopCode.name().toUpperCase() : code.name().toUpperCase());
		});

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.checkPref(_buildAtmosphericConcentratorName, false);
		machindustrySettingsTable.checkPref(_buildBeamNodeName, false);
		machindustrySettingsTable.checkPref(_buildBuildTowerName, false);
		machindustrySettingsTable.checkPref(_buildElectricHeaterName, false);
		machindustrySettingsTable.checkPref(_buildLiquidTransportName, false);

		machindustrySettingsTable.pref(visibleSpace);
		machindustrySettingsTable.pref(new TextSetting(Core.bundle.get("machindustry.beam-title")));

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.textPref("beam-key", _beamPathFinderCode.name().toUpperCase(), v ->
		{
			KeyCode code = Arrays.stream(KeyCode.values()).filter(k -> k.value.equalsIgnoreCase(v)).findFirst().orElse(null);
			Core.settings.put("beam-key", code == null ? _beamPathFinderCode.name().toUpperCase() : code.name().toUpperCase());
		});

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.sliderPref(_beamFrequencyName, 1000, 100, 10000, 100, v -> Integer.toString(v));
		machindustrySettingsTable.sliderPref(_beamBuildTimeName, Vars.mobile ? 50 : 20, 2, 200, 2, v -> Integer.toString(v));
		machindustrySettingsTable.sliderPref(_beamBuildTotalTimeName, Vars.mobile ? 500 : 200, 20, 2000, 20, v -> Integer.toString(v));

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.checkPref(_beamMaskAroundBuildName, false);
		machindustrySettingsTable.checkPref(_beamMaskAroundCoreName, true);
		machindustrySettingsTable.checkPref(_beamMaskAroundLiquidName, false);
		machindustrySettingsTable.checkPref(_beamMaskAroundSolidName, false);
		machindustrySettingsTable.checkPref(_beamIgnoreMaskName, true);

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.checkPref(_beamTargetModeName, true);

		machindustrySettingsTable.pref(visibleSpace);
		machindustrySettingsTable.pref(new TextSetting(Core.bundle.get("machindustry.liquid-title")));

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.textPref("liquid-key", _liquidPathFinderCode.name().toUpperCase(), v ->
		{
			KeyCode code = Arrays.stream(KeyCode.values()).filter(k -> k.value.equalsIgnoreCase(v)).findFirst().orElse(null);
			Core.settings.put("liquid-key", code == null ? _liquidPathFinderCode.name().toUpperCase() : code.name().toUpperCase());
		});

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.sliderPref(_liquidFrequencyName, 1000, 100, 10000, 100, v -> Integer.toString(v));
		machindustrySettingsTable.sliderPref(_liquidBuildTimeName, Vars.mobile ? 250 : 100, 10, 1000, 10, v -> Integer.toString(v));
		machindustrySettingsTable.sliderPref(_liquidBuildTotalTimeName, Vars.mobile ? 2500 : 1000, 100, 10000, 100, v -> Integer.toString(v));

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.checkPref(_liquidMaskAroundBuildName, false);
		machindustrySettingsTable.checkPref(_liquidMaskAroundCoreName, true);
		machindustrySettingsTable.checkPref(_liquidMaskAroundLiquidName, false);
		machindustrySettingsTable.checkPref(_liquidMaskAroundSolidName, false);
		machindustrySettingsTable.checkPref(_liquidIgnoreMaskName, true);

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.checkPref(_liquidTargetModeName, true);
		machindustrySettingsTable.checkPref(_liquidReplaceOneName, true);

		machindustrySettingsTable.pref(visibleSpace);
		machindustrySettingsTable.pref(new TextSetting(Core.bundle.get("machindustry.solid-title")));

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.textPref("solid-key", _solidPathFinderCode.name().toUpperCase(), v ->
		{
			KeyCode code = Arrays.stream(KeyCode.values()).filter(k -> k.value.equalsIgnoreCase(v)).findFirst().orElse(null);
			Core.settings.put("solid-key", code == null ? _solidPathFinderCode.name().toUpperCase() : code.name().toUpperCase());
		});

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.sliderPref(_solidFrequencyName, 1000, 100, 10000, 100, v -> Integer.toString(v));
		machindustrySettingsTable.sliderPref(_solidBuildTimeName, Vars.mobile ? 250 : 100, 10, 1000, 10, v -> Integer.toString(v));
		machindustrySettingsTable.sliderPref(_solidBuildTotalTimeName, Vars.mobile ? 2500 : 1000, 100, 10000, 100, v -> Integer.toString(v));

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.checkPref(_solidMaskAroundBuildName, false);
		machindustrySettingsTable.checkPref(_solidMaskAroundCoreName, true);
		machindustrySettingsTable.checkPref(_solidMaskAroundLiquidName, false);
		machindustrySettingsTable.checkPref(_solidMaskAroundSolidName, false);
		machindustrySettingsTable.checkPref(_solidIgnoreMaskName, true);

		machindustrySettingsTable.pref(invisibleSpace);
		machindustrySettingsTable.checkPref(_solidTargetModeName, true);
		machindustrySettingsTable.checkPref(_solidDisableSorterName, false);
		machindustrySettingsTable.checkPref(_solidReplaceOneName, true);
		machindustrySettingsTable.sliderPref(_solidReplaceWithName, 0, 0, 2, 1, v -> Core.bundle.get("machindustry.solid-replace-with-" + GetReplacerSolidName(v)));

		machindustrySettingsTable.pref(visibleSpace);
		mindustrySettingsTable.add(machindustrySettingsTable);
	}

	private Point CanOverridePoint(final WorldState worldState, final int x, final int y, final int r)
	{
		final Team team = Vars.player.team();
		final Tiles tiles = Vars.world.tiles;

		if (team == null)
			throw new NullPointerException("Vars.player.team is null");

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		int overrideX = -1;
		int overrideY = -1;

		switch (r)
		{
			case RIGHT:
				overrideX = x + 1;
				overrideY = y;
				break;

			case UPPER:
				overrideX = x;
				overrideY = y + 1;
				break;

			case LEFT:
				overrideX = x - 1;
				overrideY = y;
				break;

			case BOTTOM:
				overrideX = x;
				overrideY = y - 1;
				break;

			default:
				break;
		}

		if (overrideX >= 0 && overrideX < _width && overrideY >= 0 && overrideY < _height)
		{
			final BuildPlan aBuildPlan = GetPlanIntersection(worldState.BuildPlans, overrideX, overrideY);
			final int overrideI = overrideX + overrideY * _width;

			if (aBuildPlan == null && !worldState.Map[overrideI])
				return new Point(overrideX, overrideY, overrideI);
		}

		return new Point(-1, -1, -1);
	}

	private void DrawRectangleOverlay(final int x1, final int y1, final int x2, final int y2, final Color color)
	{
		final float tilesize = (float)Vars.tilesize;

		final float offset = tilesize * 0.5F;
		final float width = tilesize * 0.25F;

		final float worldX1 = (float)x1 * tilesize;
		final float worldY1 = (float)y1 * tilesize;
		final float worldX2 = (float)x2 * tilesize;
		final float worldY2 = (float)y2 * tilesize;

		final float w = Math.abs(worldX1 - worldX2);
		final float h = Math.abs(worldY1 - worldY2);
		final float x = Math.min(worldX1, worldX2) + w * 0.5F;
		final float y = Math.min(worldY1, worldY2) + h * 0.5F;

		Draw.color(color.r, color.g, color.b, color.a);
		Fill.rect(x, y - offset - (h - width) * 0.5F, w + tilesize, width);
		Fill.rect(x, y + offset + (h - width) * 0.5F, w + tilesize, width);
		Fill.rect(x - offset - (w - width) * 0.5F, y, width, h + tilesize - width * 2F);
		Fill.rect(x + offset + (w - width) * 0.5F, y, width, h + tilesize - width * 2F);
		Draw.reset();
	}

	private void DrawSquareOverlay(final int x, final int y, final Color color)
	{
		final float tilesize = (float)Vars.tilesize;

		final float worldX = (float)x * tilesize;
		final float worldY = (float)y * tilesize;

		Draw.color(color.r, color.g, color.b, color.a);
		Fill.rect(worldX, worldY, tilesize, tilesize);
		Draw.reset();
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
					{
						final Tile tile = tiles.geti(i);
						final Block block = tile.block();
						final Building build = tile.build;

						if (build != null && build.team == team && build.tile == tile)
						{
							final boolean c = block instanceof CoreBlock;
							final boolean l = block instanceof LiquidBlock;
							final boolean s = block.isDuct;
							final boolean b = !(c || l || s) && (block.hasItems || block.hasLiquids);

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

				final long aStartTime = System.nanoTime();
				final LinkedList<BuildPlan> buildPlans = function.apply
				(
					new Pair<Point, Point>(pointList1.get(k % size1), pointList2.get(k % size2))
				);
				final long aEndTime = System.nanoTime();

				if (buildPlans != null)
				{
					_resultTimeAlgorithm = (aEndTime - aStartTime) / (long)1000000;
					return buildPlans;
				}
			}

			for (int i = 0; i < size1; ++i)
				for (int j = 0; j < size2; ++j)
					if (i % size2 != j % size1)
					{
						if (Expired(endTime, taskEpoch))
							return null;

						final long aStartTime = System.nanoTime();
						final LinkedList<BuildPlan> buildPlans = function.apply
						(
							new Pair<Point, Point>(pointList1.get(i), pointList2.get(j))
						);
						final long aEndTime = System.nanoTime();

						if (buildPlans != null)
						{
							_resultTimeAlgorithm = (aEndTime - aStartTime) / (long)1000000;
							return buildPlans;
						}
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
		final long endTime = System.nanoTime() + (long)Core.settings.getInt(_beamBuildTotalTimeName) * (long)1000000;

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

		long aStartTime = System.nanoTime();
		LinkedList<BuildPlan> buildPlans = pathFinder.BuildPath(x1, y1, x2, y2, targetMode, _masksMap);
		long aEndTime = System.nanoTime();

		if (buildPlans == null && ignoreMask)
		{
			if (Expired(endTime, taskEpoch))
				return null;

			aStartTime = System.nanoTime();
			buildPlans = pathFinder.BuildPath(x1, y1, x2, y2, targetMode, null);
			aEndTime = System.nanoTime();
		}

		if (buildPlans != null)
			_resultTimeAlgorithm = (aEndTime - aStartTime) / (long)1000000;

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
		final Team team = Vars.player.team();
		final Tiles tiles = Vars.world.tiles;

		if (team == null)
			throw new NullPointerException("Vars.player.team is null");

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		final long endTime = System.nanoTime() + (long)Core.settings.getInt(_liquidBuildTotalTimeName) * (long)1000000;

		final boolean ignoreMask = Core.settings.getBool(_liquidIgnoreMaskName);
		final boolean targetMode = Core.settings.getBool(_liquidTargetModeName);

		worldState.UpdateMap();

		if (Expired(endTime, taskEpoch))
			return null;

		final BuildPlan buildPlan2 = GetPlanIntersection(worldState.BuildPlans, x2, y2);

		final Tile tile2 = tiles.get(x2, y2);
		final Block block2 = buildPlan2 == null ? tile2.block() : buildPlan2.block;

		if (block2 == Blocks.reinforcedConduit || block2 == Blocks.reinforcedBridgeConduit)
		{
			final boolean[] wsMap = worldState.Map;
			final int i2 = x2 + y2 * _width;

			int rotation = -1;

			if (buildPlan2 == null)
			{
				final Building build = tile2.build;

				if (build != null && build.team == team)
					rotation = build.rotation;
			}
			else
				rotation = buildPlan2.rotation;

			switch (rotation)
			{
				case RIGHT:
					if (x2 + 1 < _width)
						wsMap[i2 + 1] = true;
					break;

				case UPPER:
					if (y2 + 1 < _height)
						wsMap[i2 + _width] = true;
					break;

				case LEFT:
					if (x2 > 0)
						wsMap[i2 - 1] = true;
					break;

				case BOTTOM:
					if (y2 > 0)
						wsMap[i2 - _width] = true;
					break;

				default:
					break;
			}
		}

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
		final boolean[] masks = new boolean[5];

		final BuildPlan buildPlan1 = GetPlanIntersection(worldState.BuildPlans, x1, y1);

		final Tile tile1 = tiles.get(x1, y1);
		final Block block1 = buildPlan1 == null ? tile1.block() : buildPlan1.block;

		final boolean replace = Core.settings.getBool(_liquidReplaceOneName);

		if ((block1 == Blocks.reinforcedConduit && !replace) || block1 == Blocks.reinforcedBridgeConduit)
		{
			int rotation = -1;

			if (buildPlan1 == null)
			{
				final Building build = tile1.build;

				if (build != null && build.team == team)
					rotation = build.rotation;
			}
			else
				rotation = buildPlan1.rotation;

			switch (rotation)
			{
				case RIGHT:
					pair.a.removeIf(point -> (point.x == x1 && point.y == y1 + 1));
					pair.a.removeIf(point -> (point.x == x1 - 1 && point.y == y1));
					pair.a.removeIf(point -> (point.x == x1 && point.y == y1 - 1));
					break;

				case UPPER:
					pair.a.removeIf(point -> (point.x == x1 + 1 && point.y == y1));
					pair.a.removeIf(point -> (point.x == x1 - 1 && point.y == y1));
					pair.a.removeIf(point -> (point.x == x1 && point.y == y1 - 1));
					break;

				case LEFT:
					pair.a.removeIf(point -> (point.x == x1 + 1 && point.y == y1));
					pair.a.removeIf(point -> (point.x == x1 && point.y == y1 + 1));
					pair.a.removeIf(point -> (point.x == x1 && point.y == y1 - 1));
					break;

				case BOTTOM:
					pair.a.removeIf(point -> (point.x == x1 + 1 && point.y == y1));
					pair.a.removeIf(point -> (point.x == x1 && point.y == y1 + 1));
					pair.a.removeIf(point -> (point.x == x1 - 1 && point.y == y1));
					break;

				default:
					break;
			}
		}

		int zOverrideX = -1;
		int zOverrideY = -1;

		if (block1 == Blocks.reinforcedConduit || block1 == Blocks.reinforcedBridgeConduit)
		{
			int rotation = -1;

			if (buildPlan1 == null)
			{
				final Building build = tile1.build;

				if (build != null && build.team == team)
					rotation = build.rotation;
			}
			else
				rotation = buildPlan1.rotation;

			if (rotation != -1)
			{
				final Point override = CanOverridePoint(worldState, x1, y1, rotation);

				zOverrideX = override.x;
				zOverrideY = override.y;
			}
		}

		final int overrideX = zOverrideX;
		final int overrideY = zOverrideY;

		final AtomicReference<Point> firstPoint = new AtomicReference<Point>();

		LinkedList<BuildPlan> buildPlans = FindPath
		(
			(p) ->
			{
				firstPoint.set(p.a);

				int aOverrideX = -1;
				int aOverrideY = -1;

				if (overrideX == p.a.x && overrideY == p.a.y)
				{
					aOverrideX = overrideX;
					aOverrideY = overrideY;
				}

				MaskPoints(masks, true, p.a, p.b);
				LinkedList<BuildPlan> path = pathFinder.BuildPath
				(
					p.a.x,
					p.a.y,
					p.b.x,
					p.b.y,
					aOverrideX,
					aOverrideY,
					GetRotate(x1, y1, p.a.x, p.a.y),
					targetMode,
					_masksMap
				);
				MaskPoints(masks, false, p.a, p.b);
				return path;
			},
			pair.a,
			pair.b,
			endTime,
			taskEpoch
		);

		if (buildPlans == null && ignoreMask && !Expired(endTime, taskEpoch))
			buildPlans = FindPath
			(
				(p) ->
				{
					firstPoint.set(p.a);

					int aOverrideX = -1;
					int aOverrideY = -1;

					if (overrideX == p.a.x && overrideY == p.a.y)
					{
						aOverrideX = overrideX;
						aOverrideY = overrideY;
					}

					return pathFinder.BuildPath
					(
						p.a.x,
						p.a.y,
						p.b.x,
						p.b.y,
						aOverrideX,
						aOverrideY,
						GetRotate(x1, y1, p.a.x, p.a.y),
						targetMode,
						null
					);
				},
				pair.a,
				pair.b,
				endTime,
				taskEpoch
			);

		if (buildPlans != null)
		{
			final Point point = firstPoint.get();

			if (replace)
				ReplaceLiquid(worldState.BuildPlans, buildPlans, point.x, point.y, x1, y1);

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
		final Team team = Vars.player.team();
		final Tiles tiles = Vars.world.tiles;

		if (team == null)
			throw new NullPointerException("Vars.player.team is null");

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		final long endTime = System.nanoTime() + (long)Core.settings.getInt(_solidBuildTotalTimeName) * (long)1000000;

		final boolean ignoreMask = Core.settings.getBool(_solidIgnoreMaskName);
		final boolean targetMode = Core.settings.getBool(_solidTargetModeName);

		worldState.UpdateMap();

		if (Expired(endTime, taskEpoch))
			return null;

		final BuildPlan buildPlan2 = GetPlanIntersection(worldState.BuildPlans, x2, y2);

		final Tile tile2 = tiles.get(x2, y2);
		final Block block2 = buildPlan2 == null ? tile2.block() : buildPlan2.block;

		final boolean isDuct2 = block2.isDuct || block2 == Blocks.surgeConveyor;
		final boolean isRouter2 = block2 == Blocks.surgeRouter || block2 == Blocks.ductRouter
			|| block2 == Blocks.overflowDuct || block2 == Blocks.underflowDuct || block2 == Blocks.ductUnloader;

		if (isDuct2 || isRouter2)
		{
			final boolean[] wsMap = worldState.Map;
			final int i2 = x2 + y2 * _width;

			final boolean right = x2 + 1 < _width;
			final boolean upper = y2 + 1 < _height;
			final boolean left = x2 > 0;
			final boolean bottom = y2 > 0;

			final int i2_right = i2 + 1;
			final int i2_upper = i2 + _width;
			final int i2_left = i2 - 1;
			final int i2_bottom = i2 - _width;

			int rotation = -1;

			if (buildPlan2 == null)
			{
				final Building build = tile2.build;

				if (build != null && build.team == team)
					rotation = build.rotation;
			}
			else
				rotation = buildPlan2.rotation;

			if (isDuct2)
				switch (rotation)
				{
					case RIGHT:
						if (right)
							wsMap[i2_right] = true;
						break;

					case UPPER:
						if (upper)
							wsMap[i2_upper] = true;
						break;

					case LEFT:
						if (left)
							wsMap[i2_left] = true;
						break;

					case BOTTOM:
						if (bottom)
							wsMap[i2_bottom] = true;
						break;

					default:
						break;
				}

			if (isRouter2)
				switch (rotation)
				{
					case RIGHT:
						if (right)
							wsMap[i2_right] = true;
						if (upper)
							wsMap[i2_upper] = true;
						if (bottom)
							wsMap[i2_bottom] = true;
						break;

					case UPPER:
						if (right)
							wsMap[i2_right] = true;
						if (upper)
							wsMap[i2_upper] = true;
						if (left)
							wsMap[i2_left] = true;
						break;

					case LEFT:
						if (upper)
							wsMap[i2_upper] = true;
						if (left)
							wsMap[i2_left] = true;
						if (bottom)
							wsMap[i2_bottom] = true;
						break;

					case BOTTOM:
						if (right)
							wsMap[i2_right] = true;
						if (left)
							wsMap[i2_left] = true;
						if (bottom)
							wsMap[i2_bottom] = true;
						break;

					default:
						break;
				}
		}

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
		final boolean[] masks = new boolean[5];

		final BuildPlan buildPlan1 = GetPlanIntersection(worldState.BuildPlans, x1, y1);

		final Tile tile1 = tiles.get(x1, y1);
		final Block block1 = buildPlan1 == null ? tile1.block() : buildPlan1.block;

		final boolean replace = Core.settings.getBool(_solidReplaceOneName);

		final boolean isDuct1 = ((block1 == Blocks.armoredDuct || block1 == Blocks.duct) && !replace) || block1 == Blocks.surgeConveyor
			|| block1 == Blocks.ductBridge || block1 == Blocks.ductUnloader;
		final boolean isRouter1 = ((block1 == Blocks.armoredDuct || block1 == Blocks.duct) && replace) || block1 == Blocks.surgeRouter
			|| block1 == Blocks.ductRouter || block1 == Blocks.overflowDuct || block1 == Blocks.underflowDuct;

		if (isDuct1 || isRouter1)
		{
			Object config = null;
			int rotation = -1;

			if (buildPlan1 == null)
			{
				final Building build = tile1.build;

				if (build != null && build.team == team)
				{
					config = build.config();
					rotation = build.rotation;
				}
			}
			else
			{
				config = buildPlan1.config;
				rotation = buildPlan1.rotation;
			}

			final boolean isSorter1 = !Core.settings.getBool(_solidDisableSorterName) && block1 == Blocks.ductRouter && config != null;

			if (isDuct1 || isSorter1)
				switch (rotation)
				{
					case RIGHT:
						pair.a.removeIf(point -> (point.x == x1 && point.y == y1 + 1));
						pair.a.removeIf(point -> (point.x == x1 - 1 && point.y == y1));
						pair.a.removeIf(point -> (point.x == x1 && point.y == y1 - 1));
						break;

					case UPPER:
						pair.a.removeIf(point -> (point.x == x1 + 1 && point.y == y1));
						pair.a.removeIf(point -> (point.x == x1 - 1 && point.y == y1));
						pair.a.removeIf(point -> (point.x == x1 && point.y == y1 - 1));
						break;

					case LEFT:
						pair.a.removeIf(point -> (point.x == x1 + 1 && point.y == y1));
						pair.a.removeIf(point -> (point.x == x1 && point.y == y1 + 1));
						pair.a.removeIf(point -> (point.x == x1 && point.y == y1 - 1));
						break;

					case BOTTOM:
						pair.a.removeIf(point -> (point.x == x1 + 1 && point.y == y1));
						pair.a.removeIf(point -> (point.x == x1 && point.y == y1 + 1));
						pair.a.removeIf(point -> (point.x == x1 - 1 && point.y == y1));
						break;

					default:
						break;
				}

			if (isRouter1 && !isSorter1)
				switch (rotation)
				{
					case RIGHT:
						pair.a.removeIf(point -> (point.x == x1 - 1 && point.y == y1));
						break;

					case UPPER:
						pair.a.removeIf(point -> (point.x == x1 && point.y == y1 - 1));
						break;

					case LEFT:
						pair.a.removeIf(point -> (point.x == x1 + 1 && point.y == y1));
						break;

					case BOTTOM:
						pair.a.removeIf(point -> (point.x == x1 && point.y == y1 + 1));
						break;

					default:
						break;
				}
		}

		int zOverrideX = -1;
		int zOverrideY = -1;

		if (block1.isDuct)
		{
			int rotation = -1;

			if (buildPlan1 == null)
			{
				final Building build = tile1.build;

				if (build != null && build.team == team)
					rotation = build.rotation;
			}
			else
				rotation = buildPlan1.rotation;

			if (rotation != -1)
			{
				final Point override = CanOverridePoint(worldState, x1, y1, rotation);

				zOverrideX = override.x;
				zOverrideY = override.y;
			}
		}

		final int overrideX = zOverrideX;
		final int overrideY = zOverrideY;

		final AtomicReference<Point> firstPoint = new AtomicReference<Point>();

		LinkedList<BuildPlan> buildPlans = FindPath
		(
			(p) ->
			{
				firstPoint.set(p.a);

				int aOverrideX = -1;
				int aOverrideY = -1;

				if (overrideX == p.a.x && overrideY == p.a.y)
				{
					aOverrideX = overrideX;
					aOverrideY = overrideY;
				}

				MaskPoints(masks, true, p.a, p.b);
				LinkedList<BuildPlan> path = pathFinder.BuildPath
				(
					p.a.x,
					p.a.y,
					p.b.x,
					p.b.y,
					aOverrideX,
					aOverrideY,
					NotRotate(GetRotate(x1, y1, p.a.x, p.a.y)),
					targetMode,
					_masksMap
				);
				MaskPoints(masks, false, p.a, p.b);
				return path;
			},
			pair.a,
			pair.b,
			endTime,
			taskEpoch
		);

		if (buildPlans == null && ignoreMask && !Expired(endTime, taskEpoch))
			buildPlans = FindPath
			(
				(p) ->
				{
					firstPoint.set(p.a);

					int aOverrideX = -1;
					int aOverrideY = -1;

					if (overrideX == p.a.x && overrideY == p.a.y)
					{
						aOverrideX = overrideX;
						aOverrideY = overrideY;
					}

					return pathFinder.BuildPath
					(
						p.a.x,
						p.a.y,
						p.b.x,
						p.b.y,
						aOverrideX,
						aOverrideY,
						NotRotate(GetRotate(x1, y1, p.a.x, p.a.y)),
						targetMode,
						null
					);
				},
				pair.a,
				pair.b,
				endTime,
				taskEpoch
			);

		if (buildPlans != null)
		{
			final Point point = firstPoint.get();

			if (replace)
				ReplaceSolid(worldState.BuildPlans, buildPlans, point.x, point.y, x1, y1);

			return buildPlans;
		}

		return null;
	}

	private LinkedList<BuildPlan> FindPath
	(
		final BeamPathFinder pathFinder,
		final WorldState worldState,
		final LinkedList<Point> powers,
		final LinkedList<Point> turbines,
		final long taskEpoch
	)
	{
		final long endTime = System.nanoTime() + (long)Core.settings.getInt(_beamBuildTotalTimeName) * (long)turbines.size() * (long)1000000;

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

		Point point1 = null;
		Point point2 = null;

		int ptDistance = Integer.MAX_VALUE;

		for (final Point power : powers)
			for (final Point turbine : turbines)
			{
				final int distance = Math.abs(power.x - turbine.x) + Math.abs(power.y - turbine.y);

				if (ptDistance > distance)
				{
					point1 = power;
					point2 = turbine;

					ptDistance = distance;
				}
			}

		if (Expired(endTime, taskEpoch))
			return null;

		LinkedList<BuildPlan> buildPlans = null;

		if (point2 == null)
			point2 = turbines.getFirst();

		if (point1 != null)
		{
			long aStartTime = System.nanoTime();
			buildPlans = pathFinder.BuildPath
			(
				point1.x,
				point1.y,
				point2.x,
				point2.y,
				targetMode,
				_masksMap
			);
			long aEndTime = System.nanoTime();

			if (buildPlans == null && ignoreMask)
			{
				if (Expired(endTime, taskEpoch))
					return buildPlans;

				aStartTime = System.nanoTime();
				buildPlans = pathFinder.BuildPath
				(
					point1.x,
					point1.y,
					point2.x,
					point2.y,
					targetMode,
					null
				);
				aEndTime = System.nanoTime();
			}

			if (buildPlans != null)
				_resultTimeAlgorithm = (aEndTime - aStartTime) / (long)1000000;
		}

		if (buildPlans == null)
			buildPlans = new LinkedList<BuildPlan>();

		final Point shitForJava1 = point2;
		turbines.removeIf(v -> v == shitForJava1);

		point1 = point2;

		final int size = turbines.size();
		for (int i = 0; i < size; ++i)
		{
			if (Expired(endTime, taskEpoch))
				return buildPlans;

			point2 = null;
			int ttDistance = Integer.MAX_VALUE;

			for (final Point point : turbines)
			{
				final int distance = Math.abs(point1.x - point.x) + Math.abs(point1.y - point.y);

				if (ttDistance > distance)
				{
					point2 = point;
					ttDistance = distance;
				}
			}

			if (Expired(endTime, taskEpoch))
				return buildPlans;

			long aStartTime = System.nanoTime();
			LinkedList<BuildPlan> aBuildPlans = pathFinder.BuildPath
			(
				point1.x,
				point1.y,
				point2.x,
				point2.y,
				targetMode,
				_masksMap
			);
			long aEndTime = System.nanoTime();

			if (aBuildPlans == null && ignoreMask)
			{
				if (Expired(endTime, taskEpoch))
					return buildPlans;

				aStartTime = System.nanoTime();
				aBuildPlans = pathFinder.BuildPath
				(
					point1.x,
					point1.y,
					point2.x,
					point2.y,
					targetMode,
					null
				);
				aEndTime = System.nanoTime();
			}

			if (aBuildPlans != null)
			{
				_resultTimeAlgorithm = (aEndTime - aStartTime) / (long)1000000;
				buildPlans.addAll(aBuildPlans);
			}

			final Point shitForJava2 = point2;
			turbines.removeIf(v -> v == shitForJava2);

			point1 = point2;
		}

		return buildPlans;
	}

	private void FindVent(final int x1, final int y1, final int x2, final int y2)
	{
		final int xMax = Math.min(x1 >= x2 ? x1 : x2, _width - 2);
		final int xMin = Math.max(x1 >= x2 ? x2 : x1, 1);

		final int yMax = Math.min(y1 >= y2 ? y1 : y2, _height - 2);
		final int yMin = Math.max(y1 >= y2 ? y2 : y1, 1);

		final Team team = Vars.player.team();
		final Tiles tiles = Vars.world.tiles;

		if (team == null)
			throw new NullPointerException("Vars.player.team is null");

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		final LinkedList<Point> powers = new LinkedList<Point>();
		final LinkedList<Point> turbines = new LinkedList<Point>();

		final boolean preview = Core.settings.getBool(_previewName);

		final Queue<BuildPlan> buildQueue = Vars.player.unit().plans;
		final Seq<BuildPlan> previewQueue = Vars.control.input.selectPlans;

		Arrays.fill(_buildMap, false);

		for (final BuildPlan buildPlan : buildQueue)
		{
			final Block block = buildPlan.block;

			final int xxMax = Math.min(buildPlan.x + block.size + block.sizeOffset, _width - 1);
			final int xxMin = Math.max(buildPlan.x + block.sizeOffset, 0);

			final int yyMax = Math.min(buildPlan.y + block.size + block.sizeOffset, _height - 1);
			final int yyMin = Math.max(buildPlan.y + block.sizeOffset, 0);

			final int sstep = _width + xxMin - xxMax - 1;
			for (int y = yyMin, i = xxMin + yyMin * _width; y <= yyMax; ++y, i += sstep)
				for (int x = xxMin; x <= xxMax; ++x, ++i)
					_buildMap[i] = true;
		}

		for (final BuildPlan buildPlan : previewQueue)
		{
			final Block block = buildPlan.block;
			if (buildPlan.x + block.size + block.sizeOffset < _width && buildPlan.x + block.sizeOffset >= 0
				&& buildPlan.y + block.size + block.sizeOffset < _height && buildPlan.y + block.sizeOffset >= 0)
			{
				final int xxMax = Math.min(buildPlan.x + block.size + block.sizeOffset, _width - 1);
				final int xxMin = Math.max(buildPlan.x + block.sizeOffset, 0);

				final int yyMax = Math.min(buildPlan.y + block.size + block.sizeOffset, _height - 1);
				final int yyMin = Math.max(buildPlan.y + block.sizeOffset, 0);

				final int sstep = _width + xxMin - xxMax - 1;
				for (int y = yyMin, i = xxMin + yyMin * _width; y <= yyMax; ++y, i += sstep)
					for (int x = xxMin; x <= xxMax; ++x, ++i)
						_buildMap[i] = true;
			}
		}

		final int step = _width - (xMax - xMin + 1);
		for (int y = yMin, i = xMin + yMin * _width; y <= yMax; ++y, i += step)
			CHECK_VENT:
			for (int x = xMin; x <= xMax; ++x, ++i)
			{
				Tile tile = tiles.geti(i);
				Building build = tile.build;

				if (tile.floor().attributes.get(Attribute.steam) <= 0F || (build != null && build.team == team) || !_buildMap[i])
					continue CHECK_VENT;

				final int xxMax = x + 1;
				final int xxMin = x - 1;

				final int yyMax = y + 1;
				final int yyMin = y - 1;

				final int sstep = _width - 3;
				for (int yy = yyMin, ii = xxMin + yyMin * _width; yy <= yyMax; ++yy, ii += sstep)
					for (int xx = xxMin; xx <= xxMax; ++xx, ++ii)
					{
						tile = tiles.geti(ii);
						build = tile.build;

						if (tile.floor().attributes.get(Attribute.steam) <= 0F || (build != null && build.team == team) || !_buildMap[ii])
							continue CHECK_VENT;
					}

				if (Build.validPlace(Blocks.turbineCondenser, team, x, y, -1))
				{
					final BuildPlan buildPlan = new BuildPlan(x, y, -1, Blocks.turbineCondenser);
					turbines.addLast(new Point(x, y, i));

					if (preview)
						previewQueue.add(buildPlan);
					else
						buildQueue.addLast(buildPlan);
				}
			}

		if (turbines.size() != 0)
		{
			PowerGraph powerGraph = null;
			float capacity = -1F;

			for (final Building build : Vars.indexer.getFlagged(team, BlockFlag.generator))
				if (build.power != null)
				{
					final PowerGraph pg = build.power.graph;
					final float pgCapacity = pg.getLastCapacity();

					if (capacity < pgCapacity)
					{
						powerGraph = pg;
						capacity = pgCapacity;
					}
				}

			for (final Building build : Vars.indexer.getFlagged(team, BlockFlag.reactor))
				if (build.power != null)
				{
					final PowerGraph pg = build.power.graph;
					final float pgCapacity = pg.getLastCapacity();

					if (capacity < pgCapacity)
					{
						powerGraph = pg;
						capacity = pgCapacity;
					}
				}

			if (powerGraph != null)
				for (final Building build : powerGraph.all)
					powers.addLast(new Point((int)build.tile.x, (int)build.tile.y, (int)build.tile.x + (int)build.tile.y * _width));

			if (!_taskQueue.Produce(new PathTask(powers, turbines, _worldState.BuildPlanEpoch + (long)1, _taskEpoch, PathType.VENT)))
				Vars.ui.showInfoToast(_resultMessage4, 1F);
		}
	}

	private Point GetCurrentPoint()
	{
		final float tilesize = (float)Vars.tilesize;
		final Vec2 vec2 = Core.input.mouseWorld();

		final int x = (int)Math.floor(vec2.x / tilesize + 0.5F);
		final int y = (int)Math.floor(vec2.y / tilesize + 0.5F);
		final int i = x + y * _width;

		return new Point(x, y, i);
	}

	private Point GetCurrentPoint(final int sx, final int sy)
	{
		final float tilesize = (float)Vars.tilesize;
		final Vec2 vec2 = Core.input.mouseWorld((float)sx, (float)sy);

		final int x = (int)Math.floor(vec2.x / tilesize + 0.5F);
		final int y = (int)Math.floor(vec2.y / tilesize + 0.5F);
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

		if (tile1 == tile2)
			return new Pair<ArrayList<Point>, ArrayList<Point>>(new ArrayList<Point>(0), new ArrayList<Point>(0));

		final ArrayList<Point> p1 = GetOuterEdgePoints(block1, (int)tile1.x, (int)tile1.y, (int)tile1.x + (int)tile1.y * _width);
		final ArrayList<Point> p2 = GetInnerEdgePoints(block2, (int)tile2.x, (int)tile2.y, (int)tile2.x + (int)tile2.y * _width);

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

	private void HandleBeamButton()
	{
		_beamButton = true;
	}

	private void HandleBeamButtonDown(final int x, final int y)
	{
		try
		{
			_beamFirstPoint = GetCurrentPoint(x, y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling beam button down: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private void HandleBeamButtonUp(final int x, final int y)
	{
		try
		{
			_beamLastPoint = GetCurrentPoint(x, y);
			if (!_taskQueue.Produce(new PathTask(_beamFirstPoint, _beamLastPoint, (long)-1, _taskEpoch, PathType.BEAM)))
				Vars.ui.showInfoToast(_resultMessage4, 1F);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling beam button up: '" + e.getMessage() + "'");
			e.printStackTrace();
		}

		_beamFirstPoint = null;
		_beamLastPoint = null;

		_beamButton = false;
	}

	private void HandleBeamDown()
	{
		try
		{
			_beamFirstPoint = GetCurrentPoint();
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling beam down: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private void HandleBeamUp()
	{
		try
		{
			_beamLastPoint = GetCurrentPoint();
			if (!_taskQueue.Produce(new PathTask(_beamFirstPoint, _beamLastPoint, (long)-1, _taskEpoch, PathType.BEAM)))
				Vars.ui.showInfoToast(_resultMessage4, 1F);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling beam up: '" + e.getMessage() + "'");
			e.printStackTrace();
		}

		_beamFirstPoint = null;
		_beamLastPoint = null;
	}

	private void HandleLiquidButton()
	{
		_liquidButton = true;
	}

	private void HandleLiquidButtonDown(final int x, final int y)
	{
		try
		{
			_liquidFirstPoint = GetCurrentPoint(x, y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling liquid button down: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private void HandleLiquidButtonUp(final int x, final int y)
	{
		try
		{
			_liquidLastPoint = GetCurrentPoint(x, y);
			if (!_taskQueue.Produce(new PathTask(_liquidFirstPoint, _liquidLastPoint, (long)-1, _taskEpoch, PathType.LIQUID)))
				Vars.ui.showInfoToast(_resultMessage4, 1F);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling liquid button up: '" + e.getMessage() + "'");
			e.printStackTrace();
		}

		_liquidFirstPoint = null;
		_liquidLastPoint = null;

		_liquidButton = false;
	}

	private void HandleLiquidDown()
	{
		try
		{
			_liquidFirstPoint = GetCurrentPoint();
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling liquid down: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private void HandleLiquidUp()
	{
		try
		{
			_liquidLastPoint = GetCurrentPoint();
			if (!_taskQueue.Produce(new PathTask(_liquidFirstPoint, _liquidLastPoint, (long)-1, _taskEpoch, PathType.LIQUID)))
				Vars.ui.showInfoToast(_resultMessage4, 1F);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling liquid up: '" + e.getMessage() + "'");
			e.printStackTrace();
		}

		_liquidFirstPoint = null;
		_liquidLastPoint = null;
	}

	private void HandleSolidButton()
	{
		_solidButton = true;
	}

	private void HandleSolidButtonDown(final int x, final int y)
	{
		try
		{
			_solidFirstPoint = GetCurrentPoint(x, y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling solid button down: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private void HandleSolidButtonUp(final int x, final int y)
	{
		try
		{
			_solidLastPoint = GetCurrentPoint(x, y);
			if (!_taskQueue.Produce(new PathTask(_solidFirstPoint, _solidLastPoint, (long)-1, _taskEpoch, PathType.SOLID)))
				Vars.ui.showInfoToast(_resultMessage4, 1F);

			DisableRouterSorter(_solidFirstPoint.x, _solidFirstPoint.y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling solid button up: '" + e.getMessage() + "'");
			e.printStackTrace();
		}

		_solidFirstPoint = null;
		_solidLastPoint = null;

		_solidButton = false;
	}

	private void HandleSolidDown()
	{
		try
		{
			_solidFirstPoint = GetCurrentPoint();
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling solid down: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private void HandleSolidUp()
	{
		try
		{
			_solidLastPoint = GetCurrentPoint();
			if (!_taskQueue.Produce(new PathTask(_solidFirstPoint, _solidLastPoint, (long)-1, _taskEpoch, PathType.SOLID)))
				Vars.ui.showInfoToast(_resultMessage4, 1F);

			DisableRouterSorter(_solidFirstPoint.x, _solidFirstPoint.y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling solid up: '" + e.getMessage() + "'");
			e.printStackTrace();
		}

		_solidFirstPoint = null;
		_solidLastPoint = null;
	}

	private void HandleTakeButton()
	{
		_takeButton = true;
	}

	private void HandleTakeButtonDown(final int x, final int y)
	{
		try
		{
			_takeFirstPoint = GetCurrentPoint(x, y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling take button down: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private void HandleTakeButtonUp(final int x, final int y)
	{
		try
		{
			_takeLastPoint = GetCurrentPoint(x, y);
			TakeToTheTop(_takeFirstPoint.x, _takeFirstPoint.y, _takeLastPoint.x, _takeLastPoint.y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling take button up: '" + e.getMessage() + "'");
			e.printStackTrace();
		}

		_takeFirstPoint = null;
		_takeLastPoint = null;

		_takeButton = false;
	}

	private void HandleTakeDown()
	{
		try
		{
			_takeFirstPoint = GetCurrentPoint();
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling take down: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private void HandleTakeUp()
	{
		try
		{
			_takeLastPoint = GetCurrentPoint();
			TakeToTheTop(_takeFirstPoint.x, _takeFirstPoint.y, _takeLastPoint.x, _takeLastPoint.y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling take up: '" + e.getMessage() + "'");
			e.printStackTrace();
		}

		_takeFirstPoint = null;
		_takeLastPoint = null;
	}

	private void HandleVentButton()
	{
		_ventButton = true;
	}

	private void HandleVentButtonDown(final int x, final int y)
	{
		try
		{
			_ventFirstPoint = GetCurrentPoint(x, y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling vent button down: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private void HandleVentButtonUp(final int x, final int y)
	{
		try
		{
			_ventLastPoint = GetCurrentPoint(x, y);
			FindVent(_ventFirstPoint.x, _ventFirstPoint.y, _ventLastPoint.x, _ventLastPoint.y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling vent button up: '" + e.getMessage() + "'");
			e.printStackTrace();
		}

		_ventFirstPoint = null;
		_ventLastPoint = null;

		_ventButton = false;
	}

	private void HandleVentDown()
	{
		try
		{
			_ventFirstPoint = GetCurrentPoint();
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling vent down: '" + e.getMessage() + "'");
			e.printStackTrace();
		}
	}

	private void HandleVentUp()
	{
		try
		{
			_ventLastPoint = GetCurrentPoint();
			FindVent(_ventFirstPoint.x, _ventFirstPoint.y, _ventLastPoint.x, _ventLastPoint.y);
		}
		catch (Exception e)
		{
			PrintLine("Exception cacthed when handling vent up: '" + e.getMessage() + "'");
			e.printStackTrace();
		}

		_ventFirstPoint = null;
		_ventLastPoint = null;
	}

	private void MachindustryDispose()
	{
		// DO NOT EVEN THINK
		// Events.remove(DisposeEvent.class, _gameExitEventCons);
		Events.remove(WorldLoadEvent.class, _worldLoadEventCons);

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

		++_taskEpoch;

		if (_worldState != null)
			_worldState.close();

		_buildMap = new boolean[_size];
		_masksMap = new boolean[_size];
		_worldState = new WorldState
		(
			_height,
			_width,
			Core.settings.getBool(_previewName),
			Core.settings.getBool(_polygonSafeZoneName),
			(float)Core.settings.getInt(_radiusSafeZoneName),
			_worldUpdateRunnable,
			null
		);

		_beamPathFinder = new BeamPathFinder
		(
			_height,
			_width,
			(long)Core.settings.getInt(_beamFrequencyName),
			(long)Core.settings.getInt(_beamBuildTimeName)
		);

		_liquidPathFinder = new LiquidPathFinder
		(
			_height,
			_width,
			(long)Core.settings.getInt(_liquidFrequencyName),
			(long)Core.settings.getInt(_liquidBuildTimeName)
		);

		_solidPathFinder = new SolidPathFinder
		(
			_height,
			_width,
			(long)Core.settings.getInt(_solidFrequencyName),
			(long)Core.settings.getInt(_solidBuildTimeName)
		);
	}

	private void MaskPoints(final boolean[] masks, final boolean mask, final Point a, final Point b)
	{
		if (mask)
		{
			masks[0] = _masksMap[a.i];
			_masksMap[a.i] = false;

			if (b.x + 1 < _width)
			{
				final int index = b.i + 1;

				masks[1] = _masksMap[index];
				_masksMap[index] = false;
			}

			if (b.y + 1 < _height)
			{
				final int index = b.i + _width;

				masks[2] = _masksMap[index];
				_masksMap[index] = false;
			}

			if (b.x > 0)
			{
				final int index = b.i - 1;

				masks[3] = _masksMap[index];
				_masksMap[index] = false;
			}

			if (b.y > 0)
			{
				final int index = b.i - _width;

				masks[4] = _masksMap[index];
				_masksMap[index] = false;
			}
		}
		else
		{
			_masksMap[a.i] = masks[0];

			if (b.x + 1 < _width)
				_masksMap[b.i + 1] = masks[1];

			if (b.y + 1 < _height)
				_masksMap[b.i + _width] = masks[2];

			if (b.x > 0)
				_masksMap[b.i - 1] = masks[3];

			if (b.y > 0)
				_masksMap[b.i - _width] = masks[4];
		}
	}

	private String ShowResultTime()
	{
		String time = _resultMessage1;

		if (_resultTimeAlgorithm == -1)
			time += "-";
		else
		{
			time += _resultTimeAlgorithm;
			_resultTimeAlgorithm = -1;
		}

		time += _resultMessage2;

		if (_resultTimeTotal == -1)
			time += "-";
		else
		{
			time += _resultTimeTotal;
			_resultTimeTotal = -1;
		}

		time += _resultMessage3;

		return time;
	}

	private void TakeToTheTop(final boolean topAC, final boolean topBN, final boolean topBT, final boolean topEH, final boolean topLT)
	{
		if (topAC || topBN || topBT || topEH || topLT)
		{
			boolean buildTowerExist = false;

			final Queue<BuildPlan> queue = Vars.player.unit().plans;
			final Queue<BuildPlan> topOrderedQueue = new Queue<BuildPlan>(queue.size);
			final Queue<BuildPlan> bottomOrderedQueue = new Queue<BuildPlan>(queue.size);

			for (BuildPlan buildPlan : queue)
				if (!buildPlan.breaking && buildPlan.block == Blocks.buildTower)
				{
					buildTowerExist = true;
					break;
				}

			if (buildTowerExist)
			{
				for (final BuildPlan buildPlan : queue)
				{
					final Block block = buildPlan.block;

					if (!buildPlan.breaking
						&& ((block == Blocks.atmosphericConcentrator && topAC)
						|| (block == Blocks.beamNode && topBN)
						|| (block == Blocks.buildTower && topBT)
						|| (block == Blocks.electricHeater && topEH)
						|| (block instanceof LiquidBlock && topLT)))
						topOrderedQueue.addLast(buildPlan);
					else
						bottomOrderedQueue.addLast(buildPlan);
				}

				queue.clear();

				for (final BuildPlan buildPlan : topOrderedQueue)
					queue.addLast(buildPlan);

				for (final BuildPlan buildPlan : bottomOrderedQueue)
					queue.addLast(buildPlan);
			}
		}
	}

	private void TakeToTheTop(final int x1, final int y1, final int x2, final int y2)
	{
		final int xMax = Math.max(x1, x2);
		final int xMin = Math.min(x1, x2);
		final int yMax = Math.max(y1, y2);
		final int yMin = Math.min(y1, y2);

		final Queue<BuildPlan> queue = Vars.player.unit().plans;
		final Queue<BuildPlan> topOrderedQueue = new Queue<BuildPlan>(queue.size);
		final Queue<BuildPlan> bottomOrderedQueue = new Queue<BuildPlan>(queue.size);

		for (final BuildPlan buildPlan : queue)
		{
			final Block block = buildPlan.block;

			final int blockXMax = buildPlan.x + block.size + block.sizeOffset;
			final int blockXMin = buildPlan.x + block.sizeOffset;
			final int blockYMax = buildPlan.y + block.size + block.sizeOffset;
			final int blockYMin = buildPlan.y + block.sizeOffset;

			if (!buildPlan.breaking && blockXMax <= xMax && blockXMin >= xMin && blockYMax <= yMax && blockYMin >= yMin)
				topOrderedQueue.addLast(buildPlan);
			else
				bottomOrderedQueue.addLast(buildPlan);
		}

		queue.clear();

		for (final BuildPlan buildPlan : topOrderedQueue)
			queue.addLast(buildPlan);

		for (final BuildPlan buildPlan : bottomOrderedQueue)
			queue.addLast(buildPlan);
	}

	private void TaskWorker()
	{
		while (_running)
		{
			if (Vars.state.isMenu())
				_taskQueue.Clear();

			final PathTask task = _taskQueue.Consume();

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
				while (_worldState.BuildPlanEpoch < task.planEpoch)
					try
					{
						Thread.sleep((long)1);
					}
					catch (InterruptedException e) {}

				LinkedList<BuildPlan> buildPlans = null;

				try
				{
					final long tStartTime = System.nanoTime();
					switch (task.type)
					{
						case BEAM:
							buildPlans = FindPath
							(
								_beamPathFinder,
								_worldState,
								((Point)task.o1).x,
								((Point)task.o1).y,
								((Point)task.o2).x,
								((Point)task.o2).y, 
								task.taskEpoch
							);
							break;

						case LIQUID:
							buildPlans = FindPath
							(
								_liquidPathFinder,
								_worldState,
								((Point)task.o1).x,
								((Point)task.o1).y,
								((Point)task.o2).x,
								((Point)task.o2).y, 
								task.taskEpoch
							);
							break;

						case SOLID:
							buildPlans = FindPath
							(
								_solidPathFinder,
								_worldState,
								((Point)task.o1).x,
								((Point)task.o1).y,
								((Point)task.o2).x,
								((Point)task.o2).y, 
								task.taskEpoch
							);
							break;

						case VENT:
							buildPlans = FindPath
							(
								_beamPathFinder,
								_worldState,
								(LinkedList<Point>)task.o1,
								(LinkedList<Point>)task.o2,
								task.taskEpoch
							);
							break;

						default:
							break;
					}
					final long tEndTime = System.nanoTime();

					_resultTimeTotal = (tEndTime - tStartTime) / (long)1000000;
				}
				catch (Exception e)
				{
					PrintLine
					(
						"Exception catched when working on task" +
						(task.o1 instanceof Point ? (" x1 = " + ((Point)task.o1).x + ",") : "") +
						(task.o1 instanceof Point ? (" y1 = " + ((Point)task.o1).y + ",") : "") +
						(task.o1 instanceof LinkedList ? (" list1 size = " + ((LinkedList<?>)task.o1).size() + ",") : "") +
						(task.o2 instanceof Point ? (" x2 = " + ((Point)task.o2).x + ",") : "") +
						(task.o2 instanceof Point ? (" y2 = " + ((Point)task.o2).y + ",") : "") +
						(task.o2 instanceof LinkedList ? (" list2 size = " + ((LinkedList<?>)task.o2).size() + ",") : "") +
						" type = " + task.type + "," +
						" plan epoch = " + task.planEpoch + "," +
						" task epoch = " + task.taskEpoch + "," +
						" global plan epoch = " + _worldState.BuildPlanEpoch + "," +
						" global task epoch = " + _taskEpoch + "," +
						" exception = '" + e.getMessage() + "'"
					);

					e.printStackTrace();
				}

				if (buildPlans == null)
					_resultFailure = true;
				else
				{
					_resultSuccess = true;
					final BuildPlan[] buildPlansArray = buildPlans.toArray(new BuildPlan[buildPlans.size()]);

					while (_running && !Vars.state.isMenu() && !_worldState.BuildPlansMachinary.Produce(buildPlansArray))
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

	private void WorldDrawRunnable()
	{
		if (DoHandle())
		{
			Point currentPoint = GetCurrentPoint();

			if (_ventFirstPoint != null)
				DrawRectangleOverlay
				(
					_ventFirstPoint.x,
					_ventFirstPoint.y,
					currentPoint.x,
					currentPoint.y,
					_turbinePointColor
				);

			if (_takeFirstPoint != null)
				DrawRectangleOverlay
				(
					_takeFirstPoint.x,
					_takeFirstPoint.y,
					currentPoint.x,
					currentPoint.y,
					_takeToTheTopPointColor
				);

			if (_beamFirstPoint != null)
			{
				DrawSquareOverlay(_beamFirstPoint.x, _beamFirstPoint.y, _beamPointColor);
				DrawSquareOverlay(currentPoint.x, currentPoint.y, _beamPointColor);
			}

			if (_liquidFirstPoint != null)
			{
				DrawSquareOverlay(_liquidFirstPoint.x, _liquidFirstPoint.y, _liquidPointColor);
				DrawSquareOverlay(currentPoint.x, currentPoint.y, _liquidPointColor);
			}

			if (_solidFirstPoint != null)
			{
				DrawSquareOverlay(_solidFirstPoint.x, _solidFirstPoint.y, _solidPointColor);
				DrawSquareOverlay(currentPoint.x, currentPoint.y, _solidPointColor);
			}
		}
	}

	private void WorldUpdateRunnable()
	{
		TakeToTheTop
		(
			Core.settings.getBool(_buildAtmosphericConcentratorName),
			Core.settings.getBool(_buildBeamNodeName),
			Core.settings.getBool(_buildBuildTowerName),
			Core.settings.getBool(_buildElectricHeaterName),
			Core.settings.getBool(_buildLiquidTransportName)
		);

		if (Vars.mobile && _touch && (_beamButton || _liquidButton || _solidButton || _takeButton || _ventButton))
		{
			final Vec2 vec2 = Core.input.mouse();

			final float mouseX = vec2.x / (float)Core.graphics.getWidth();
			final float mouseY = vec2.y / (float)Core.graphics.getHeight();

			float dx = 0F;
			float dy = 0F;

			if (mouseX <= 0.1F)
				dx = (0.1F - mouseX) * -0.1F;
			else if (mouseX >= 0.9F)
				dx = (mouseX - 0.9F) * 0.1F;

			if (mouseY <= 0.1F)
				dy = (0.1F - mouseY) * -0.1F;
			else if (mouseY >= 0.9F)
				dy = (mouseY - 0.9F) * 0.1F;

			Core.camera.position.x += dx * Core.camera.width;
			Core.camera.position.y += dy * Core.camera.height;
		}

		if (_resultFailure)
		{
			_resultFailure = false;
			Vars.ui.showInfoToast(_failureMessage + ShowResultTime(), 1F);
		}

		if (_resultSuccess)
		{
			_resultSuccess = false;
			Vars.ui.showInfoToast(_successMessage + ShowResultTime(), 1F);
		}
	}

	public Machindustry()
	{
		super();

		// Should remove after???
		Events.run(Trigger.drawOver, _worldDrawRunnable);

		Events.on(DisposeEvent.class, _gameExitEventCons);
		Events.on(WorldLoadEvent.class, _worldLoadEventCons);

		_thread = new Thread(() -> TaskWorker(), "Machindustry worker thread");
		_thread.setDaemon(true);
		_thread.start();
	}

	@Override
	public void init()
	{
		_failureMessage = Core.bundle.get("machindustry.failure-message");
		_successMessage = Core.bundle.get("machindustry.success-message");

		_resultMessage1 = " [[";
		_resultMessage2 = " " + Core.bundle.get("machindustry.ms") + "; ";
		_resultMessage3 = " " + Core.bundle.get("machindustry.ms") + "]";
		_resultMessage4 = Core.bundle.get("machindustry.overflow-message");

		if (!Core.settings.getBool(_name))
		{
			Core.settings.put(_name, true);
			Tutorial();
		}

		Vars.ui.settings.getCategories().add(new SettingsCategory
		(
			Core.bundle.get("machindustry.title"),
			new TextureRegionDrawable(Core.atlas.find("machindustry-machindustry")),
			_settingsBuilder
		));

		_touch = Core.settings.getBool(_buttonsName);

		if (_touch)
			Vars.ui.hudGroup.fill(_buttonsBuilder);

		Core.input.addProcessor(_inputListener);
		CheckUpdates();
	}

	private class MachindustryInputProcessor implements InputProcessor
	{
		@Override
		public boolean keyDown(KeyCode code)
		{
			if (DoHandle())
			{
				if (code == _beamPathFinderCode)
					HandleBeamDown();
				else if (code == _liquidPathFinderCode)
					HandleLiquidDown();
				else if (code == _solidPathFinderCode)
					HandleSolidDown();
				else if (code == _takeToTheTopCode)
					HandleTakeDown();
				else if (code == _ventBuilderCode)
					HandleVentDown();
				else
					return false;

				return true;
			}
			else
				return false;
		}

		@Override
		public boolean keyUp(KeyCode code)
		{
			if (DoHandle())
			{
				if (code == _beamPathFinderCode)
					HandleBeamUp();
				else if (code == _liquidPathFinderCode)
					HandleLiquidUp();
				else if (code == _solidPathFinderCode)
					HandleSolidUp();
				else if (code == _takeToTheTopCode)
					HandleTakeUp();
				else if (code == _ventBuilderCode)
					HandleVentUp();
				else
					return false;

				return true;
			}
			else
				return false;
		}

		@Override
		public boolean touchDown(int x, int y, int p, KeyCode code)
		{
			if (_touch && DoHandle())
			{
				if (_beamButton)
					HandleBeamButtonDown(x, y);
				else if (_liquidButton)
					HandleLiquidButtonDown(x, y);
				else if (_solidButton)
					HandleSolidButtonDown(x, y);
				else if (_takeButton)
					HandleTakeButtonDown(x, y);
				else if (_ventButton)
					HandleVentButtonDown(x, y);
				else
					return false;

				return true;
			}
			else
				return false;
		}

		@Override
		public boolean touchUp(int x, int y, int p, KeyCode code)
		{
			if (_touch && DoHandle())
			{
				if (_beamButton)
					HandleBeamButtonUp(x, y);
				else if (_liquidButton)
					HandleLiquidButtonUp(x, y);
				else if (_solidButton)
					HandleSolidButtonUp(x, y);
				else if (_takeButton)
					HandleTakeButtonUp(x, y);
				else if (_ventButton)
					HandleVentButtonUp(x, y);
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