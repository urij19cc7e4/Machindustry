package machindustry;

public class OreNode
{
	/**
	 * Ore node distance
	*/
	public int d;

	/**
	 * Ore node X coordinate
	*/
	public int x;

	/**
	 * Ore node Y coordinate
	*/
	public int y;

	/**
	 * Ore node linear index
	*/
	public int i;

	public OreNode(int d, int x, int y, int i)
	{
		this.d = d;
		this.x = x;
		this.y = y;
		this.i = i;
	}

	public OreNode(OreNode o)
	{
		d = o.d;
		x = o.x;
		y = o.y;
		i = o.i;
	}
}