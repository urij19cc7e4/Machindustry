package machindustry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import arc.Events;
import arc.func.Cons;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.Queue;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.game.EventType.Trigger;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.graphics.Voronoi;
import mindustry.graphics.Voronoi.GraphEdge;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

@SuppressWarnings({ "deprecation", "removal", "unchecked" })
public class WorldState implements AutoCloseable
{
	/**
	 * Is closed
	*/
	private boolean _closed = false;

	/**
	 * Do not direct access, copy first
	*/
	private CoreBuild[] _cores = null;

	/**
	 * Interacts with game data in main game thread
	*/
	private final Cons<?> _updater = e -> MainGameThreadUpdate();

	/**
	 * Used to check if close invoked in same thread as constructor; assuming constructor was invoked in main game thread
	*/
	private final long _threadID = Thread.currentThread().getId();

	/**
	 * Building validation map height
	*/
	public final int Height;

	/**
	 * Building validation map width
	*/
	public final int Width;

	/**
	 * Building validation map size
	*/
	public final int Size;

	/**
	 * Building validation map
	*/
	public final boolean[] Map;

	/**
	 * Main game thread must expect not null. Not main game thread must expect null.
	*/
	public final AtomicReference<BuildPlan[]> BuildPlansMachinary = new AtomicReference<BuildPlan[]>(null);

	/**
	 * Do not direct access, copy first
	*/
	public BuildPlan[] BuildPlans = null;

	/**
	 * Better safe than sorry. Increases polygon borders between cores from 1 to 3. This is TILE term not UNIT (not x8).
	*/
	public boolean PolygonSafeZone = true;

	/**
	 * Better safe than sorry. Increases radius borders by given value. This is TILE term not UNIT (not x8).
	*/
	public float RadiusSafeZone = 1F;

	/**
	 * Interacts with game data in main game thread.
	 * BuildPlansMachinary field is copied to player building plans.
	 * Player building plans are copied to BuildPlans field.
	 * Map cores are copied to _cores field.
	*/
	private void MainGameThreadUpdate()
	{
		final Seq<TeamData> teams = Vars.state.teams.active;
		final Queue<BuildPlan> queue = Vars.player.unit().plans;

		// Need to change it to null so cmpxchg value to null
		// If other thread already cmpxchg null to not null then do not care
		final BuildPlan[] buildPlansMachinary = BuildPlansMachinary.get();
		BuildPlansMachinary.compareAndSet(buildPlansMachinary, null);

		if (buildPlansMachinary != null)
		{
			queue.ensureCapacity(buildPlansMachinary.length);

			for (BuildPlan buildPlan : buildPlansMachinary)
				queue.addLast(buildPlan);
		}

		final BuildPlan[] buildPlans = new BuildPlan[queue.size];

		CoreBuild[] cores;
		int count = 0;

		for (BuildPlan buildPlan : queue)
			buildPlans[count++] = buildPlan;

		count = 0;

		for (TeamData teamData : teams)
			count += teamData.cores.size;

		cores = new CoreBuild[count];
		count = 0;

		for (TeamData teamData : teams)
			for (CoreBuild coreBuild : teamData.cores)
				cores[count++] = coreBuild;

		_cores = cores;
		BuildPlans = buildPlans;
	}

	@Override
	protected void finalize() throws Throwable
	{
		try
		{
			close();
		}
		finally
		{
			super.finalize();
		}
	}

	private class Point
	{
		public int x;
		public int y;
		public int i;

		public Point(int x, int y, int i)
		{
			this.x = x;
			this.y = y;
			this.i = i;
		}
	}

	/**
	 * INVOKE ONLY IN MAIN GAME THREAD
	 * @throws Exception ARC events reflection access error
	*/
	public WorldState(int height, int width) throws Exception
	{
		Height = height;
		Width = width;
		Size = height * width;
		Map = new boolean[Size];

		try
		{
			// Since there is no way to add enum-keyed listener to ARC event system without identity lost
			// use reflection to direct access private field and add event listener
			final Field eventsField = Events.class.getDeclaredField("events");
			eventsField.setAccessible(true);

			final ObjectMap<Object, Seq<Cons<?>>> events = (ObjectMap<Object, Seq<Cons<?>>>)eventsField.get(null);
			events.get(Trigger.update, () -> new Seq<>(Cons.class)).add(_updater);
		}
		catch (Exception e)
		{
			System.err.println("ARC events reflection access error");
			e.printStackTrace();

			throw new Exception("ARC events reflection access error");
		}
	}

	/**
	 * INVOKE ONLY IN MAIN GAME THREAD
	 * @throws Exception ARC events reflection access error
	*/
	public WorldState(int height, int width, boolean polygonSZ, float radiusSZ) throws Exception
	{
		this(height, width);

		PolygonSafeZone = polygonSZ;
		RadiusSafeZone = radiusSZ;
	}

	/**
	 * Updates internal building validation map. At fact this is ported version of {@link Build#validPlace}
	 * method designed to run in a separate thread and optimized for processing the entire map efficiently.
	 * It does not check ground units
	*/
	public void UpdateMap()
	{
		MainGameThreadUpdate();
		final Team team = Vars.player.team();
		final Tiles tiles = Vars.world.tiles;

		if (team == null)
			throw new NullPointerException("Vars.player.team is null");

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		final CoreBuild[] cores = _cores;
		final float tilesize = (float)Vars.tilesize;

		if (cores == null)
			throw new NullPointerException("WorldState.cores is null");

		if (Vars.state.rules.polygonCoreProtection)
		{
			final Vec2[] coresVec2s = new Vec2[cores.length];

			for (int i = 0; i < cores.length; ++i)
				coresVec2s[i] = new Vec2(cores[i].x, cores[i].y);

			final Seq<GraphEdge> edges = Voronoi.generate(coresVec2s, 0F, (float)Vars.world.unitWidth(), 0F, (float)Vars.world.unitHeight());

			// Draw Bresenham line for each graph edge that is between enemy and player teams
			for (GraphEdge edge : edges)
				if (cores[edge.site1].team == team ^ cores[edge.site2].team == team)
				{
					float x1 = edge.x1;
					float y1 = edge.y1;

					final float x2 = edge.x2;
					final float y2 = edge.y2;

					if (x1 == x2 && y1 == y2)
						continue;

					final float dx = Math.abs(x1 - x2);
					final float dy = Math.abs(y1 - y2);

					final float sx = (x1 < x2) ? tilesize : -tilesize;
					final float sy = (y1 < y2) ? tilesize : -tilesize;

					final int ix2 = Math.round(x2 / tilesize);
					final int iy2 = Math.round(y2 / tilesize);

					float error = dx - dy;

					while (true)
					{
						// Do you also hate 'to nearest even' rounding? Let's hate together
						final int ix1 = Math.round(x1 / tilesize);
						final int iy1 = Math.round(y1 / tilesize);

						if (ix1 < 0 || iy1 < 0 || ix1 >= Width || iy1 >= Height)
							break;

						final int i = ix1 + iy1 * Width;
						Map[i] = true;

						if (PolygonSafeZone)
						{
							if (ix1 + 1 < Width)
								Map[i + 1] = true;

							if (iy1 + 1 < Height)
								Map[i + Width] = true;

							if (ix1 - 1 >= 0)
								Map[i - 1] = true;

							if (iy1 - 1 >= 0)
								Map[i - Width] = true;
						}

						if ((sx >= 0F ? ix1 >= ix2 : ix1 <= ix2) && (sy >= 0F ? iy1 >= iy2 : iy1 <= iy2))
							break;

						final float errorEx = error * 2F;

						if (errorEx > -dy)
						{
							error -= dy;
							x1 += sx;
						}

						if (errorEx < dx)
						{
							error += dx;
							y1 += sy;
						}
					}
				}

			final ArrayList<Point> queue = new ArrayList<Point>(Size);

			// Fill between enemy cores and enemy-player graph edges
			for (CoreBuild core : cores)
				if (core.team != team)
				{
					int x = Math.round(core.x / tilesize);
					int y = Math.round(core.y / tilesize);
					int i = x + y * Width;

					queue.add(new Point(x, y, i));

					// Yes I hate recursion
					while (queue.size() != 0)
					{
						final Point point = queue.remove(queue.size() - 1);

						x = point.x;
						y = point.y;
						i = point.i;

						if (x < 0 || y < 0 || x >= Width || y >= Height || Map[i])
							continue;

						Map[i] = true;

						queue.add(new Point(x + 1, y, i + 1));
						queue.add(new Point(x, y + 1, i + Width));
						queue.add(new Point(x - 1, y, i - 1));
						queue.add(new Point(x, y - 1, i - Width));
					}
				}
		}
		else
		{
			final float tileRadius = Vars.state.rules.enemyCoreBuildRadius + RadiusSafeZone * tilesize + tilesize;
			final float tileRadiusSquare = tileRadius * tileRadius;

			for (CoreBuild core : cores)
				if (core.team != team)
				{
					final int xMax = Math.min((int)Math.floor((core.x + tileRadius) / tilesize), Width - 1);
					final int xMin = Math.max((int)Math.ceil((core.x - tileRadius) / tilesize), 0);

					final int yMax = Math.min((int)Math.floor((core.y + tileRadius) / tilesize), Height - 1);
					final int yMin = Math.max((int)Math.ceil((core.y - tileRadius) / tilesize), 0);

					final int step = Width + xMin - xMax - 1;
					float fy = (float)yMin * tilesize;

					// Yes I do not use float as loop counter
					for (int y = yMin, i = xMin + yMin * Width; y <= yMax; ++y, i += step, fy += tilesize)
					{
						float fx = (float)xMin * tilesize;
						for (int x = xMin; x <= xMax; ++x, ++i, fx += tilesize)
							if (Mathf.dst2(fx, fy, core.x, core.y) < tileRadiusSquare)
								Map[i] = true;
					}
				}
		}

		if (Vars.state.rules.placeRangeCheck)
		{
			// Do not ask why slag incinerator
			final float tileRadiusEx = Blocks.slagIncinerator.placeOverlapRange + RadiusSafeZone * tilesize + 4F;

			for (int ii = 0; ii < Size; ++ii)
			{
				final Tile tile = tiles.geti(ii);
				final Building build = tile.build;

				// Check one building only once (build.tile != tile)
				if (build == null || build.team == team || build.tile != tile)
					continue;

				final float tileRadius = tileRadiusEx + build.hitSize() / 2F;
				final float tileRadiusSquare = tileRadius * tileRadius;

				final int xMax = Math.min((int)Math.floor((build.x + tileRadius) / tilesize), Width - 1);
				final int xMin = Math.max((int)Math.ceil((build.x - tileRadius) / tilesize), 0);

				final int yMax = Math.min((int)Math.floor((build.y + tileRadius) / tilesize), Height - 1);
				final int yMin = Math.max((int)Math.ceil((build.y - tileRadius) / tilesize), 0);

				final int step = Width + xMin - xMax - 1;
				float fy = (float)yMin * tilesize;

				// Yes I do not use float as loop counter
				for (int y = yMin, i = xMin + yMin * Width; y <= yMax; ++y, i += step, fy += tilesize)
				{
					float fx = (float)xMin * tilesize;
					for (int x = xMin; x <= xMax; ++x, ++i, fx += tilesize)
						if (Mathf.dst2(fx, fy, build.x, build.y) < tileRadiusSquare)
							Map[i] = true;
				}
			}
		}

		for (int y = 0, i = 0; y < Height; ++y)
			for (int x = 0; x < Width; ++x, ++i)
				if (!Map[i])
				{
					final Tile tile = tiles.geti(i);

					final Block block = tile.block();
					final Floor floor = tile.floor();

					Map[i] = !block.alwaysReplace || !floor.placeableOn || floor.isDeep() || !tile.interactable(team) || (Vars.state.rules.fog
						&& Vars.state.rules.staticFog && !Vars.fogControl.isDiscovered(team, x, y)) || Vars.world.getDarkness(x, y) >= 3;
				}
	}

	/**
	 * INVOKE ONLY IN MAIN GAME THREAD
	 * @throws Exception ARC events reflection access error
	*/
	@Override
	public void close() throws Exception
	{
		if (!_closed)
		{
			// Print warning because close might be silently invoked by garbage collector
			if (_threadID != Thread.currentThread().getId())
				System.err.println("WorldState: constructor's and close's threads ids do not match");

			try
			{
				// Since there is no way to remove enum-keyed listener from ARC event system
				// use reflection to direct access private field and remove event listener
				final Field eventsField = Events.class.getDeclaredField("events");
				eventsField.setAccessible(true);

				final ObjectMap<Object, Seq<Cons<?>>> events = (ObjectMap<Object, Seq<Cons<?>>>)eventsField.get(null);
				events.get(Trigger.update, () -> new Seq<>(Cons.class)).remove(_updater);
			}
			catch (Exception e)
			{
				System.err.println("ARC events reflection access error");
				e.printStackTrace();

				throw new Exception("ARC events reflection access error");
			}

			_closed = true;
		}
	}
}