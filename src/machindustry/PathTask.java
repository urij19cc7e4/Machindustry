package machindustry;

public class PathTask
{
	/**
	 * Path first point X coordinate
	*/
	public final int x1;

	/**
	 * Path first point Y coordinate
	*/
	public final int y1;

	/**
	 * Path last point X coordinate
	*/
	public final int x2;

	/**
	 * Path last point Y coordinate
	*/
	public final int y2;

	/**
	 * Path type
	*/
	public final PathType type;

	public PathTask(int x1, int y1, int x2, int y2, PathType type)
	{
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.type = type;
	}

	public enum PathType
	{
		BEAM,
		LIQUID,
		SOLID
	}
}