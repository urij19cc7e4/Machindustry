package machindustry;

public class PathNode
{
	public int r;
	public int s;
	public int x;
	public int y;
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