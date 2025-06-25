package machindustry;

public class Point
{
	/**
	 * Point X coordinate
	*/
	public int x;

	/**
	 * Point Y coordinate
	*/
	public int y;

	/**
	 * Point linear index
	*/
	public int i;

	public Point(int x, int y, int i)
	{
		this.x = x;
		this.y = y;
		this.i = i;
	}

	public Point(Point o)
	{
		x = o.x;
		y = o.y;
		i = o.i;
	}
}