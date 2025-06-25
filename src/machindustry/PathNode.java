package machindustry;

public class PathNode
{
	/**
	 * Path node distance/rotation
	*/
	public int r;

	/**
	 * Path node step
	*/
	public int s;

	/**
	 * Path node X coordinate
	*/
	public int x;

	/**
	 * Path node Y coordinate
	*/
	public int y;

	/**
	 * Path node linear index
	*/
	public int i;

	public PathNode(int r, int s, int x, int y, int i)
	{
		this.r = r;
		this.s = s;
		this.x = x;
		this.y = y;
		this.i = i;
	}

	public PathNode(PathNode o)
	{
		r = o.r;
		s = o.s;
		x = o.x;
		y = o.y;
		i = o.i;
	}
}