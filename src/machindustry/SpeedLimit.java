package machindustry;

import mindustry.content.Items;
import mindustry.type.Item;

public class SpeedLimit
{
	/**
	 * One eruption drill takes 25 tiles and gives 10.41 per second
	*/
	public float BerylliumFloor = 48F;

	/**
	 * Eight small laser bores take 16 tiles and give 15 per second
	*/
	public float BerylliumWall = 24F;

	/**
	 * One eruption drill takes 25 tiles and gives 10.41 per second
	*/
	public float GraphiteFloor = 48F;

	/**
	 * Eight small laser bores take 16 tiles and give 15 per second
	*/
	public float GraphiteWall = 24F;

	/**
	 * One eruption drill takes 25 tiles and gives 4.16 per second
	*/
	public float TungstenFloor = 144F;

	/**
	 * Three large laser bores take 9 tiles and give 13.5 per second
	*/
	public float TungstenWall = 16F;

	/**
	 * One eruption drill takes 25 tiles and gives 4.16 per second
	*/
	public float ThoriumFloor = 144F;

	/**
	 * Three large laser bores take 9 tiles and give 13.5 per second
	*/
	public float ThoriumWall = 16F;

	/**
	 * This is rss speed not tile count
	*/
	public float SandWall = 24F;

	public SpeedLimit
	(
		float berylliumFloor,
		float berylliumWall,
		float graphiteFloor,
		float graphiteWall,
		float tungstenFloor,
		float tungstenWall,
		float thoriumFloor,
		float thoriumWall,
		float sandWall
	)
	{
		BerylliumFloor = berylliumFloor;
		BerylliumWall = berylliumWall;
		GraphiteFloor = graphiteFloor;
		GraphiteWall = graphiteWall;
		TungstenFloor = tungstenFloor;
		TungstenWall = tungstenWall;
		ThoriumFloor = thoriumFloor;
		ThoriumWall = thoriumWall;
		SandWall = sandWall;
	}

	public SpeedLimit(SpeedLimit o)
	{
		BerylliumFloor = o.BerylliumFloor;
		BerylliumWall = o.BerylliumWall;
		GraphiteFloor = o.GraphiteFloor;
		GraphiteWall = o.GraphiteWall;
		TungstenFloor = o.TungstenFloor;
		TungstenWall = o.TungstenWall;
		ThoriumFloor = o.ThoriumFloor;
		ThoriumWall = o.ThoriumWall;
		SandWall = o.SandWall;
	}

	public float GetSpeed(Item item, boolean floor)
	{
		if (item == Items.beryllium)
			return floor ? BerylliumFloor : BerylliumWall;
		else if (item == Items.graphite)
			return floor ? GraphiteFloor : GraphiteWall;
		else if (item == Items.tungsten)
			return floor ? TungstenFloor : TungstenWall;
		else if (item == Items.thorium)
			return floor ? ThoriumFloor : ThoriumWall;
		else if (item == Items.sand)
			return floor ? 0F : SandWall;
		else
			return 0F;
	}

	public void SetSpeed(Item item, boolean floor, float speed)
	{
		if (item == Items.beryllium)
		{
			if (floor)
				BerylliumFloor = speed;
			else
				BerylliumWall = speed;
		}
		else if (item == Items.graphite)
		{
			if (floor)
				GraphiteFloor = speed;
			else
				GraphiteWall = speed;
		}
		else if (item == Items.tungsten)
		{
			if (floor)
				TungstenFloor = speed;
			else
				TungstenWall = speed;
		}
		else if (item == Items.thorium)
		{
			if (floor)
				ThoriumFloor = speed;
			else
				ThoriumWall = speed;
		}
		else if (item == Items.sand)
		{
			if (!floor)
				SandWall = speed;
		}
	}
}