package machindustry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

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
	 * Internal masked tile state map
	*/
	private final byte[] aMap;

	/**
	 * Internal energy buildings index map
	*/
	private final int[] eMap;

	/**
	 * How much evaluations done before timer check
	*/
	public long Frequency = (long)-1;

	/**
	 * How much time can spent on path building, ns
	*/
	public long BuildTime = (long)-1;

	/**
	 * Protects beam tower from shortening its range with beam node
	 * @param x - building x coordinate
	 * @param y - building y coordinate
	 * @param i - building linear coordinate
	*/
	private void ProcessTower(final int x, final int y, final int i)
	{
		int max = x + 13;
		int min = x + 2;

		for (int ix = min, ii = i + 2; ix <= max; ++ix, ++ii)
			if (ix < _width)
			{
				if (_map[ii] != ENERGY)
					_map[ii] = BLOCK;
			}
			else
				break;

		max = y + 13;
		min = y + 2;

		for (int iy = min, ii = i + _width * 2; iy <= max; ++iy, ii += _width)
			if (iy < _height)
			{
				if (_map[ii] != ENERGY)
					_map[ii] = BLOCK;
			}
			else
				break;

		max = x - 2;
		min = x - 13;

		for (int ix = max, ii = i - 2; ix >= min; --ix, --ii)
			if (ix >= 0)
			{
				if (_map[ii] != ENERGY)
					_map[ii] = BLOCK;
			}
			else
				break;


		max = y - 2;
		min = y - 13;

		for (int iy = max, ii = i - _width * 2; iy >= min; --iy, ii -= _width)
			if (iy >= 0)
			{
				if (_map[ii] != ENERGY)
					_map[ii] = BLOCK;
			}
			else
				break;
	}

	public BeamPathFinder(int height, int width)
	{
		_height = height;
		_width = width;
		_size = height * width;
		_map = new byte[_size];
		aMap = new byte[_size];
		eMap = new int[_size];
	}

	public BeamPathFinder(int height, int width, long freq, long time)
	{
		this(height, width);

		Frequency = freq;
		BuildTime = time;
	}

	/**
	 * Builds path for beam nodes
	 * @return             List of building plans if success, null if failure
	 * @param tile1      - First energy tile of the path (starting coordinates)
	 * @param tile2      - Last energy tile of the path (destination coordinates)
	 * @param targetMode - Determines whether to keep target/previous direction settings
	*/
	public LinkedList<BuildPlan> BuildPath(Tile tile1, Tile tile2, boolean targetMode)
	{
		return BuildPath((int)tile1.x, (int)tile1.y, (int)tile2.x, (int)tile2.y, targetMode, null);
	}

	/**
	 * Builds path for beam nodes
	 * @return             List of building plans if success, null if failure
	 * @param x1         - First energy tile of the path (starting coordinate)
	 * @param y1         - First energy tile of the path (starting coordinate)
	 * @param x2         - Last energy tile of the path (destination coordinate)
	 * @param y2         - Last energy tile of the path (destination coordinate)
	 * @param targetMode - Determines whether to keep target/previous direction settings
	*/
	public LinkedList<BuildPlan> BuildPath(int x1, int y1, int x2, int y2, boolean targetMode)
	{
		return BuildPath(x1, y1, x2, y2, targetMode, null);
	}

	/**
	 * Builds path for beam nodes
	 * @return             List of building plans if success, null if failure
	 * @param tile1      - First energy tile of the path (starting coordinates)
	 * @param tile2      - Last energy tile of the path (destination coordinates)
	 * @param targetMode - Determines whether to keep target/previous direction settings
	 * @param masks      - Boolean map that protects tiles from pathing
	*/
	public LinkedList<BuildPlan> BuildPath(Tile tile1, Tile tile2, boolean targetMode, boolean[] masks)
	{
		return BuildPath((int)tile1.x, (int)tile1.y, (int)tile2.x, (int)tile2.y, targetMode, masks);
	}

	/**
	 * Builds path for beam nodes
	 * @return             List of building plans if success, null if failure
	 * @param x1         - First energy tile of the path (starting coordinate)
	 * @param y1         - First energy tile of the path (starting coordinate)
	 * @param x2         - Last energy tile of the path (destination coordinate)
	 * @param y2         - Last energy tile of the path (destination coordinate)
	 * @param targetMode - Determines whether to keep target/previous direction settings
	 * @param masks      - Boolean map that protects tiles from pathing
	*/
	public LinkedList<BuildPlan> BuildPath(int x1, int y1, final int x2, final int y2, final boolean targetMode, final boolean[] masks)
	{
		long startTime = System.nanoTime();
		long evaluations = 0;

		final int idx1 = x1 + y1 * _width;
		final int idx2 = x2 + y2 * _width;

		final Tiles tiles = Vars.world.tiles;

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		// Check if first and last tiles are not different energy tiles
		if (idx1 == idx2 || _map[idx1] != ENERGY || _map[idx2] != ENERGY)
			return null;

		/**
	 	 * Stores building plans constructed from path nodes
		*/
		final LinkedList<BuildPlan> buildPath = new LinkedList<BuildPlan>();

		/**
	 	 * Stores path nodes during path evaluation
		*/
		ArrayList<PathNode> pathNodes = new ArrayList<PathNode>(_size);

		/**
	 	 * Path nodes indices map. Stores -1 or index of valid path node. Filled right before reduction.
		*/
		final int[] iMap = new int[_size];

		/**
	 	 * Path nodes map. Stores false or true for unbuildable or visited tile.
		*/
		final boolean[] pMap = new boolean[_size];

		/**
	 	 * Evaluate rotate order
		*/
		final int[] evaluateRotateOrder = new int[4];

		// Copy tiles
		if (masks == null)
			System.arraycopy(_map, 0, aMap, 0, _size);
		// Copy masked with blocks tiles
		else
		{
			for (int i = 0; i < _size; ++i)
				if (masks[i])
					aMap[i] = _map[i] == ENERGY ? ENERGY : BLOCK;
				else
					aMap[i] = _map[i];
		}

		// Fill indices map with -1
		Arrays.fill(iMap, -1);

		// Map all blocked tiles to pMap
		// Suppose Java initialized arrays with falses already
		for (int i = 0; i < _size; ++i)
			if (aMap[i] == BLOCK)
				pMap[i] = true;

		int pRotate;

		// Rotate in target direction
		if (Math.abs(x1 - x2) > Math.abs(y1 - y2))
		{
			if (x1 < x2)
				pRotate = RIGHT;
			else
				pRotate = LEFT;
		}
		else
		{
			if (y1 < y2)
				pRotate = UPPER;
			else
				pRotate = BOTTOM;
		}

		final int dRotate = pRotate;

		// Path evaluation
		// Yes I hate recursion
		while (x1 != x2 || y1 != y2)
		{
			if (Frequency != -1)
			{
				// If time exceeds return failure
				if (evaluations >= Frequency)
				{
					if (startTime + BuildTime <= System.nanoTime())
						return null;
					else
						evaluations = 0;
				}
				else
					++evaluations;
			}

			final int dx = x1 - x2;
			final int dy = y1 - y2;

			int idx = x1 + y1 * _width;

			int mRotate = -1;
			int mStep = 0;

			int hRotate1, hRotate2;
			int vRotate1, vRotate2;

			if (x1 < x2)
			{
				hRotate1 = RIGHT;
				hRotate2 = LEFT;
			}
			else
			{
				hRotate1 = LEFT;
				hRotate2 = RIGHT;
			}

			if (y1 < y2)
			{
				vRotate1 = UPPER;
				vRotate2 = BOTTOM;
			}
			else
			{
				vRotate1 = BOTTOM;
				vRotate2 = UPPER;
			}

			// If in target mode set direction to target
			if (targetMode)
			{
				if (Math.abs(dx) > Math.abs(dy))
				{
					evaluateRotateOrder[0] = hRotate1;
					evaluateRotateOrder[1] = vRotate1;
					evaluateRotateOrder[2] = vRotate2;
					evaluateRotateOrder[3] = hRotate2;
				}
				else
				{
					evaluateRotateOrder[0] = vRotate1;
					evaluateRotateOrder[1] = hRotate1;
					evaluateRotateOrder[2] = hRotate2;
					evaluateRotateOrder[3] = vRotate2;
				}
			}
			// Else set direction to previous
			else
			{
				if (pRotate == RIGHT || pRotate == LEFT)
				{
					evaluateRotateOrder[1] = vRotate1;
					evaluateRotateOrder[2] = vRotate2;
				}
				else // if (pRotate == UPPER || pRotate == BOTTOM)
				{
					evaluateRotateOrder[1] = hRotate1;
					evaluateRotateOrder[2] = hRotate2;
				}

				evaluateRotateOrder[0] = pRotate;

				if (pRotate <= 1)
					evaluateRotateOrder[3] = pRotate + 2;
				else
					evaluateRotateOrder[3] = pRotate - 2;
			}

			// _height + _width should be enought
			// 
			// PathNode stores distance in r field during evaluations
			// PathNode stores rotation in r field after evaluations
			// 
			// PathNode stores step in s field
			// PathNode stores coordinates in x, y fields
			// 
			// mStep is stored in PathNode s field during evaluations
			PathNode pathNode = new PathNode(_height + _width, mStep, x1, y1, idx);

			/**
			 * Let first tile rotate any direction
			*/
			final int aRotate = pathNodes.size() == 0 ? -1 : mRotate;
			pMap[idx] = true;

			final boolean energy = aMap[idx] == ENERGY;
			final int index = eMap[idx];

			final int xAbs = Math.abs(x1 - x2);
			final int yAbs = Math.abs(y1 - y2);

			for (int i = 0; i < 4; ++i)
				switch (evaluateRotateOrder[i])
				{
					case RIGHT:
					{
						if (aRotate != LEFT)
						{
							final int xMax = x1 + 10;
							final int xMin = x1 + 1;

							for (int ix = xMin, ii = idx + 1; ix <= xMax; ++ix, ++ii)
								if (ix < _width && (!energy || aMap[ii] != ENERGY || (ix == xMin && eMap[ii] == index)))
								{
									final int distance = Math.abs(ix - x2) + yAbs;

									if (pathNode.r > distance && !pMap[ii])
									{
										pathNode.r = distance;
										pathNode.s = ix - x1;
										mRotate = RIGHT;
									}

									if (aMap[ii] == ENERGY)
										break;
								}
								else
									break;
						}

						break;
					}

					case UPPER:
					{
						if (aRotate != BOTTOM)
						{
							final int yMax = y1 + 10;
							final int yMin = y1 + 1;

							for (int iy = yMin, ii = idx + _width; iy <= yMax; ++iy, ii += _width)
								if (iy < _height && (!energy || aMap[ii] != ENERGY || (iy == yMin && eMap[ii] == index)))
								{
									final int distance = xAbs + Math.abs(iy - y2);

									if (pathNode.r > distance && !pMap[ii])
									{
										pathNode.r = distance;
										pathNode.s = iy - y1;
										mRotate = UPPER;
									}

									if (aMap[ii] == ENERGY)
										break;
								}
								else
									break;
						}

						break;
					}

					case LEFT:
					{
						if (aRotate != RIGHT)
						{
							final int xMax = x1 - 1;
							final int xMin = x1 - 10;

							for (int ix = xMax, ii = idx - 1; ix >= xMin; --ix, --ii)
								if (ix >= 0 && (!energy || aMap[ii] != ENERGY || (ix == xMax && eMap[ii] == index)))
								{
									final int distance = Math.abs(ix - x2) + yAbs;

									if (pathNode.r > distance && !pMap[ii])
									{
										pathNode.r = distance;
										pathNode.s = x1 - ix;
										mRotate = LEFT;
									}

									if (aMap[ii] == ENERGY)
										break;
								}
								else
									break;
						}

						break;
					}

					case BOTTOM:
					{
						if (aRotate != UPPER)
						{
							final int yMax = y1 - 1;
							final int yMin = y1 - 10;

							for (int iy = yMax, ii = idx - _width; iy >= yMin; --iy, ii -= _width)
								if (iy >= 0 && (!energy || aMap[ii] != ENERGY || (iy == yMax && eMap[ii] == index)))
								{
									final int distance = xAbs + Math.abs(iy - y2);

									if (pathNode.r > distance && !pMap[ii])
									{
										pathNode.r = distance;
										pathNode.s = y1 - iy;
										mRotate = BOTTOM;
									}

									if (aMap[ii] == ENERGY)
										break;
								}
								else
									break;
						}

						break;
					}

					default:
						break;
				}

			// If no path found get back to previous position or return failure
			if (mRotate == -1)
			{
				if (pathNodes.size() == 0)
					return null;
				else
				{
					pathNodes.remove(pathNodes.size() - 1);

					if (pathNodes.size() == 0)
						pRotate = dRotate;
					else
						pRotate = pathNodes.get(pathNodes.size() - 1).r;
				}
			}
			// Else save position and get ahead
			else
			{
				pathNode.r = mRotate;
				mStep = pathNode.s;

				pRotate = mRotate;

				pathNodes.add(pathNode);

				switch (mRotate)
				{
					case RIGHT:
						x1 += mStep;
						break;

					case UPPER:
						y1 += mStep;
						break;

					case LEFT:
						x1 -= mStep;
						break;

					case BOTTOM:
						y1 -= mStep;
						break;

					default:
						break;
				}
			}
		}

		// Fill indices map
		for (int i = 0; i < pathNodes.size(); ++i)
			iMap[pathNodes.get(i).i] = i;

		// Path reduction
		for (int i = 0; i < pathNodes.size(); ++i)
		{
			final PathNode pathNode = pathNodes.get(i);
			final int idx3 = pathNode.i;

			final boolean energy = aMap[idx3] == ENERGY;
			final int index = eMap[idx3];

			int j = i;

			int max = pathNode.x + 10;
			int min = pathNode.x + 1;

			for (int ix = min, ii = idx3 + 1; ix <= max; ++ix, ++ii)
				if (ix < _width && (!energy || aMap[ii] != ENERGY || (ix == min && eMap[ii] == index)))
				{
					int jj = iMap[ii];

					if (j < jj)
						j = jj;

					if (aMap[ii] == ENERGY)
						break;
				}
				else
					break;

			max = pathNode.y + 10;
			min = pathNode.y + 1;

			for (int iy = min, ii = idx3 + _width; iy <= max; ++iy, ii += _width)
				if (iy < _height && (!energy || aMap[ii] != ENERGY || (iy == min && eMap[ii] == index)))
				{
					int jj = iMap[ii];

					if (j < jj)
						j = jj;

					if (aMap[ii] == ENERGY)
						break;
				}
				else
					break;

			max = pathNode.x - 1;
			min = pathNode.x - 10;

			for (int ix = max, ii = idx3 - 1; ix >= min; --ix, --ii)
				if (ix >= 0 && (!energy || aMap[ii] != ENERGY || (ix == max && eMap[ii] == index)))
				{
					int jj = iMap[ii];

					if (j < jj)
						j = jj;

					if (aMap[ii] == ENERGY)
						break;
				}
				else
					break;

			max = pathNode.y - 1;
			min = pathNode.y - 10;

			for (int iy = max, ii = idx3 - _width; iy >= min; --iy, ii -= _width)
				if (iy >= 0 && (!energy || aMap[ii] != ENERGY || (iy == max && eMap[ii] == index)))
				{
					int jj = iMap[ii];

					if (j < jj)
						j = jj;

					if (aMap[ii] == ENERGY)
						break;
				}
				else
					break;

			if (i != j)
				i = j - 1;

			if (aMap[idx3] != ENERGY)
				buildPath.addLast(new BuildPlan(pathNode.x, pathNode.y, 0, Blocks.beamNode));
		}

		return buildPath;
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
		for (int y = 0, i = 0; y < _height; ++y)
			for (int x = 0; x < _width; ++x, ++i)
			{
				final Tile tile = tiles.geti(i);
				final Block block = tile.block();
				final Building build = tile.build;

				if (map[i])
				{
					if (block.hasPower && build != null && build.team == team)
					{
						// Check one beam tower building only once (build.tile == tile)
						if (block == Blocks.beamTower && build.tile == tile)
							ProcessTower(x, y, i);

						_map[i] = ENERGY;
						eMap[i] = build.tile.x + build.tile.y * _width;
					}
					else
						_map[i] = BLOCK;
				}
				else
					_map[i] = EMPTY;
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

				if (block.size == 1)
				{
					final int idx = buildPlan.x + buildPlan.y * _width;

					if (block.hasPower)
						_map[idx] = ENERGY;
					else
						_map[idx] = BLOCK;
				}
				else
				{
					final int x1 = buildPlan.x + block.sizeOffset;
					final int x2 = x1 + block.size;

					final int y1 = buildPlan.y + block.sizeOffset;
					final int y2 = y1 + block.size;

					final int step = _width + x1 - x2;

					if (block.hasPower)
					{
						final int idx = buildPlan.x + buildPlan.y * _width;

						for (int y = y1, i = x1 + y1 * _width; y < y2; ++y, i += step)
							for (int x = x1; x < x2; ++x, ++i)
							{
								_map[i] = ENERGY;
								eMap[i] = idx;
							}

						if (block == Blocks.beamTower)
							ProcessTower(buildPlan.x, buildPlan.y, idx);
					}
					else
						for (int y = y1, i = x1 + y1 * _width; y < y2; ++y, i += step)
							for (int x = x1; x < x2; ++x, ++i)
								_map[i] = BLOCK;
				}
			}
	}
}