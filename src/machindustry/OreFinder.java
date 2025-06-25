package machindustry;

import java.util.ArrayList;
import java.util.Arrays;

import arc.math.geom.Rect;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.meta.Attribute;

public class OreFinder
{
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
	 * Internal beryllium floor map
	*/
	private final QuadTreeTraversable<OreRegion> _berylliumFloorMap;

	/**
	 * Internal beryllium wall map
	*/
	private final QuadTreeTraversable<OreRegion> _berylliumWallMap;

	/**
	 * Internal graphite floor map
	*/
	private final QuadTreeTraversable<OreRegion> _graphiteFloorMap;

	/**
	 * Internal graphite wall map
	*/
	private final QuadTreeTraversable<OreRegion> _graphiteWallMap;

	/**
	 * Internal tungsten floor map
	*/
	private final QuadTreeTraversable<OreRegion> _tungstenFloorMap;

	/**
	 * Internal tungsten wall map
	*/
	private final QuadTreeTraversable<OreRegion> _tungstenWallMap;

	/**
	 * Internal thorium floor map
	*/
	private final QuadTreeTraversable<OreRegion> _thoriumFloorMap;

	/**
	 * Internal thorium wall map
	*/
	private final QuadTreeTraversable<OreRegion> _thoriumWallMap;

	/**
	 * Internal sand wall map
	*/
	private final QuadTreeTraversable<OreRegion> _sandWallMap;

	private void FloorMap
	(
		final QuadTreeTraversable<OreRegion> map,
		final boolean[] blockMap,
		final Item[] itemMap,
		final Item item,
		final int range
	)
	{
		final Tiles tiles = Vars.world.tiles;

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		final boolean[] objectMap = new boolean[_size];
		final boolean[] regionMap = new boolean[_size];

		final ArrayList<OreNode> queue = new ArrayList<OreNode>(_size);

		for (int y = 0, i = 0; y < _height; ++y)
			for (int x = 0; x < _width; ++x, ++i)
				if (!objectMap[i] && itemMap[i] == item)
				{
					int xMax = x;
					int xMin = x;

					int yMax = y;
					int yMin = y;

					Arrays.fill(regionMap, false);
					queue.add(new OreNode(0, x, y, i));

					// Yes I hate recursion
					while (queue.size() != 0)
					{
						final OreNode node = queue.remove(queue.size() - 1);

						final int nx = node.x;
						final int ny = node.y;
						final int ni = node.i;

						final int nd = itemMap[ni] == item ? 0 : node.d + 1;

						if (nx < 0 || nx >= _width || ny < 0 || ny >= _height || objectMap[ni] || nd > range)
							continue;

						objectMap[ni] = true;

						if (nd == 0 && !blockMap[ni])
							regionMap[ni] = true;

						if (xMax < nx)
							xMax = nx;

						if (xMin > nx)
							xMin = nx;

						if (yMax < ny)
							yMax = ny;

						if (yMin > ny)
							yMin = ny;

						queue.add(new OreNode(nd, nx + 1, ny + 1, ni + _width + 1));
						queue.add(new OreNode(nd, nx + 1, ny, ni + 1));
						queue.add(new OreNode(nd, nx + 1, ny - 1, ni - _width + 1));
						queue.add(new OreNode(nd, nx, ny + 1, ni + _width));
						queue.add(new OreNode(nd, nx, ny - 1, ni - _width));
						queue.add(new OreNode(nd, nx - 1, ny + 1, ni + _width - 1));
						queue.add(new OreNode(nd, nx - 1, ny, ni - 1));
						queue.add(new OreNode(nd, nx - 1, ny - 1, ni - _width - 1));
					}

					final int rh = yMax - yMin + 1;
					final int rw = xMax - xMin + 1;

					final int rx = xMin;
					final int ry = yMin;

					final int rs = rh * rw;
					final int step = _width - rw;

					int massX = 0;
					int massY = 0;
					int count = 0;

					final boolean[] localMap = new boolean[rs];

					for (int m = 0, k = 0, l = rx * ry; m < rh; ++m, l += step)
						for (int n = 0; n < rw; ++n, ++k, ++l)
						{
							localMap[k] = regionMap[l];

							massX += n;
							massY += m;
							++count;
						}

					if (count != 0)
					{
						final float floatMassX = (float)massX / (float)count + (float)rx;
						final float floatMassY = (float)massY / (float)count + (float)ry;

						map.insert(new OreRegion(localMap, floatMassX, floatMassY, (float)count, rh, rw, rx, ry, true));
					}
				}
	}

	private void WallMap
	(
		final QuadTreeTraversable<OreRegion> map,
		final boolean[] blockMap,
		final Item[] itemMap,
		final Item item,
		final int range
	)
	{
		final Tiles tiles = Vars.world.tiles;

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		final boolean[] objectMap = new boolean[_size];
		final boolean[] regionMap = new boolean[_size];

		final ArrayList<OreNode> queue = new ArrayList<OreNode>(_size);

		for (int y = 0, i = 0; y < _height; ++y)
			for (int x = 0; x < _width; ++x, ++i)
				if (!objectMap[i] && itemMap[i] == item)
				{
					int xMax = x;
					int xMin = x;

					int yMax = y;
					int yMin = y;

					Arrays.fill(regionMap, false);
					queue.add(new OreNode(0, x, y, i));

					// Yes I hate recursion
					while (queue.size() != 0)
					{
						final OreNode node = queue.remove(queue.size() - 1);

						final int nx = node.x;
						final int ny = node.y;
						final int ni = node.i;

						final int nd = itemMap[ni] == item ? 0 : node.d + 1;

						if (nx < 0 || nx >= _width || ny < 0 || ny >= _height || objectMap[ni] || nd > range)
							continue;

						objectMap[ni] = true;

						if (nd == 0 && ((nx + 1 < _width && !blockMap[ni + 1]) || (ny + 1 < _height && !blockMap[ni + _width])
							|| (nx - 1 >= 0 && !blockMap[ni - 1]) || (ny - 1 >= 0 && !blockMap[ni - _width])))
							regionMap[ni] = true;

						if (xMax < nx)
							xMax = nx;

						if (xMin > nx)
							xMin = nx;

						if (yMax < ny)
							yMax = ny;

						if (yMin > ny)
							yMin = ny;

						queue.add(new OreNode(nd, nx + 1, ny + 1, ni + _width + 1));
						queue.add(new OreNode(nd, nx + 1, ny, ni + 1));
						queue.add(new OreNode(nd, nx + 1, ny - 1, ni - _width + 1));
						queue.add(new OreNode(nd, nx, ny + 1, ni + _width));
						queue.add(new OreNode(nd, nx, ny - 1, ni - _width));
						queue.add(new OreNode(nd, nx - 1, ny + 1, ni + _width - 1));
						queue.add(new OreNode(nd, nx - 1, ny, ni - 1));
						queue.add(new OreNode(nd, nx - 1, ny - 1, ni - _width - 1));
					}

					final int rh = yMax - yMin + 1;
					final int rw = xMax - xMin + 1;

					final int rx = xMin;
					final int ry = yMin;

					final int rs = rh * rw;
					final int step = _width - rw;

					int massX = 0;
					int massY = 0;
					int count = 0;

					float efficiency = 0F;

					final boolean[] localMap = new boolean[rs];

					if (item == Items.sand)
						for (int m = 0, k = 0, l = rx * ry; m < rh; ++m, l += step)
							for (int n = 0; n < rw; ++n, ++k, ++l)
							{
								final Tile tile = tiles.geti(l);
								final Block block = tile.block();

								localMap[k] = regionMap[l];

								massX += n;
								massY += m;
								++count;

								if (tile.solid())
									efficiency += block.attributes.get(Attribute.sand);
							}
					else
						for (int m = 0, k = 0, l = rx * ry; m < rh; ++m, l += step)
							for (int n = 0; n < rw; ++n, ++k, ++l)
							{
								localMap[k] = regionMap[l];

								massX += n;
								massY += m;
								++count;
							}

					if (count != 0)
					{
						final float floatMassX = (float)massX / (float)count + (float)rx;
						final float floatMassY = (float)massY / (float)count + (float)ry;

						map.insert(new OreRegion(localMap, floatMassX, floatMassY, item == Items.sand ? efficiency : (float)count, rh, rw, rx, ry, true));
					}
				}
	}

	public OreFinder(int height, int width)
	{
		this(height, width, 0);
	}

	public OreFinder(int height, int width, int range)
	{
		final Tiles tiles = Vars.world.tiles;

		if (tiles == null)
			throw new NullPointerException("Vars.world.tiles is null");

		_height = height;
		_width = width;
		_size = height * width;

		_berylliumFloorMap = new QuadTreeTraversable<OreRegion>(new Rect(0F, 0F, (float)width, (float)height));
		_berylliumWallMap = new QuadTreeTraversable<OreRegion>(new Rect(0F, 0F, (float)width, (float)height));
		_graphiteFloorMap = new QuadTreeTraversable<OreRegion>(new Rect(0F, 0F, (float)width, (float)height));
		_graphiteWallMap = new QuadTreeTraversable<OreRegion>(new Rect(0F, 0F, (float)width, (float)height));
		_tungstenFloorMap = new QuadTreeTraversable<OreRegion>(new Rect(0F, 0F, (float)width, (float)height));
		_tungstenWallMap = new QuadTreeTraversable<OreRegion>(new Rect(0F, 0F, (float)width, (float)height));
		_thoriumFloorMap = new QuadTreeTraversable<OreRegion>(new Rect(0F, 0F, (float)width, (float)height));
		_thoriumWallMap = new QuadTreeTraversable<OreRegion>(new Rect(0F, 0F, (float)width, (float)height));
		_sandWallMap = new QuadTreeTraversable<OreRegion>(new Rect(0F, 0F, (float)width, (float)height));

		final boolean[] blockMap = new boolean[_size];

		final Item[] floorMap = new Item[_size];
		final Item[] wallMap = new Item[_size];
		final Item[] sandMap = new Item[_size];

		for (int y = 0, i = 0; y < _height; ++y)
			for (int x = 0; x < _width; ++x, ++i)
			{
				final Tile tile = tiles.geti(i);

				final Block block = tile.block();
				final Floor floor = tile.floor();

				blockMap[i] = !block.alwaysReplace || !floor.placeableOn || floor.isDeep() || Vars.world.getDarkness(x, y) >= 3;

				floorMap[i] = block.isStatic() ? null : tile.drop();
				wallMap[i] = tile.solid() ? tile.wallDrop() : null;
				sandMap[i] = tile.solid() ? block.attributes.get(Attribute.sand) > 0F ? Items.sand : null : null;
			}

		if (range < 0)
			range = 0;

		FloorMap(_berylliumFloorMap, blockMap, floorMap, Items.beryllium, range);
		WallMap(_berylliumWallMap, blockMap, wallMap, Items.beryllium, range);
		FloorMap(_graphiteFloorMap, blockMap, floorMap, Items.graphite, range);
		WallMap(_graphiteWallMap, blockMap, wallMap, Items.graphite, range);
		FloorMap(_tungstenFloorMap, blockMap, floorMap, Items.tungsten, range);
		WallMap(_tungstenWallMap, blockMap, wallMap, Items.tungsten, range);
		FloorMap(_thoriumFloorMap, blockMap, floorMap, Items.thorium, range);
		WallMap(_thoriumWallMap, blockMap, wallMap, Items.thorium, range);
		WallMap(_sandWallMap, blockMap, sandMap, Items.sand, range);
	}
}