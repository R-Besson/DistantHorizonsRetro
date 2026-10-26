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
import java.util.Random;

/** The packed key sort in LodQuadBuilder has to merge quads exactly like the comparator sort it replaced. */
public class QuadMergeSortTest
{
	@Test
	public void packedSortMatchesComparatorSort() throws Exception
	{
		Constructor<LodQuadBuilder> constructor = LodQuadBuilder.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		LodQuadBuilder builder = constructor.newInstance();

		Method packedMerge = LodQuadBuilder.class.getDeclaredMethod("mergeQuadsInternal", ArrayList[].class, int.class, BufferMergeDirectionEnum.class);
		packedMerge.setAccessible(true);
		Method comparatorMerge = LodQuadBuilder.class.getDeclaredMethod("mergeSortedQuadsWithComparator", ArrayList.class, BufferMergeDirectionEnum.class);
		comparatorMerge.setAccessible(true);

		Random random = new Random(1234);
		for (int run = 0; run < 400; run++)
		{
			EDhDirection direction = EDhDirection.values()[run % 6];
			BufferMergeDirectionEnum mergeDirection = BufferMergeDirectionEnum.values()[(run / 6) % 2];

			// small position ranges and few colors so there are lots of ties and merges
			int quadCount = 1 + random.nextInt(3000);
			int range = 4 + random.nextInt(60);
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
			long comparatorMergeCount = (long) comparatorMerge.invoke(null, comparatorQuads, mergeDirection);

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

	private static BufferQuad createQuad(short x, short y, short z, short widthEastWest, short widthNorthSouth, int color, EDhDirection direction)
	{
		BufferQuad quad = new BufferQuad();
		quad.set(x, y, z, widthEastWest, widthNorthSouth, color, (short) 0, (byte) 0, (byte) 15, (byte) 0, direction);
		return quad;
	}
}
