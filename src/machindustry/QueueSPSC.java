package machindustry;

import java.util.ArrayList;

/**
 * Single-producer single-consumer queue. It does not use locks or CAS (compare and swap) operations to
 * synchronize the underlying buffer for producer and consumer threads. The producer and consumer each
 * exclusively modify its own index of underlying buffer, ensuring wait-free operation under contention.
 * The queue is bounded by the size specified at construction and does not dynamically resize.
*/
public class QueueSPSC<T>
{
	private final Object[] _data;
	private final int _size;

	// Get rid of cache contention, 64 bytes pad
	private final long _padField00 = 0;
	private final long _padField01 = 0;
	private final long _padField02 = 0;
	private final long _padField03 = 0;
	private final long _padField04 = 0;
	private final long _padField05 = 0;
	private final long _padField06 = 0;
	private final long _padField07 = 0;

	private volatile int _head = 0;

	// Get rid of cache contention, 64 bytes pad
	private final long _padField10 = 0;
	private final long _padField11 = 0;
	private final long _padField12 = 0;
	private final long _padField13 = 0;
	private final long _padField14 = 0;
	private final long _padField15 = 0;
	private final long _padField16 = 0;
	private final long _padField17 = 0;

	private volatile int _tail = 0;

	private int After(int index)
	{
		++index;

		if (index == _size)
			index = 0;

		return index;
	}

	public QueueSPSC(int size)
	{
		if (size < 0)
			throw new IllegalArgumentException("QueueSPSC size must be non-negative");

		_data = new Object[size + 1];
		_size = size + 1;
	}

	/**
	 * CAN BE CALLED BY CONSUMER THREAD ONLY
	*/
	public void Clear()
	{
		int head = _head;
		int tail = _tail;

		while (head != tail)
		{
			_head = After(head);
			_data[head] = null;

			head = _head;
			tail = _tail;
		}
	}

	/**
	 * CAN BE CALLED BY CONSUMER THREAD ONLY
	*/
	public ArrayList<T> Drain()
	{
		int head = _head;
		int tail = _tail;

		final ArrayList<T> list = new ArrayList<T>((head > tail ? _size + tail - head : tail - head) * 2);

		while (head != tail)
		{
			_head = After(head);

			list.add((T)_data[head]);
			_data[head] = null;

			head = _head;
			tail = _tail;
		}

		return list;
	}

	public boolean IsEmpty()
	{
		final int head = _head;
		final int tail = _tail;

		return head == tail;
	}

	public boolean IsFull()
	{
		final int head = _head;
		final int tail = _tail;

		return After(tail) == head;
	}

	/**
	 * CAN BE CALLED BY CONSUMER THREAD ONLY
	*/
	public T Consume()
	{
		final int head = _head;
		final int tail = _tail;

		if (head == tail)
			return null;
		else
		{
			_head = After(head);

			final T o = (T)_data[head];
			_data[head] = null;

			return o;
		}
	}

	/**
	 * CAN BE CALLED BY PRODUCER THREAD ONLY
	*/
	public boolean Produce(final T o)
	{
		final int head = _head;
		final int tail = _tail;

		final int after = After(tail);

		if (after == head)
			return false;
		else
		{
			_data[tail] = o;
			_tail = after;

			return true;
		}
	}

	public int Size()
	{
		final int head = _head;
		final int tail = _tail;

		return head > tail ? _size + tail - head : tail - head;
	}
}