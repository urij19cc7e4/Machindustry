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
	 * Plan epoch
	*/
	public final long planEpoch;

	/**
	 * Task epoch
	*/
	public final long taskEpoch;

	/**
	 * Path type
	*/
	public final PathType type;

	public PathTask(Object o1, Object o2, long planEpoch, long taskEpoch, PathType type)
	{
		this.o1 = o1;
		this.o2 = o2;
		this.planEpoch = planEpoch;
		this.taskEpoch = taskEpoch;
		this.type = type;
	}

	public PathTask(PathTask o)
	{
		o1 = o.o1;
		o2 = o.o2;
		planEpoch = o.planEpoch;
		taskEpoch = o.taskEpoch;
		type = o.type;
	}

	public enum PathType
	{
		BEAM,
		LIQUID,
		SOLID,
		VENT
	}
}