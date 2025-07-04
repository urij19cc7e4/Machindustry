// Bridge chain terms:
// 
//     ++ -- ++          ++ -- ++          ++ -- ++
//     || >> || == == == || >> || == == == || >> ||
//     ++ -- ++          ++ -- ++          ++ -- ++
// mid-chain bridge  mid-chain bridge  end-chain bridge
// 
//     ++ -- ++
//     || >> ||
//     ++ -- ++
// end-chain bridge
// 
// First bridge is mid-chain
// Lone bridge is end-chain

// Damage system:
// 
//     ++ -- ++ ++ -- ++ ++ -- ++ ++ -- ++ ++ -- ++
//     || >> ||  DAMAGE   DAMAGE   DAMAGE   DAMAGE 
//     ++ -- ++ ++ -- ++ ++ -- ++ ++ -- ++ ++ -- ++
// 
// Every bridge creates 4 damage tiles ahead of itself

// Danger system:
// 
//              ++ -- ++
//               DANGER 
//              ++ -- ++
//     ++ -- ++ ++ -- ++ ++ -- ++
//      DANGER  ||ITEM||  DANGER 
//     ++ -- ++ ++ -- ++ ++ -- ++
//              ++ -- ++
//               DANGER 
//              ++ -- ++
// 
// Every building that can output items creates danger tiles around its perimeter

// Collide system:
// Collide = damage && danger

// Protect system:
// Every bridge base (base only) tile is protect

// Block system:
// Every solid or unbuildable tile except for the ones above is block; also used for masking

// Empty system:
// The rest are empty

package machindustry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.Tiles;

public class SolidPathFinder
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
	 * Can not through with bridge or duct
	*/
	private static final byte PROTECT = (byte)0;

	/**
	 * Can through with bridge or duct; another bridge heading to this tile; items output nearby
	*/
	private static final byte COLLIDE = (byte)1;

	/**
	 * Can through with bridge or duct; another bridge heading to this tile
	*/
	private static final byte DAMAGE = (byte)2;

	/**
	 * Can through with bridge or duct; items output nearby
	*/
	private static final byte DANGER = (byte)3;

	/**
	 * Can through with bridge but not duct
	*/
	private static final byte BLOCK = (byte)4;

	/**
	 * Can through with bridge or duct
	*/
	private static final byte EMPTY = (byte)5;

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
	 * Internal output items map
	*/
	private final boolean[] oMap;

	/**
	 * How much evaluations done before timer check
	*/
	public long Frequency = (long)-1;

	/**
	 * How much time can spent on path building, ns
	*/
	public long BuildTime = (long)-1;

	/**
	 * Evaluates the possibility of turning the path to the right and the distance to the target.
	 * Parameters are almost equal to BuildPath's local variables
	*/
	private boolean EvaluateRightRotate
	(
		final ArrayList<PathNode> pathNodes,
		final PathNode pathNode,
		final int[] bMap,
		final int[] iMap,
		final boolean[] pMap,
		final boolean[] rMap,
		final int idx,
		final int idx4,
		final int x1,
		final int y1,
		final int x2,
		final int y2,
		final int pRotate,
		final int pStep
	)
	{
		// Check bMap to prevent mixing items due to mid- and end-chain bridges input rules difference
		// Check iMap to prevent bridge loops and bridge opposite rotations
		// Check oMap to prevent mixing items due to unforseen input
		// Check pMap to prevent path nodes collision
		// Check rMap to prevent stucking in dead-end
		if (x1 + 1 < _width)
		{
			final int right_1_1 = idx + 1;
			final int right_1_4 = idx4 + 4;

			final int right_2_1 = idx + 2;
			final int right_2_4 = idx4 + 8;

			final int right_3_1 = idx + 3;
			final int right_3_4 = idx4 + 12;

			final int right_4_1 = idx + 4;
			final int right_4_4 = idx4 + 16;

			final int upper_1_1 = idx + _width;
			final int left_1_1 = idx - 1;
			final int bottom_1_1 = idx - _width;

			// Evaluate bridges only if there is block ahead to prevent full-bridge paths
			if (!pMap[right_1_1] && (!rMap[right_1_4 + RIGHT] || !rMap[right_1_4 + UPPER] || !rMap[right_1_4 + BOTTOM]) && (pStep <= 1 || bMap[idx] == 0))
			{
				// Check if items output block is behind
				// pStep == 1 is to let first tile duct be non-armored and bridge cross danger building
				if ((aMap[idx] == COLLIDE || aMap[idx] == DANGER) && pStep == 1 && x1 - 1 >= 0 && oMap[left_1_1])
					return false;

				// Check if end-chain bridge heading to another bridge
				// pStep > 1 is to ensure that previous tile is bridge
				if (pStep > 1 && ((x1 + 1 < _width && aMap[right_1_1] == PROTECT) || (x1 + 2 < _width && aMap[right_2_1] == PROTECT)
					|| (x1 + 3 < _width && aMap[right_3_1] == PROTECT) || (x1 + 4 < _width && aMap[right_4_1] == PROTECT)))
					return false;

				// Check if there is bridge ahead, pStep > 1 is to ensure that previous tile is bridge
				if (pStep > 1)
				{
					if (x1 + 1 < _width && pMap[right_1_1])
					{
						final int idx1 = iMap[right_1_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (x1 + 2 < _width && pMap[right_2_1])
					{
						final int idx1 = iMap[right_2_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (x1 + 3 < _width && pMap[right_3_1])
					{
						final int idx1 = iMap[right_3_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (x1 + 4 < _width && pMap[right_4_1])
					{
						final int idx1 = iMap[right_4_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}
				}

				final int distance = Math.abs((x1 + 1) - x2) + Math.abs(y1 - y2);

				if (pathNode.r > distance)
				{
					pathNode.r = distance;
					pathNode.s = 1;
					return true;
				}
			}
			else if (bMap[idx] == 0 && aMap[right_1_1] != PROTECT)
			{
				// Check another bridge heading to this tile
				if (aMap[idx] == COLLIDE || aMap[idx] == DAMAGE)
					return false;

				// Check if items output block is nearby
				if (aMap[idx] == DANGER)
				{
					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != BOTTOM && pStep != 0 && y1 + 1 < _height && oMap[upper_1_1])
						return false;

					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != RIGHT && pStep != 0 && x1 - 1 >= 0 && oMap[left_1_1])
						return false;

					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != UPPER && pStep != 0 && y1 - 1 >= 0 && oMap[bottom_1_1])
						return false;
				}

				// Check if bridge ahead is under damage
				if (x1 + 2 < _width && !pMap[right_2_1] && (!rMap[right_2_4 + RIGHT] || !rMap[right_2_4 + UPPER]
					|| !rMap[right_2_4 + BOTTOM]) && aMap[right_2_1] != COLLIDE && aMap[right_2_1] != DAMAGE)
				{
					final int distance = Math.abs((x1 + 2) - x2) + Math.abs(y1 - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 2;
						return true;
					}
				}
				else if (x1 + 3 < _width && !pMap[right_3_1] && (!rMap[right_3_4 + RIGHT] || !rMap[right_3_4 + UPPER]
					|| !rMap[right_3_4 + BOTTOM]) && aMap[right_3_1] != COLLIDE && aMap[right_3_1] != DAMAGE)
				{
					// Check if there is another bridge in between (1_1 check in above else if)
					if (aMap[right_2_1] == PROTECT)
						return false;

					final int distance = Math.abs((x1 + 3) - x2) + Math.abs(y1 - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 3;
						return true;
					}
				}
				else if (x1 + 4 < _width && !pMap[right_4_1] && (!rMap[right_4_4 + RIGHT] || !rMap[right_4_4 + UPPER]
					|| !rMap[right_4_4 + BOTTOM]) && aMap[right_4_1] != COLLIDE && aMap[right_4_1] != DAMAGE)
				{
					// Check if there is another bridge in between (1_1 check in above else if)
					if (aMap[right_2_1] == PROTECT || aMap[right_3_1] == PROTECT)
						return false;

					final int distance = Math.abs((x1 + 4) - x2) + Math.abs(y1 - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 4;
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Evaluates the possibility of turning the path to the upper and the distance to the target.
	 * Parameters are almost equal to BuildPath's local variables
	*/
	private boolean EvaluateUpperRotate
	(
		final ArrayList<PathNode> pathNodes,
		final PathNode pathNode,
		final int[] bMap,
		final int[] iMap,
		final boolean[] pMap,
		final boolean[] rMap,
		final int idx,
		final int idx4,
		final int x1,
		final int y1,
		final int x2,
		final int y2,
		final int pRotate,
		final int pStep
	)
	{
		// Check bMap to prevent mixing items due to mid- and end-chain bridges input rules difference
		// Check iMap to prevent bridge loops and bridge opposite rotations
		// Check oMap to prevent mixing items due to unforseen input
		// Check pMap to prevent path nodes collision
		// Check rMap to prevent stucking in dead-end
		if (y1 + 1 < _height)
		{
			final int width4 = _width * 4;

			final int upper_1_1 = idx + _width;
			final int upper_1_4 = idx4 + width4;

			final int upper_2_1 = idx + _width * 2;
			final int upper_2_4 = idx4 + width4 * 2;

			final int upper_3_1 = idx + _width * 3;
			final int upper_3_4 = idx4 + width4 * 3;

			final int upper_4_1 = idx + _width * 4;
			final int upper_4_4 = idx4 + width4 * 4;

			final int right_1_1 = idx + 1;
			final int left_1_1 = idx - 1;
			final int bottom_1_1 = idx - _width;

			// Evaluate bridges only if there is block ahead to prevent full-bridge paths
			if (!pMap[upper_1_1] && (!rMap[upper_1_4 + UPPER] || !rMap[upper_1_4 + RIGHT] || !rMap[upper_1_4 + LEFT]) && (pStep <= 1 || bMap[idx] == 0))
			{
				// Check if items output block is behind
				// pStep == 1 is to let first tile duct be non-armored and bridge cross danger building
				if ((aMap[idx] == COLLIDE || aMap[idx] == DANGER) && pStep == 1 && y1 - 1 >= 0 && oMap[bottom_1_1])
					return false;

				// Check if end-chain bridge heading to another bridge
				// pStep > 1 is to ensure that previous tile is bridge
				if (pStep > 1 && ((y1 + 1 < _height && aMap[upper_1_1] == PROTECT) || (y1 + 2 < _height && aMap[upper_2_1] == PROTECT)
					|| (y1 + 3 < _height && aMap[upper_3_1] == PROTECT) || (y1 + 4 < _height && aMap[upper_4_1] == PROTECT)))
					return false;

				// Check if there is bridge ahead, pStep > 1 is to ensure that previous tile is bridge
				if (pStep > 1)
				{
					if (y1 + 1 < _height && pMap[upper_1_1])
					{
						final int idx1 = iMap[upper_1_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (y1 + 2 < _height && pMap[upper_2_1])
					{
						final int idx1 = iMap[upper_2_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (y1 + 3 < _height && pMap[upper_3_1])
					{
						final int idx1 = iMap[upper_3_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (y1 + 4 < _height && pMap[upper_4_1])
					{
						final int idx1 = iMap[upper_4_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}
				}

				final int distance = Math.abs(x1 - x2) + Math.abs((y1 + 1) - y2);

				if (pathNode.r > distance)
				{
					pathNode.r = distance;
					pathNode.s = 1;
					return true;
				}
			}
			else if (bMap[idx] == 0 && aMap[upper_1_1] != PROTECT)
			{
				// Check another bridge heading to this tile
				if (aMap[idx] == COLLIDE || aMap[idx] == DAMAGE)
					return false;

				// Check if items output block is nearby
				if (aMap[idx] == DANGER)
				{
					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != LEFT && pStep != 0 && x1 + 1 < _width && oMap[right_1_1])
						return false;

					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != RIGHT && pStep != 0 && x1 - 1 >= 0 && oMap[left_1_1])
						return false;

					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != UPPER && pStep != 0 && y1 - 1 >= 0 && oMap[bottom_1_1])
						return false;
				}

				// Check if bridge ahead is under damage
				if (y1 + 2 < _height && !pMap[upper_2_1] && (!rMap[upper_2_4 + UPPER] || !rMap[upper_2_4 + RIGHT]
					|| !rMap[upper_2_4 + LEFT]) && aMap[upper_2_1] != COLLIDE && aMap[upper_2_1] != DAMAGE)
				{
					final int distance = Math.abs(x1 - x2) + Math.abs((y1 + 2) - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 2;
						return true;
					}
				}
				else if (y1 + 3 < _height && !pMap[upper_3_1] && (!rMap[upper_3_4 + UPPER] || !rMap[upper_3_4 + RIGHT]
					|| !rMap[upper_3_4 + LEFT]) && aMap[upper_3_1] != COLLIDE && aMap[upper_3_1] != DAMAGE)
				{
					// Check if there is another bridge in between (1_1 check in above else if)
					if (aMap[upper_2_1] == PROTECT)
						return false;

					final int distance = Math.abs(x1 - x2) + Math.abs((y1 + 3) - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 3;
						return true;
					}
				}
				else if (y1 + 4 < _height && !pMap[upper_4_1] && (!rMap[upper_4_4 + UPPER] || !rMap[upper_4_4 + RIGHT]
					|| !rMap[upper_4_4 + LEFT]) && aMap[upper_4_1] != COLLIDE && aMap[upper_4_1] != DAMAGE)
				{
					// Check if there is another bridge in between (1_1 check in above else if)
					if (aMap[upper_2_1] == PROTECT || aMap[upper_3_1] == PROTECT)
						return false;

					final int distance = Math.abs(x1 - x2) + Math.abs((y1 + 4) - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 4;
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Evaluates the possibility of turning the path to the left and the distance to the target.
	 * Parameters are almost equal to BuildPath's local variables
	*/
	private boolean EvaluateLeftRotate
	(
		final ArrayList<PathNode> pathNodes,
		final PathNode pathNode,
		final int[] bMap,
		final int[] iMap,
		final boolean[] pMap,
		final boolean[] rMap,
		final int idx,
		final int idx4,
		final int x1,
		final int y1,
		final int x2,
		final int y2,
		final int pRotate,
		final int pStep
	)
	{
		// Check bMap to prevent mixing items due to mid- and end-chain bridges input rules difference
		// Check iMap to prevent bridge loops and bridge opposite rotations
		// Check oMap to prevent mixing items due to unforseen input
		// Check pMap to prevent path nodes collision
		// Check rMap to prevent stucking in dead-end
		if (x1 - 1 >= 0)
		{
			final int left_1_1 = idx - 1;
			final int left_1_4 = idx4 - 4;

			final int left_2_1 = idx - 2;
			final int left_2_4 = idx4 - 8;

			final int left_3_1 = idx - 3;
			final int left_3_4 = idx4 - 12;

			final int left_4_1 = idx - 4;
			final int left_4_4 = idx4 - 16;

			final int right_1_1 = idx + 1;
			final int upper_1_1 = idx + _width;
			final int bottom_1_1 = idx - _width;

			// Evaluate bridges only if there is block ahead to prevent full-bridge paths
			if (!pMap[left_1_1] && (!rMap[left_1_4 + LEFT] || !rMap[left_1_4 + UPPER] || !rMap[left_1_4 + BOTTOM]) && (pStep <= 1 || bMap[idx] == 0))
			{
				// Check if items output block is behind
				// pStep == 1 is to let first tile duct be non-armored and bridge cross danger building
				if ((aMap[idx] == COLLIDE || aMap[idx] == DANGER) && pStep == 1 && x1 + 1 < _width && oMap[right_1_1])
					return false;

				// Check if end-chain bridge heading to another bridge
				// pStep > 1 is to ensure that previous tile is bridge
				if (pStep > 1 && ((x1 - 1 >= 0 && aMap[left_1_1] == PROTECT) || (x1 - 2 >= 0 && aMap[left_2_1] == PROTECT)
					|| (x1 - 3 >= 0 && aMap[left_3_1] == PROTECT) || (x1 - 4 >= 0 && aMap[left_4_1] == PROTECT)))
					return false;

				// Check if there is bridge ahead, pStep > 1 is to ensure that previous tile is bridge
				if (pStep > 1)
				{
					if (x1 - 1 >= 0 && pMap[left_1_1])
					{
						final int idx1 = iMap[left_1_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (x1 - 2 >= 0 && pMap[left_2_1])
					{
						final int idx1 = iMap[left_2_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (x1 - 3 >= 0 && pMap[left_3_1])
					{
						final int idx1 = iMap[left_3_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (x1 - 4 >= 0 && pMap[left_4_1])
					{
						final int idx1 = iMap[left_4_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}
				}

				final int distance = Math.abs((x1 - 1) - x2) + Math.abs(y1 - y2);

				if (pathNode.r > distance)
				{
					pathNode.r = distance;
					pathNode.s = 1;
					return true;
				}
			}
			else if (bMap[idx] == 0 && aMap[left_1_1] != PROTECT)
			{
				// Check another bridge heading to this tile
				if (aMap[idx] == COLLIDE || aMap[idx] == DAMAGE)
					return false;

				// Check if items output block is nearby
				if (aMap[idx] == DANGER)
				{
					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != LEFT && pStep != 0 && x1 + 1 < _width && oMap[right_1_1])
						return false;

					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != BOTTOM && pStep != 0 && y1 + 1 < _height && oMap[upper_1_1])
						return false;

					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != UPPER && pStep != 0 && y1 - 1 >= 0 && oMap[bottom_1_1])
						return false;
				}

				// Check if bridge ahead is under damage
				if (x1 - 2 >= 0 && !pMap[left_2_1] && (!rMap[left_2_4 + LEFT] || !rMap[left_2_4 + UPPER]
					|| !rMap[left_2_4 + BOTTOM]) && aMap[left_2_1] != COLLIDE && aMap[left_2_1] != DAMAGE)
				{
					final int distance = Math.abs((x1 - 2) - x2) + Math.abs(y1 - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 2;
						return true;
					}
				}
				else if (x1 - 3 >= 0 && !pMap[left_3_1] && (!rMap[left_3_4 + LEFT] || !rMap[left_3_4 + UPPER]
					|| !rMap[left_3_4 + BOTTOM]) && aMap[left_3_1] != COLLIDE && aMap[left_3_1] != DAMAGE)
				{
					// Check if there is another bridge in between (1_1 check in above else if)
					if (aMap[left_2_1] == PROTECT)
						return false;

					final int distance = Math.abs((x1 - 3) - x2) + Math.abs(y1 - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 3;
						return true;
					}
				}
				else if (x1 - 4 >= 0 && !pMap[left_4_1] && (!rMap[left_4_4 + LEFT] || !rMap[left_4_4 + UPPER]
					|| !rMap[left_4_4 + BOTTOM]) && aMap[left_4_1] != COLLIDE && aMap[left_4_1] != DAMAGE)
				{
					// Check if there is another bridge in between (1_1 check in above else if)
					if (aMap[left_2_1] == PROTECT || aMap[left_3_1] == PROTECT)
						return false;

					final int distance = Math.abs((x1 - 4) - x2) + Math.abs(y1 - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 4;
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Evaluates the possibility of turning the path to the bottom and the distance to the target.
	 * Parameters are almost equal to BuildPath's local variables
	*/
	private boolean EvaluateBottomRotate
	(
		final ArrayList<PathNode> pathNodes,
		final PathNode pathNode,
		final int[] bMap,
		final int[] iMap,
		final boolean[] pMap,
		final boolean[] rMap,
		final int idx,
		final int idx4,
		final int x1,
		final int y1,
		final int x2,
		final int y2,
		final int pRotate,
		final int pStep
	)
	{
		// Check bMap to prevent mixing items due to mid- and end-chain bridges input rules difference
		// Check iMap to prevent bridge loops and bridge opposite rotations
		// Check oMap to prevent mixing items due to unforseen input
		// Check pMap to prevent path nodes collision
		// Check rMap to prevent stucking in dead-end
		if (y1 - 1 >= 0)
		{
			final int width4 = _width * 4;

			final int bottom_1_1 = idx - _width;
			final int bottom_1_4 = idx4 - width4;

			final int bottom_2_1 = idx - _width * 2;
			final int bottom_2_4 = idx4 - width4 * 2;

			final int bottom_3_1 = idx - _width * 3;
			final int bottom_3_4 = idx4 - width4 * 3;

			final int bottom_4_1 = idx - _width * 4;
			final int bottom_4_4 = idx4 - width4 * 4;

			final int right_1_1 = idx + 1;
			final int upper_1_1 = idx + _width;
			final int left_1_1 = idx - 1;

			// Evaluate bridges only if there is block ahead to prevent full-bridge paths
			if (!pMap[bottom_1_1] && (!rMap[bottom_1_4 + BOTTOM] || !rMap[bottom_1_4 + RIGHT] || !rMap[bottom_1_4 + LEFT]) && (pStep <= 1 || bMap[idx] == 0))
			{
				// Check if items output block is behind
				// pStep == 1 is to let first tile duct be non-armored and bridge cross danger building
				if ((aMap[idx] == COLLIDE || aMap[idx] == DANGER) && pStep == 1 && y1 + 1 < _height && oMap[upper_1_1])
					return false;

				// Check if end-chain bridge heading to another bridge
				// pStep > 1 is to ensure that previous tile is bridge
				if (pStep > 1 && ((y1 - 1 >= 0 && aMap[bottom_1_1] == PROTECT) || (y1 - 2 >= 0 && aMap[bottom_2_1] == PROTECT)
					|| (y1 - 3 >= 0 && aMap[bottom_3_1] == PROTECT) || (y1 - 4 >= 0 && aMap[bottom_4_1] == PROTECT)))
					return false;

				// Check if there is bridge ahead, pStep > 1 is to ensure that previous tile is bridge
				if (pStep > 1)
				{
					if (y1 - 1 >= 0 && pMap[bottom_1_1])
					{
						final int idx1 = iMap[bottom_1_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (y1 - 2 >= 0 && pMap[bottom_2_1])
					{
						final int idx1 = iMap[bottom_2_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (y1 - 3 >= 0 && pMap[bottom_3_1])
					{
						final int idx1 = iMap[bottom_3_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}

					if (y1 - 4 >= 0 && pMap[bottom_4_1])
					{
						final int idx1 = iMap[bottom_4_1];
						final int idx0 = idx1 - 1;

						if ((idx1 >= 0 && pathNodes.get(idx1).s != 1) || (idx0 >= 0 && pathNodes.get(idx0).s != 1))
							return false;
					}
				}

				final int distance = Math.abs(x1 - x2) + Math.abs((y1 - 1) - y2);

				if (pathNode.r > distance)
				{
					pathNode.r = distance;
					pathNode.s = 1;
					return true;
				}
			}
			else if (bMap[idx] == 0 && aMap[bottom_1_1] != PROTECT)
			{
				// Check another bridge heading to this tile
				if (aMap[idx] == COLLIDE || aMap[idx] == DAMAGE)
					return false;

				// Check if items output block is nearby
				if (aMap[idx] == DANGER)
				{
					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != LEFT && pStep != 0 && x1 + 1 < _width && oMap[right_1_1])
						return false;

					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != BOTTOM && pStep != 0 && y1 + 1 < _height && oMap[upper_1_1])
						return false;

					// Bridges do not accept input from sides where another bridges connected
					// pStep != 0 is to let first tile accept any input
					if (pRotate != RIGHT && pStep != 0 && x1 - 1 >= 0 && oMap[left_1_1])
						return false;
				}

				// Check if bridge ahead is under damage
				if (y1 - 2 >= 0 && !pMap[bottom_2_1] && (!rMap[bottom_2_4 + BOTTOM] || !rMap[bottom_2_4 + RIGHT]
					|| !rMap[bottom_2_4 + LEFT]) && aMap[bottom_2_1] != COLLIDE && aMap[bottom_2_1] != DAMAGE)
				{
					final int distance = Math.abs(x1 - x2) + Math.abs((y1 - 2) - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 2;
						return true;
					}
				}
				else if (y1 - 3 >= 0 && !pMap[bottom_3_1] && (!rMap[bottom_3_4 + BOTTOM] || !rMap[bottom_3_4 + RIGHT]
					|| !rMap[bottom_3_4 + LEFT]) && aMap[bottom_3_1] != COLLIDE && aMap[bottom_3_1] != DAMAGE)
				{
					// Check if there is another bridge in between (1_1 check in above else if)
					if (aMap[bottom_2_1] == PROTECT)
						return false;

					final int distance = Math.abs(x1 - x2) + Math.abs((y1 - 3) - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 3;
						return true;
					}
				}
				else if (y1 - 4 >= 0 && !pMap[bottom_4_1] && (!rMap[bottom_4_4 + BOTTOM] || !rMap[bottom_4_4 + RIGHT]
					|| !rMap[bottom_4_4 + LEFT]) && aMap[bottom_4_1] != COLLIDE && aMap[bottom_4_1] != DAMAGE)
				{
					// Check if there is another bridge in between (1_1 check in above else if)
					if (aMap[bottom_2_1] == PROTECT || aMap[bottom_3_1] == PROTECT)
						return false;

					final int distance = Math.abs(x1 - x2) + Math.abs((y1 - 4) - y2);

					if (pathNode.r > distance)
					{
						pathNode.r = distance;
						pathNode.s = 4;
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Evaluates building items output influence on nearby tiles (only bridge)
	 * @param r - building rotation
	 * @param x - building x coordinate
	 * @param y - building y coordinate
	 * @param i - building linear index
	*/
	private void ProcessProtect(final int r, final int x, final int y, final int i)
	{
		// Make tiles ahead of bridge damage
		switch (r)
		{
			case RIGHT:
			{
				final int i_beg = i + 1;
				final int x_beg = x + 1;
				final int x_end = x + 4;

				for (int j = x_beg, k = i_beg; j <= x_end; ++j, ++k)
					if (j < _width)
					{
						if (_map[k] == PROTECT)
							return;
						else
						{
							if (_map[k] == EMPTY)
								_map[k] = DAMAGE;
							else if (_map[k] == DANGER)
								_map[k] = COLLIDE;
						}
					}
					else
						break;

				// Bridge outputs even to armored duct so make block there since it is end-chain bridge
				if (x_beg < _width)
					_map[i_beg] = BLOCK;

				break;
			}

			case UPPER:
			{
				final int i_beg = i + _width;
				final int y_beg = y + 1;
				final int y_end = y + 4;

				for (int j = y_beg, k = i_beg; j <= y_end; ++j, k += _width)
					if (j < _height)
					{
						if (_map[k] == PROTECT)
							return;
						else
						{
							if (_map[k] == EMPTY)
								_map[k] = DAMAGE;
							else if (_map[k] == DANGER)
								_map[k] = COLLIDE;
						}
					}
					else
						break;

				// Bridge outputs even to armored duct so make block there since it is end-chain bridge
				if (y_beg < _height)
					_map[i_beg] = BLOCK;

				break;
			}

			case LEFT:
			{
				final int i_beg = i - 1;
				final int x_beg = x - 1;
				final int x_end = x - 4;

				for (int j = x_beg, k = i_beg; j >= x_end; --j, --k)
					if (j >= 0)
					{
						if (_map[k] == PROTECT)
							return;
						else
						{
							if (_map[k] == EMPTY)
								_map[k] = DAMAGE;
							else if (_map[k] == DANGER)
								_map[k] = COLLIDE;
						}
					}
					else
						break;

				// Bridge outputs even to armored duct so make block there since it is end-chain bridge
				if (x_beg >= 0)
					_map[i_beg] = BLOCK;

				break;
			}

			case BOTTOM:
			{
				final int i_beg = i - _width;
				final int y_beg = y - 1;
				final int y_end = y - 4;

				for (int j = y_beg, k = i_beg; j >= y_end; --j, k -= _width)
					if (j >= 0)
					{
						if (_map[k] == PROTECT)
							return;
						else
						{
							if (_map[k] == EMPTY)
								_map[k] = DAMAGE;
							else if (_map[k] == DANGER)
								_map[k] = COLLIDE;
						}
					}
					else
						break;

				// Bridge outputs even to armored duct so make block there since it is end-chain bridge
				if (y_beg >= 0)
					_map[i_beg] = BLOCK;

				break;
			}

			default:
				break;
		}
	}

	/**
	 * Evaluates building items output influence on nearby tiles (except bridge)
	 * @param b - building block
	 * @param r - building rotation
	 * @param x - building x coordinate
	 * @param y - building y coordinate
	 * @param i - building linear index
	*/
	private void ProcessBlock(final Block b, final int r, final int x, final int y, final int i)
	{
		// (Armored) duct outputs even to armored duct so make block there
		// (Armored) duct is not output to mid- and end-chain bridge but this
		// will make pathing logic a lot more complicated because it outputs to
		// begin-chain bridge and I do not have this term so this case is ignored
		if (b.isDuct)
		{
			switch (r)
			{
				case RIGHT:
				{
					final int ii = i + 1;

					if (x < _width - 1 && _map[ii] != PROTECT)
						_map[ii] = BLOCK;

					break;
				}

				case UPPER:
				{
					final int ii = i + _width;

					if (y < _height - 1 && _map[ii] != PROTECT)
						_map[ii] = BLOCK;

					break;
				}

				case LEFT:
				{
					final int ii = i - 1;

					if (x > 0 && _map[ii] != PROTECT)
						_map[ii] = BLOCK;

					break;
				}

				case BOTTOM:
				{
					final int ii = i - _width;

					if (y > 0 && _map[ii] != PROTECT)
						_map[ii] = BLOCK;

					break;
				}

				default:
					break;
			}
		}
		// Surge duct is not output to armored duct but to non-armored duct so make danger ahead of it
		else if (b == Blocks.surgeConveyor)
		{
			switch (r)
			{
				case RIGHT:
				{
					final int ii = i + 1;

					if (x < _width - 1)
					{
						if (_map[ii] == EMPTY)
							_map[ii] = DANGER;
						else if (_map[ii] == DAMAGE)
							_map[ii] = COLLIDE;
					}

					break;
				}

				case UPPER:
				{
					final int ii = i + _width;

					if (y < _height - 1)
					{
						if (_map[ii] == EMPTY)
							_map[ii] = DANGER;
						else if (_map[ii] == DAMAGE)
							_map[ii] = COLLIDE;
					}

					break;
				}

				case LEFT:
				{
					final int ii = i - 1;

					if (x > 0)
					{
						if (_map[ii] == EMPTY)
							_map[ii] = DANGER;
						else if (_map[ii] == DAMAGE)
							_map[ii] = COLLIDE;
					}

					break;
				}

				case BOTTOM:
				{
					final int ii = i - _width;

					if (y > 0)
					{
						if (_map[ii] == EMPTY)
							_map[ii] = DANGER;
						else if (_map[ii] == DAMAGE)
							_map[ii] = COLLIDE;
					}

					break;
				}

				default:
					break;
			}
		}
		// Make connected to building tiles danger
		else
		{
			// 
			//    ++ -- ++
			//    ||tile||
			//    ++ -- ++
			//    xx xx xx
			int x1 = i + (b.sizeOffset - 1) * _width + b.sizeOffset;
			int x2 = x1 + b.size;

			if (y > -b.sizeOffset)
				for (int j = x1; j < x2; ++j)
				{
					if (_map[j] == EMPTY)
						_map[j] = DANGER;
					else if (_map[j] == DAMAGE)
						_map[j] = COLLIDE;
				}

			//    xx xx xx
			//    ++ -- ++
			//    ||tile||
			//    ++ -- ++
			// 
			if (y < _height - (b.size + b.sizeOffset))
			{
				final int dx = (b.size + 1) * _width;

				x1 += dx;
				x2 += dx;

				for (int j = x1; j < x2; ++j)
				{
					if (_map[j] == EMPTY)
						_map[j] = DANGER;
					else if (_map[j] == DAMAGE)
						_map[j] = COLLIDE;
				}
			}

			// 
			// xx ++ -- ++
			// xx ||tile||
			// xx ++ -- ++
			// 
			int y1 = i + b.sizeOffset * _width + b.sizeOffset - 1;
			int y2 = y1 + b.size * _width;

			if (x > -b.sizeOffset)
				for (int j = y1; j < y2; j += _width)
				{
					if (_map[j] == EMPTY)
						_map[j] = DANGER;
					else if (_map[j] == DAMAGE)
						_map[j] = COLLIDE;
				}

			// 
			//    ++ -- ++ xx
			//    ||tile|| xx
			//    ++ -- ++ xx
			// 
			if (x < _width - (b.size + b.sizeOffset))
			{
				final int dy = b.size + 1;

				y1 += dy;
				y2 += dy;

				for (int j = y1; j < y2; j += _width)
				{
					if (_map[j] == EMPTY)
						_map[j] = DANGER;
					else if (_map[j] == DAMAGE)
						_map[j] = COLLIDE;
				}
			}
		}
	}

	public SolidPathFinder(int height, int width)
	{
		_height = height;
		_width = width;
		_size = height * width;
		_map = new byte[_size];
		aMap = new byte[_size];
		oMap = new boolean[_size];
	}

	public SolidPathFinder(int height, int width, long freq, long time)
	{
		this(height, width);

		Frequency = freq;
		BuildTime = time;
	}

	/**
	 * Builds path for solid resources using bridges and (armored) ducts
	 * @return             List of building plans if success, null if failure
	 * @param tile1      - First tile of the path (starting coordinates)
	 * @param tile2      - Tile after the last tile of the path (destination coordinates)
	 * @param targetMode - Determines whether to keep target/previous direction settings
	*/
	public LinkedList<BuildPlan> BuildPath(Tile tile1, Tile tile2, boolean targetMode)
	{
		return BuildPath((int)tile1.x, (int)tile1.y, (int)tile2.x, (int)tile2.y, targetMode, null);
	}

	/**
	 * Builds path for solid resources using bridges and (armored) ducts
	 * @return             List of building plans if success, null if failure
	 * @param x1         - First tile of the path (starting coordinate)
	 * @param y1         - First tile of the path (starting coordinate)
	 * @param x2         - Tile after the last tile of the path (destination coordinate)
	 * @param y2         - Tile after the last tile of the path (destination coordinate)
	 * @param targetMode - Determines whether to keep target/previous direction settings
	*/
	public LinkedList<BuildPlan> BuildPath(int x1, int y1, int x2, int y2, boolean targetMode)
	{
		return BuildPath(x1, y1, x2, y2, targetMode, null);
	}

	/**
	 * Builds path for solid resources using bridges and (armored) ducts
	 * @return             List of building plans if success, null if failure
	 * @param tile1      - First tile of the path (starting coordinates)
	 * @param tile2      - Tile after the last tile of the path (destination coordinates)
	 * @param targetMode - Determines whether to keep target/previous direction settings
	 * @param masks      - Boolean map that protects tiles from pathing
	*/
	public LinkedList<BuildPlan> BuildPath(Tile tile1, Tile tile2, boolean targetMode, boolean[] masks)
	{
		return BuildPath((int)tile1.x, (int)tile1.y, (int)tile2.x, (int)tile2.y, targetMode, masks);
	}

	/**
	 * Builds path for solid resources using bridges and (armored) ducts
	 * @return             List of building plans if success, null if failure
	 * @param x1         - First tile of the path (starting coordinate)
	 * @param y1         - First tile of the path (starting coordinate)
	 * @param x2         - Tile after the last tile of the path (destination coordinate)
	 * @param y2         - Tile after the last tile of the path (destination coordinate)
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

		// Check if first tile equal to tile after last tile of path
		if (idx1 == idx2)
			return new LinkedList<BuildPlan>();

		// Check if first tile is unbuildable
		if (_map[idx1] == PROTECT || _map[idx1] == BLOCK)
			return null;

		/**
	 	 * Stores building plans constructed from path nodes
		*/
		final LinkedList<BuildPlan> buildPath = new LinkedList<BuildPlan>();

		/**
	 	 * Stores path nodes during path evaluation
		*/
		ArrayList<PathNode> pathNodes1 = new ArrayList<PathNode>(_size);

		/**
	 	 * Stores path nodes during path reduction
		*/
		ArrayList<PathNode> pathNodes2 = new ArrayList<PathNode>(_size);

		/**
	 	 * Bridges protected tiles. Stores count of bridges that are protecting tile.
		*/
		final int[] bMap = new int[_size];

		/**
	 	 * Path nodes indices map. Stores -1 or index of valid path node.
		*/
		final int[] iMap = new int[_size];

		/**
	 	 * Path nodes map. Stores false or true for valid path node.
		*/
		final boolean[] pMap = new boolean[_size];

		/**
	 	 * Path nodes rotation map: ([RIGHT][UPPER][LEFT][BOTTOM]).
		 * Does not invert when get to previous position so this map prevents from stucking in dead-end
		 * but lets algorithm to check different rotations of same path (very specific need case).
		*/
		final boolean[] rMap = new boolean[_size * 4];

		/**
	 	 * Evaluate rotate order. Yes I am greedy.
		*/
		final int[] evaluateRotateOrder = targetMode ? new int[4] : new int[3];

		// Copy tiles
		if (masks == null)
			System.arraycopy(_map, 0, aMap, 0, _size);
		// Copy masked with blocks tiles
		else
		{
			for (int i = 0; i < _size; ++i)
				if (masks[i])
					aMap[i] = _map[i] == PROTECT ? PROTECT : BLOCK;
				else
					aMap[i] = _map[i];
		}

		// Mask tile after last tile with block
		aMap[idx2] = _map[idx2] == PROTECT ? PROTECT : BLOCK;

		// Fill indices map with -1
		Arrays.fill(iMap, -1);

		// Map all blocked tiles to pMap and rMap
		// Suppose Java initialized arrays with falses already
		for (int i = 0, j = 0; i < _size; ++i, j += 4)
			if (aMap[i] == PROTECT || aMap[i] == BLOCK)
			{
				pMap[i] = true;

				rMap[j + RIGHT] = true;
				rMap[j + UPPER] = true;
				rMap[j + LEFT] = true;
				rMap[j + BOTTOM] = true;
			}

		int pRotate;
		int pStep = 1;

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
		final int dStep = pStep;

		// Path evaluation
		// Yes I hate recursion
		while (true)
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

			/**
	 		 * step == 0 for first tile to let it accept input
			*/
			final int aStep = pathNodes1.size() == 0 ? 0 : pStep;

			final int dx = x1 - x2;
			final int dy = y1 - y2;

			int idx = x1 + y1 * _width;
			int idx4 = idx * 4;

			int mRotate = -1;
			int mStep = 0;

			boolean drop = false;

			// Rotate last tile in target direction but not against previous
			// Special case for last tile to prevent building lone bridge in front of target
			// Also general algorithm would not place bridge or duct heading to bridge or block
			if (Math.abs(dx) + Math.abs(dy) == 1)
			{
				if (dx == -1)
				{
					if (pRotate != LEFT && (x1 - 1 < 0 || !oMap[idx - 1]))
					{
						pathNodes1.add(new PathNode(RIGHT, 1, x1, y1, idx));
						break;
					}
				}
				else if (dy == -1)
				{
					if (pRotate != BOTTOM && (y1 - 1 < 0 || !oMap[idx - _width]))
					{
						pathNodes1.add(new PathNode(UPPER, 1, x1, y1, idx));
						break;
					}
				}
				else if (dx == 1)
				{
					if (pRotate != RIGHT && (x1 + 1 >= _width || !oMap[idx + 1]))
					{
						pathNodes1.add(new PathNode(LEFT, 1, x1, y1, idx));
						break;
					}
				}
				else // if (dy == 1)
				{
					if (pRotate != UPPER && (y1 + 1 >= _height || !oMap[idx + _width]))
					{
						pathNodes1.add(new PathNode(BOTTOM, 1, x1, y1, idx));
						break;
					}
				}

				drop = true;
			}

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
					// Turn after bridge if it is not longer way otherwise it would go ahead until first obstacle if target is behind
					if (pStep == 1)
					{
						evaluateRotateOrder[0] = hRotate1;
						evaluateRotateOrder[1] = vRotate1;
						evaluateRotateOrder[2] = vRotate2;
						evaluateRotateOrder[3] = hRotate2;
					}
					else
					{
						evaluateRotateOrder[0] = vRotate1;
						evaluateRotateOrder[1] = vRotate2;
						evaluateRotateOrder[2] = hRotate1;
						evaluateRotateOrder[3] = hRotate2;
					}
				}
				else
				{
					// Turn after bridge if it is not longer way otherwise it would go ahead until first obstacle if target is behind
					if (pStep == 1)
					{
						evaluateRotateOrder[0] = vRotate1;
						evaluateRotateOrder[1] = hRotate1;
						evaluateRotateOrder[2] = hRotate2;
						evaluateRotateOrder[3] = vRotate2;
					}
					else
					{
						evaluateRotateOrder[0] = hRotate1;
						evaluateRotateOrder[1] = hRotate2;
						evaluateRotateOrder[2] = vRotate1;
						evaluateRotateOrder[3] = vRotate2;
					}
				}
			}
			// Else set direction to previous
			else
			{
				if (pRotate == RIGHT || pRotate == LEFT)
				{
					// Turn after bridge if it is not longer way otherwise it would go ahead until first obstacle if target is behind
					if (pStep == 1)
					{
						evaluateRotateOrder[0] = pRotate;
						evaluateRotateOrder[1] = vRotate1;
						evaluateRotateOrder[2] = vRotate2;
					}
					else
					{
						evaluateRotateOrder[0] = vRotate1;
						evaluateRotateOrder[1] = vRotate2;
						evaluateRotateOrder[2] = pRotate;
					}
				}
				else // if (pRotate == UPPER || pRotate == BOTTOM)
				{
					// Turn after bridge if it is not longer way otherwise it would go ahead until first obstacle if target is behind
					if (pStep == 1)
					{
						evaluateRotateOrder[0] = pRotate;
						evaluateRotateOrder[1] = hRotate1;
						evaluateRotateOrder[2] = hRotate2;
					}
					else
					{
						evaluateRotateOrder[0] = hRotate1;
						evaluateRotateOrder[1] = hRotate2;
						evaluateRotateOrder[2] = pRotate;
					}
				}
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

			// Reset path node index
			iMap[idx] = -1;

			// Reset path node
			pMap[idx] = false;

			// First evaluated rotation have advantage over the rest rotations
			// evaluateRotateOrder is to make machine keep target/previous rotation until obstacle
			// not stick to one rotation and create multi-lane highway that can not be bridged over
			// aStep == 0 is to let first tile rotate in four directions not three as the rest tiles
			if (!drop)
			{
				if (aStep == 0)
				{
					if (targetMode)
						for (int i = 0; i < 4; ++i)
							switch (evaluateRotateOrder[i])
							{
								case RIGHT:
									if (EvaluateRightRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = RIGHT;
									break;

								case UPPER:
									if (EvaluateUpperRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = UPPER;
									break;

								case LEFT:
									if (EvaluateLeftRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = LEFT;
									break;

								case BOTTOM:
									if (EvaluateBottomRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = BOTTOM;
									break;

								default:
									break;
							}
					else
					{
						final int[] evaluateRotateOrderEx = new int[4];

						evaluateRotateOrderEx[0] = evaluateRotateOrder[0];
						evaluateRotateOrderEx[1] = evaluateRotateOrder[1];
						evaluateRotateOrderEx[2] = evaluateRotateOrder[2];

						// Yes I do not want modulo operation here
						// Fourth rotate is first plus/minus 180 degrees
						// evaluateRotateOrderEx[3] = (evaluateRotateOrder[0] + 2) % 4
						if (evaluateRotateOrder[0] <= 1)
							evaluateRotateOrderEx[3] = evaluateRotateOrder[0] + 2;
						else
							evaluateRotateOrderEx[3] = evaluateRotateOrder[0] - 2;

						for (int i = 0; i < 4; ++i)
							switch (evaluateRotateOrderEx[i])
							{
								case RIGHT:
									if (EvaluateRightRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = RIGHT;
									break;

								case UPPER:
									if (EvaluateUpperRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = UPPER;
									break;

								case LEFT:
									if (EvaluateLeftRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = LEFT;
									break;

								case BOTTOM:
									if (EvaluateBottomRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = BOTTOM;
									break;

								default:
									break;
							}
					}
				}
				else
				{
					if (targetMode)
						for (int i = 0; i < 4; ++i)
							switch (evaluateRotateOrder[i])
							{
								case RIGHT:
									if (pRotate != LEFT && EvaluateRightRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = RIGHT;
									break;

								case UPPER:
									if (pRotate != BOTTOM && EvaluateUpperRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = UPPER;
									break;

								case LEFT:
									if (pRotate != RIGHT && EvaluateLeftRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = LEFT;
									break;

								case BOTTOM:
									if (pRotate != UPPER && EvaluateBottomRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = BOTTOM;
									break;

								default:
									break;
							}
					else
						for (int i = 0; i < 3; ++i)
							switch (evaluateRotateOrder[i])
							{
								case RIGHT:
									if (EvaluateRightRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = RIGHT;
									break;

								case UPPER:
									if (EvaluateUpperRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = UPPER;
									break;

								case LEFT:
									if (EvaluateLeftRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = LEFT;
									break;

								case BOTTOM:
									if (EvaluateBottomRotate(pathNodes1, pathNode, bMap, iMap, pMap, rMap, idx, idx4, x1, y1, x2, y2, pRotate, aStep))
										mRotate = BOTTOM;
									break;

								default:
									break;
							}
				}
			}

			// If no path found get back to previous position or return failure
			if (mRotate == -1)
			{
				// Set all possible rotations
				if (pRotate != LEFT)
					rMap[idx4 + RIGHT] = true;

				if (pRotate != BOTTOM)
					rMap[idx4 + UPPER] = true;

				if (pRotate != RIGHT)
					rMap[idx4 + LEFT] = true;

				if (pRotate != UPPER)
					rMap[idx4 + BOTTOM] = true;

				if (pathNodes1.size() == 0)
					return null;
				else
				{
					final PathNode pPathNode = pathNodes1.remove(pathNodes1.size() - 1);

					x1 = pPathNode.x;
					y1 = pPathNode.y;
					idx = pPathNode.i;

					mRotate = pPathNode.r;
					mStep = pPathNode.s;

					if (pathNodes1.size() == 0)
					{
						pRotate = dRotate;
						pStep = dStep;
					}
					else
					{
						final PathNode pPathNode1 = pathNodes1.get(pathNodes1.size() - 1);

						pRotate = pPathNode1.r;
						pStep = pPathNode1.s;

						// If node is end-chain bridge then unprotect tiles ahead
						if (mStep == 1 && pStep != 1)
							switch (mRotate)
							{
								case RIGHT:
								{
									if ((pRotate != BOTTOM && y1 + 1 < _height && oMap[idx + _width])
										|| (pRotate != RIGHT && x1 - 1 >= 0 && oMap[idx - 1])
										|| (pRotate != UPPER && y1 - 1 >= 0 && oMap[idx - _width]))
										for (int i = x1 + 1, j = idx + 1; i <= x1 + 4; ++i, ++j)
											if (i < _width)
												--bMap[j];
											else
												break;

									break;
								}

								case UPPER:
								{
									if ((pRotate != LEFT && x1 + 1 < _width && oMap[idx + 1])
										|| (pRotate != RIGHT && x1 - 1 >= 0 && oMap[idx - 1])
										|| (pRotate != UPPER && y1 - 1 >= 0 && oMap[idx - _width]))
										for (int i = y1 + 1, j = idx + _width; i <= y1 + 4; ++i, j += _width)
											if (i < _height)
												--bMap[j];
											else
												break;

									break;
								}

								case LEFT:
								{
									if ((pRotate != LEFT && x1 + 1 < _width && oMap[idx + 1])
										|| (pRotate != BOTTOM && y1 + 1 < _height && oMap[idx + _width])
										|| (pRotate != UPPER && y1 - 1 >= 0 && oMap[idx - _width]))
										for (int i = x1 - 1, j = idx - 1; i >= x1 - 4; --i, --j)
											if (i >= 0)
												--bMap[j];
											else
												break;

									break;
								}

								case BOTTOM:
								{
									if ((pRotate != LEFT && x1 + 1 < _width && oMap[idx + 1])
										|| (pRotate != BOTTOM && y1 + 1 < _height && oMap[idx + _width])
										|| (pRotate != RIGHT && x1 - 1 >= 0 && oMap[idx - 1]))
										for (int i = y1 - 1, j = idx - _width; i >= y1 - 4; --i, j -= _width)
											if (i >= 0)
												--bMap[j];
											else
												break;

									break;
								}

								default:
									break;
							}
					}
				}
			}
			// Else save position and get ahead
			else
			{
				pathNode.r = mRotate;
				mStep = pathNode.s;

				pRotate = mRotate;
				pStep = mStep;

				// Set path node index, path node and path node rotation
				iMap[idx] = pathNodes1.size();
				pMap[idx] = true;
				rMap[idx4 + mRotate] = true;

				pathNodes1.add(pathNode);

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

				// If node is end-chain bridge then protect tiles ahead
				if (mStep == 1 && pStep != 1)
					switch (mRotate)
					{
						case RIGHT:
						{
							if ((pRotate != BOTTOM && y1 + 1 < _height && oMap[idx + _width])
								|| (pRotate != RIGHT && x1 - 1 >= 0 && oMap[idx - 1])
								|| (pRotate != UPPER && y1 - 1 >= 0 && oMap[idx - _width]))
								for (int i = x1 + 1, j = idx + 1; i <= x1 + 4; ++i, ++j)
									if (i < _width)
										++bMap[j];
									else
										break;

							break;
						}

						case UPPER:
						{
							if ((pRotate != LEFT && x1 + 1 < _width && oMap[idx + 1])
								|| (pRotate != RIGHT && x1 - 1 >= 0 && oMap[idx - 1])
								|| (pRotate != UPPER && y1 - 1 >= 0 && oMap[idx - _width]))
								for (int i = y1 + 1, j = idx + _width; i <= y1 + 4; ++i, j += _width)
									if (i < _height)
										++bMap[j];
									else
										break;

							break;
						}

						case LEFT:
						{
							if ((pRotate != LEFT && x1 + 1 < _width && oMap[idx + 1])
								|| (pRotate != BOTTOM && y1 + 1 < _height && oMap[idx + _width])
								|| (pRotate != UPPER && y1 - 1 >= 0 && oMap[idx - _width]))
								for (int i = x1 - 1, j = idx - 1; i >= x1 - 4; --i, --j)
									if (i >= 0)
										++bMap[j];
									else
										break;

							break;
						}

						case BOTTOM:
						{
							if ((pRotate != LEFT && x1 + 1 < _width && oMap[idx + 1])
								|| (pRotate != BOTTOM && y1 + 1 < _height && oMap[idx + _width])
								|| (pRotate != RIGHT && x1 - 1 >= 0 && oMap[idx - 1]))
								for (int i = y1 - 1, j = idx - _width; i >= y1 - 4; --i, j -= _width)
									if (i >= 0)
										++bMap[j];
									else
										break;

							break;
						}

						default:
							break;
					}
			}
		}

		PathNode pPathNode = null;

		// Path reduction
		for (int i = 0; i < pathNodes1.size(); ++i)
		{
			int ii = -1;
			int rr = -1;

			final PathNode pathNode = pathNodes1.get(i);
			final int idx3 = pathNode.i;

			final int r1 = pathNode.r;
			final int r0 = pPathNode == null ? -1 : pPathNode.r;
			final int s1 = pathNode.s;
			final int s0 = pPathNode == null ? 1 : pPathNode.s;

			final int right = idx3 + 1;
			final int upper = idx3 + _width;
			final int left = idx3 - 1;
			final int bottom = idx3 - _width;

			final int rightIndex = pathNode.x + 1 < _width ? iMap[right] : -1;
			final int upperIndex = pathNode.y + 1 < _height ? iMap[upper] : -1;
			final int leftIndex = pathNode.x - 1 >= 0 ? iMap[left] : -1;
			final int bottomIndex = pathNode.y - 1 >= 0 ? iMap[bottom] : -1;

			if (r1 != RIGHT && r0 != LEFT && i < rightIndex && ii < rightIndex && (pathNode.x - 1 < 0 || !oMap[left]))
			{
				ii = rightIndex;
				rr = RIGHT;
			}

			if (r1 != UPPER && r0 != BOTTOM && i < upperIndex && ii < upperIndex && (pathNode.y - 1 < 0 || !oMap[bottom]))
			{
				ii = upperIndex;
				rr = UPPER;
			}

			if (r1 != LEFT && r0 != RIGHT && i < leftIndex && ii < leftIndex && (pathNode.x + 1 >= _width || !oMap[right]))
			{
				ii = leftIndex;
				rr = LEFT;
			}

			if (r1 != BOTTOM && r0 != UPPER && i < bottomIndex && ii < bottomIndex && (pathNode.y + 1 >= _height || !oMap[upper]))
			{
				ii = bottomIndex;
				rr = BOTTOM;
			}

			if (ii != -1 && s1 == 1 && s0 == 1)
			{
				pathNode.r = rr;
				i = ii - 1;
			}

			pathNodes2.add(pathNode);
			pPathNode = pathNode;
		}

		// Comment this to see what is redundant (snake-like) path
		// 
		// ++ ++ ++ ++ ++ ++ ++ ++ ++ ++ ++ ++ ++ ++
		// ++ ++ ++ ?? ?? ?? ?? ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ?? ?? ?? ?? ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ?? ?? ?? ?? ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ?? ?? ?? ?? ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ++ ++ ++ ++ ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ++ ++ ++ ++ ?? ?? ?? ?? ++ ++ ++
		// ++ AA ++ ++ ++ ++ ++ ?? ?? ?? ?? ++ BB ++
		// ++ ++ ++ ++ ++ ++ ++ ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ++ ++ ++ ++ ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ?? ?? ?? ?? ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ?? ?? ?? ?? ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ?? ?? ?? ?? ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ?? ?? ?? ?? ?? ?? ?? ?? ++ ++ ++
		// ++ ++ ++ ++ ++ ++ ++ ++ ++ ++ ++ ++ ++ ++
		// 
		// Create path from AA to BB, ++ are for empty tiles, ?? are for walls
		pathNodes1 = pathNodes2;

		PathNode pathNode = null;
		PathNode nPathNode = null;

		// Last path node case
		// Not armored if tile after last tile is the only reason of danger state
		if (pathNodes1.size() >= 2)
		{
			pathNode = pathNodes1.get(pathNodes1.size() - 1);
			nPathNode = pathNodes1.get(pathNodes1.size() - 2);

			if (pathNode.s == 1 && nPathNode.s == 1)
			{
				final int idx3 = pathNode.i;

				if ((aMap[idx3] == COLLIDE || aMap[idx3] == DANGER)
					&& ((pathNode.r != RIGHT && pathNode.r != LEFT && ((pathNode.x + 1 < _width && oMap[idx3 + 1])
					|| (pathNode.x - 1 >= 0 && oMap[idx3 - 1]))) || (pathNode.r != UPPER && pathNode.r != BOTTOM
					&& ((pathNode.y + 1 < _height && oMap[idx3 + _width]) || (pathNode.y - 1 >= 0 && oMap[idx3 - _width])))))
					buildPath.addLast(new BuildPlan(pathNode.x, pathNode.y, pathNode.r, Blocks.armoredDuct));
				else
					buildPath.addLast(new BuildPlan(pathNode.x, pathNode.y, pathNode.r, Blocks.duct));
			}
			else
				buildPath.addLast(new BuildPlan(pathNode.x, pathNode.y, pathNode.r, Blocks.ductBridge));
		}

		// Path building
		// Process in reverse order because it is safer to build
		for (int i = pathNodes1.size() - 2; i >= 1; --i)
		{
			pathNode = nPathNode;
			nPathNode = pathNodes1.get(i - 1);

			// Bridge steps are 1 (end-chain bridge), 2, 3, 4; (armored) duct step is 1
			if (pathNode.s == 1 && nPathNode.s == 1)
			{
				int idx3 = pathNode.i;

				if (aMap[idx3] == COLLIDE || aMap[idx3] == DANGER)
					buildPath.addLast(new BuildPlan(pathNode.x, pathNode.y, pathNode.r, Blocks.armoredDuct));
				else
					buildPath.addLast(new BuildPlan(pathNode.x, pathNode.y, pathNode.r, Blocks.duct));
			}
			else
				buildPath.addLast(new BuildPlan(pathNode.x, pathNode.y, pathNode.r, Blocks.ductBridge));
		}

		// First path node case
		// Not armored
		if (pathNodes1.size() >= 1)
		{
			pathNode = pathNodes1.get(0);

			if (pathNode.s == 1)
				buildPath.addLast(new BuildPlan(pathNode.x, pathNode.y, pathNode.r, Blocks.duct));
			else
				buildPath.addLast(new BuildPlan(pathNode.x, pathNode.y, pathNode.r, Blocks.ductBridge));
		}

		BuildPlan buildPlan1 = null;
		BuildPlan buildPlan2 = null;

		final ListIterator<BuildPlan> iterator = buildPath.listIterator();

		// Bridges and ducts reduction (have you ever seen 1-3 ducts between bridges in manual path building?)
		while (iterator.hasNext())
		{
			final BuildPlan buildPlan = iterator.next();

			if (buildPlan.block == Blocks.ductBridge)
			{
				BuildPlan clear = null;

				if (buildPlan2 != null)
				{
					final Tile nTile = buildPlan.tile();
					final Tile pTile = buildPlan2.tile();

					switch (buildPlan.rotation)
					{
						case RIGHT:
							if (nTile.y == pTile.y && (nTile.x + 1 == pTile.x || nTile.x + 2 == pTile.x || nTile.x + 3 == pTile.x || nTile.x + 4 == pTile.x))
								clear = buildPlan2;
							break;

						case UPPER:
							if (nTile.x == pTile.x && (nTile.y + 1 == pTile.y || nTile.y + 2 == pTile.y || nTile.y + 3 == pTile.y || nTile.y + 4 == pTile.y))
								clear = buildPlan2;
							break;

						case LEFT:
							if (nTile.y == pTile.y && (nTile.x - 1 == pTile.x || nTile.x - 2 == pTile.x || nTile.x - 3 == pTile.x || nTile.x - 4 == pTile.x))
								clear = buildPlan2;
							break;

						case BOTTOM:
							if (nTile.x == pTile.x && (nTile.y - 1 == pTile.y || nTile.y - 2 == pTile.y || nTile.y - 3 == pTile.y || nTile.y - 4 == pTile.y))
								clear = buildPlan2;
							break;

						default:
							break;
					}
				}

				if (clear != null)
					buildPlan1 = buildPlan2;
				else if (buildPlan1 != null)
				{
					final Tile nTile = buildPlan.tile();
					final Tile pTile = buildPlan1.tile();

					switch (buildPlan.rotation)
					{
						case RIGHT:
							if (nTile.y == pTile.y && (nTile.x + 1 == pTile.x || nTile.x + 2 == pTile.x || nTile.x + 3 == pTile.x || nTile.x + 4 == pTile.x))
								clear = buildPlan1;
							break;

						case UPPER:
							if (nTile.x == pTile.x && (nTile.y + 1 == pTile.y || nTile.y + 2 == pTile.y || nTile.y + 3 == pTile.y || nTile.y + 4 == pTile.y))
								clear = buildPlan1;
							break;

						case LEFT:
							if (nTile.y == pTile.y && (nTile.x - 1 == pTile.x || nTile.x - 2 == pTile.x || nTile.x - 3 == pTile.x || nTile.x - 4 == pTile.x))
								clear = buildPlan1;
							break;

						case BOTTOM:
							if (nTile.x == pTile.x && (nTile.y - 1 == pTile.y || nTile.y - 2 == pTile.y || nTile.y - 3 == pTile.y || nTile.y - 4 == pTile.y))
								clear = buildPlan1;
							break;

						default:
							break;
					}
				}

				// Yes I hate Java LinkedList iterators implementation
				if (clear != null)
				{
					iterator.previous();

					while (iterator.previous() != clear)
						iterator.remove();

					iterator.next();
					iterator.next();
				}

				buildPlan2 = buildPlan1;
				buildPlan1 = buildPlan;
			}
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

		// Divide all tiles into protect, block and empty tiles
		for (int i = 0; i < _size; ++i)
		{
			final Tile tile = tiles.geti(i);
			final Block block = tile.block();
			final Building build = tile.build;

			if (map[i])
			{
				if (block == Blocks.ductBridge && build != null && build.team == team)
					_map[i] = PROTECT;
				else
					_map[i] = BLOCK;
			}
			else
				_map[i] = EMPTY;

			oMap[i] = block.outputsItems();
		}

		// Divide empty tiles into collide, damage, danger, block and empty tiles
		// It is faster to seek buildings and bridges to divide nearby empty tiles
		// than to seek buildings and bridges around each empty tile
		for (int y = 0, i = 0; y < _height; ++y)
			for (int x = 0; x < _width; ++x, ++i)
				if (_map[i] == PROTECT || _map[i] == BLOCK)
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
						// Check blocks that output items but not payloads (like T2/T3 factories)
						if (build.tile == tile && block.outputsItems())
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

				if (block == Blocks.ductBridge)
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

					if (block.outputsItems())
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

				if (block == Blocks.ductBridge)
					ProcessProtect(buildPlan.rotation, buildPlan.x, buildPlan.y, idx);
				else if (block.outputsItems())
					ProcessBlock(block, buildPlan.rotation, buildPlan.x, buildPlan.y, idx);
			}
	}
}