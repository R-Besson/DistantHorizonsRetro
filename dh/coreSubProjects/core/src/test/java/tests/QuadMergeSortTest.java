package tests;

import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.BufferMergeDirectionEnum;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.BufferQuad;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Random;

/** LodQuadBuilder's primitive key sorts have to merge quads exactly like the comparator sort they replaced. */
public class QuadMergeSortTest
{
	@Test
	public void primitiveSortsMatchComparatorSort() throws Exception
	{
		Constructor<LodQuadBuilder> constructor = LodQuadBuilder.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		LodQuadBuilder builder = constructor.newInstance();

		Method packedMerge = LodQuadBuilder.class.getDeclaredMethod("mergeQuadsInternal", ArrayList[].class, int.class, BufferMergeDirectionEnum.class);
		packedMerge.setAccessible(true);

		Random random = new Random(1234);
		for (int run = 0; run < 400; run++)
		{
			EDhDirection direction = EDhDirection.values()[run % 6];
			BufferMergeDirectionEnum mergeDirection = BufferMergeDirectionEnum.values()[(run / 6) % 2];

			// small position ranges and few colors so there are lots of ties and merges,
			// list sizes cover the packed index sort, the radix sort and more quads than a 16 bit index fits
			int quadCount;
			switch (run % 4)
			{
				case 0:
					quadCount = 1 + random.nextInt(100);
					break;
				case 1:
					quadCount = 1 + random.nextInt(3000);
					break;
				case 2:
					quadCount = 2000 + random.nextInt(20000);
					break;
				default:
					quadCount = (run % 20 == 3) ? 65_000 + random.nextInt(60_000) : 1 + random.nextInt(5000);
					break;
			}
			int range = 4 + random.nextInt(run % 4 == 3 ? 400 : 60);
			ArrayList<BufferQuad> packedQuads = new ArrayList<>();
			ArrayList<BufferQuad> comparatorQuads = new ArrayList<>();
			for (int i = 0; i < quadCount; i++)
			{
				short x = (short) (random.nextInt(range) - (run % 3 == 0 ? range / 2 : 0));
				short y = (short) (random.nextInt(range) - (run % 5 == 0 ? range / 2 : 0));
				short z = (short) (random.nextInt(range));
				short widthEastWest = (short) (1 + random.nextInt(2));
				short widthNorthSouth = (short) (1 + random.nextInt(2));
				int color = random.nextInt(3);
				packedQuads.add(createQuad(x, y, z, widthEastWest, widthNorthSouth, color, direction));
				comparatorQuads.add(createQuad(x, y, z, widthEastWest, widthNorthSouth, color, direction));
			}

			ArrayList<BufferQuad>[] packedList = (ArrayList<BufferQuad>[]) new ArrayList[6];
			packedList[direction.ordinal()] = packedQuads;
			long packedMergeCount = (long) packedMerge.invoke(builder, packedList, direction.ordinal(), mergeDirection);
			long comparatorMergeCount = mergeWithComparatorSort(comparatorQuads, mergeDirection);

			Assert.assertEquals("merge count, run " + run, comparatorMergeCount, packedMergeCount);
			Assert.assertEquals("quad count, run " + run, comparatorQuads.size(), packedQuads.size());
			for (int i = 0; i < packedQuads.size(); i++)
			{
				BufferQuad expected = comparatorQuads.get(i);
				BufferQuad actual = packedQuads.get(i);
				String message = "quad " + i + ", run " + run;
				Assert.assertEquals(message, expected.x, actual.x);
				Assert.assertEquals(message, expected.y, actual.y);
				Assert.assertEquals(message, expected.z, actual.z);
				Assert.assertEquals(message, expected.widthEastWest, actual.widthEastWest);
				Assert.assertEquals(message, expected.widthNorthSouthOrHeight, actual.widthNorthSouthOrHeight);
				Assert.assertEquals(message, expected.color, actual.color);
			}
		}
	}

	/** how LodQuadBuilder merged quads before its sort was replaced */
	private static long mergeWithComparatorSort(ArrayList<BufferQuad> quads, BufferMergeDirectionEnum mergeDirection)
	{
		quads.sort((objOne, objTwo) -> objOne.compare(objTwo, mergeDirection));

		long mergeCount = 0;
		ListIterator<BufferQuad> iter = quads.listIterator();
		BufferQuad currentQuad = iter.next();
		while (iter.hasNext())
		{
			BufferQuad nextQuad = iter.next();
			if (currentQuad.tryMerge(nextQuad, mergeDirection))
			{
				mergeCount++;
				iter.set(null);
			}
			else
			{
				currentQuad = nextQuad;
			}
		}
		quads.removeIf(Objects::isNull);
		return mergeCount;
	}

	private static BufferQuad createQuad(short x, short y, short z, short widthEastWest, short widthNorthSouth, int color, EDhDirection direction)
	{
		BufferQuad quad = new BufferQuad();
		quad.set(x, y, z, widthEastWest, widthNorthSouth, color, (short) 0, (byte) 0, (byte) 15, (byte) 0, direction);
		return quad;
	}
}
