package machindustry;

import arc.math.geom.QuadTree.QuadTreeObject;
import arc.math.geom.Rect;

public class OreRegion implements QuadTreeObject
{
	/**
	 * Region map
	*/
	public final boolean[] m;

	/**
	 * Region X mass center
	*/
	public final float i;

	/**
	 * Region Y mass center
	*/
	public final float j;

	/**
	 * Region maximum mine speed. For any ore except sand it equals rss tile count. For sand it equals total efficiency
	*/
	public final float s;

	/**
	 * Region height
	*/
	public final int h;

	/**
	 * Region width
	*/
	public final int w;

	/**
	 * Region X coordinate
	*/
	public final int x;

	/**
	 * Region Y coordinate
	*/
	public final int y;

	/**
	 * Region availability. If false this region has already been built by Machindustry
	*/
	public boolean a;

	public OreRegion(boolean[] m, float i, float j, float s, int h, int w, int x, int y, boolean a)
	{
		this.m = m;
		this.i = i;
		this.j = j;
		this.s = s;
		this.h = h;
		this.w = w;
		this.x = x;
		this.y = y;
		this.a = a;
	}

	public OreRegion(OreRegion o)
	{
		m = o.m;
		i = o.i;
		j = o.j;
		s = o.s;
		h = o.h;
		w = o.w;
		x = o.x;
		y = o.y;
		a = o.a;
	}

	@Override
	public void hitbox(Rect out)
	{
		out.set((float)x, (float)y, (float)w, (float)h);
	}
}