package machindustry;

import java.util.ArrayList;

import arc.math.geom.QuadTree;
import arc.math.geom.QuadTree.QuadTreeObject;
import arc.math.geom.Rect;

/**
 * QuadTree that can roughly sort objects by dictance to point
*/
public class QuadTreeTraversable<T extends QuadTreeObject> extends QuadTree<T>
{
	private static <T extends QuadTreeObject> void Traverse(QuadTree<T> root, ArrayList<T> array, ArrayList<QuadTree<T>> queue)
	{
		queue.add(root);

		// Yes I hate recursion
		while (queue.size() != 0)
		{
			QuadTree<T> node = queue.remove(queue.size() - 1);

			for (int i = 0; i < node.objects.size; ++i)
				array.add(node.objects.items[i]);

			if (!node.leaf)
			{
				queue.add(node.botLeft);
				queue.add(node.botRight);
				queue.add(node.topLeft);
				queue.add(node.topRight);
			}
		}
	}

	public QuadTreeTraversable(Rect bounds)
	{
		super(bounds);
	}

	/**
	 * Traverses QuadTree's objects in ascending order of distance to given point
	 * @return    List of objects roughly sorted by distance to given point
	 * @param x - Point coordinate
	 * @param y - Point coordinate
	*/
	public ArrayList<T> Traverse(float x, float y)
	{
		// Work with base class to avoid dynamic casting
		QuadTree<T> closestChild = (QuadTree<T>)this;

		// Exactly totalObjects
		final ArrayList<T> array = new ArrayList<T>(totalObjects);

		// I know totalObjects is too much but I do not want to mess with logarithms
		final ArrayList<QuadTree<T>> nodes = new ArrayList<QuadTree<T>>(totalObjects);

		// I know totalObjects is too much but I do not want to mess with logarithms
		final ArrayList<QuadTree<T>> queue = new ArrayList<QuadTree<T>>(totalObjects);

		// Find closest child
		// Yes I hate recursion
		while (!closestChild.leaf)
		{
			nodes.add(closestChild);

			if (closestChild.botLeft.bounds.contains(x, y))
				closestChild = closestChild.botLeft;
			else if (closestChild.botRight.bounds.contains(x, y))
				closestChild = closestChild.botRight;
			else if (closestChild.topLeft.bounds.contains(x, y))
				closestChild = closestChild.topLeft;
			else if (closestChild.topRight.bounds.contains(x, y))
				closestChild = closestChild.topRight;
		}

		// Traverse objects
		// Yes I hate recursion
		while (nodes.size() != 0)
		{
			QuadTree<T> closestParent = nodes.remove(nodes.size() - 1);

			for (int i = 0; i < closestChild.objects.size; ++i)
				array.add(closestChild.objects.items[i]);

			if (closestParent.botLeft != closestChild)
				Traverse(closestParent.botLeft, array, queue);

			if (closestParent.botRight != closestChild)
				Traverse(closestParent.botRight, array, queue);

			if (closestParent.topLeft != closestChild)
				Traverse(closestParent.topLeft, array, queue);

			if (closestParent.topRight != closestChild)
				Traverse(closestParent.topRight, array, queue);

			closestChild = closestParent;
		}

		// Traverse root (this) 's objects
		for (int i = 0; i < closestChild.objects.size; ++i)
			array.add(closestChild.objects.items[i]);

		return array;
	}

	@Override
	protected QuadTree<T> newChild(Rect rect)
	{
		return new QuadTreeTraversable<T>(rect);
	}
}