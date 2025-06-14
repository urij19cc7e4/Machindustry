package machindustry;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.Tiles;

public class BeamPathFinder
{
    // Not using enum because of Java memory model
	// Need this to work fast
	// Enum TileRotate

	/**
	 * →
	*/
	private static final int RIGHT = 0;

	/**
	 * ↑
	*/
	private static final int UPPER = 1;

	/**
	 * ←
	*/
	private static final int LEFT = 2;

	/**
	 * ↓
	*/
	private static final int BOTTOM = 3;

	// Not using enum because of Java memory model
	// Need this to work fast
	// Enum TileState

	/**
	 * Conduct or consume energy
	*/
	private static final byte ENERGY = (byte)0;

	/**
	 * Can not place beam
	*/
	private static final byte BLOCK = (byte)1;

	/**
	 * Can place beam
	*/
	private static final byte EMPTY = (byte)2;

	/**
	 * Internal map height
	*/
	private final int _height;

	/**
	 * Internal map width
	*/
	private final int _width;

	/**
	 * Internal map size
	*/
	private final int _size;

	/**
	 * Internal tile state map
	*/
	private final byte[] _map;

	/**
	 * How much evaluations done before timer check
	*/
	public long Frequency = (long)-1;

	/**
	 * How much time can spent on path building, ns
	*/
	public long BuildTime = (long)-1;

	public BeamPathFinder(int height, int width)
	{
		_height = height;
		_width = width;
		_size = height * width;
		_map = new byte[_size];
	}

	public BeamPathFinder(int height, int width, long freq, long time)
	{
		this(height, width);

		Frequency = freq;
		BuildTime = time;
	}

	/**
	 * Updates internal map from building validation map
	 * @param map - Building validation map
	*/
	public void UpdateMap(final boolean[] map)
	{
		final Team team = Vars.player.team();
		final Tiles tiles = Vars.world.tiles;

		if (team == null)
			throw new NullPointerException("Vars.player.team is null");

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		// Divide all tiles into energy, block and empty tiles
		for (int i = 0; i < _size; ++i)
		{
			final Tile tile = tiles.geti(i);
			final Block block = tile.block();
			final Building build = tile.build;

			if (map[i])
			{
				if (block == Blocks.reinforcedConduit && build != null && build.team == team)
				{
					_map[i] = INVISIBLE;
					vMap[i] = build.rotation == RIGHT || build.rotation == LEFT;
				}
				else if (block == Blocks.reinforcedBridgeConduit && build != null && build.team == team)
					_map[i] = PROTECT;
				else
					_map[i] = BLOCK;
			}
			else
				_map[i] = EMPTY;

			oMap[i] = block.outputsLiquid;
		}

		// Divide empty tiles into collide, damage, danger, block and empty tiles
		// It is faster to seek buildings and bridges to divide nearby empty tiles
		// than to seek buildings and bridges around each empty tile
		for (int y = 0, i = 0; y < _height; ++y)
			for (int x = 0; x < _width; ++x, ++i)
				if (_map[i] == INVISIBLE || _map[i] == PROTECT || _map[i] == BLOCK)
				{
					final Tile tile = tiles.geti(i);
					final Building build = tile.build;

					// Can be changed to null by main game thread
					// Can be null because of use block as mask
					// Can be other team's
					if (build == null || build.team != team)
						continue;

					if (_map[i] == PROTECT)
						ProcessProtect(build.rotation, x, y, i);
					else
					{
						final Block block = tile.block();

						// Check one building only once (build.tile == tile)
						// Check blocks that output liquids
						if (build.tile == tile && block.outputsLiquid)
							ProcessBlock(block, build.rotation, x, y, i);
					}
				}
	}

	/**
	 * Updates internal map from building plans array
	 * @param buildPlans - Building plans array
	*/
	public void UpdateMap(final BuildPlan[] buildPlans)
	{
		// Not update breaking plans because can not use their space until they are finished
		// Divide all tiles under buildings on map
		for (BuildPlan buildPlan : buildPlans)
			if (!buildPlan.breaking)
			{
				final Block block = buildPlan.block;

				if (block == Blocks.reinforcedConduit)
				{
					int idx = buildPlan.x + buildPlan.y * _width;

					_map[idx] = INVISIBLE;
					vMap[idx] = buildPlan.rotation == RIGHT || buildPlan.rotation == LEFT;
				}
				else if (block == Blocks.reinforcedBridgeConduit)
					_map[buildPlan.x + buildPlan.y * _width] = PROTECT;
				else if (block.size == 1)
					_map[buildPlan.x + buildPlan.y * _width] = BLOCK;
				else
				{
					final int x1 = buildPlan.x + block.sizeOffset;
					final int x2 = x1 + block.size;

					final int y1 = buildPlan.y + block.sizeOffset;
					final int y2 = y1 + block.size;

					final int step = _width + x1 - x2;

					if (block.outputsLiquid)
						for (int y = y1, i = x1 + y1 * _width; y < y2; ++y, i += step)
							for (int x = x1; x < x2; ++x, ++i)
							{
								_map[i] = BLOCK;
								oMap[i] = true;
							}
					else
						for (int y = y1, i = x1 + y1 * _width; y < y2; ++y, i += step)
							for (int x = x1; x < x2; ++x, ++i)
								_map[i] = BLOCK;
				}
			}
			
		// Not update breaking plans because can not use their space until they are finished
		// Divide empty tiles nearby buildings on map
		for (BuildPlan buildPlan : buildPlans)
			if (!buildPlan.breaking)
			{
				final Block block = buildPlan.block;
				final int idx = buildPlan.x + buildPlan.y * _width;

				if (block == Blocks.reinforcedBridgeConduit)
					ProcessProtect(buildPlan.rotation, buildPlan.x, buildPlan.y, idx);
				else if (block.outputsLiquid)
					ProcessBlock(block, buildPlan.rotation, buildPlan.x, buildPlan.y, idx);
			}
	}
}