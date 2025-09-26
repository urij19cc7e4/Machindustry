package machindustry;

public class PathTask
{
	/**
	 * First point Point object if type is A -> B
	*/
	public final Object o1;

	/**
	 * Second point Point object if type is A -> B
	*/
	public final Object o2;

	/**
	 * Path type
	*/
	public final PathType type;

	public PathTask(Object o1, Object o2, PathType type)
	{
		this.o1 = o1;
		this.o2 = o2;
		this.type = type;
	}

	public enum PathType
	{
		BEAM,
		LIQUID,
		SOLID,
		VENT
	}
}