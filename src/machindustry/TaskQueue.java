package machindustry;

import java.util.LinkedList;

public class TaskQueue
{
	private final LinkedList<PathTask> _queue = new LinkedList<PathTask>();

	public void AddTask(PathTask task)
	{
		synchronized (_queue)
		{
			_queue.addLast(task);
		}
	}

	public boolean IsEmpty()
	{
		synchronized (_queue)
		{
			return _queue.isEmpty();
		}
	}

	public void ClearTasks()
	{
		synchronized (_queue)
		{
			_queue.clear();
		}
	}

	public PathTask RemoveTask()
	{
		synchronized (_queue)
		{
			return _queue.isEmpty() ? null : _queue.removeFirst();
		}
	}
}