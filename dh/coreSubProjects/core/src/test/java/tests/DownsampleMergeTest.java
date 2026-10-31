package tests;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Assert;
import org.junit.Test;
import testItems.wrappers.TestBiomeWrapper;
import testItems.wrappers.TestBlockStateWrapper;

import java.util.Arrays;
import java.util.Random;

/**
 * Merging a child data source into its parent reuses scratch lists, which must never leak between columns or parents. <br>
 * Merging the same data again mustn't count as a change, otherwise every update propagates to the top of the tree.
 */
public class DownsampleMergeTest
{
	private static final int COLUMN_COUNT = FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH;

	@Test
	public void mergedColumnsDontShareScratchLists() throws DataCorruptedException
	{
		Random random = new Random(7);

		long parentPosA = DhSectionPos.encode((byte) 7, 0, 0);
		long parentPosB = DhSectionPos.encode((byte) 7, 1, 0);

		FullDataSourceV2 parentA = FullDataSourceV2.createEmpty(parentPosA);
		FullDataSourceV2 parentB = FullDataSourceV2.createEmpty(parentPosB);

		// children in opposite corners of each parent
		FullDataSourceV2 childA0 = createRandomDataSource(DhSectionPos.getChildByIndex(parentPosA, 0), random);
		FullDataSourceV2 childA3 = createRandomDataSource(DhSectionPos.getChildByIndex(parentPosA, 3), random);
		FullDataSourceV2 childB0 = createRandomDataSource(DhSectionPos.getChildByIndex(parentPosB, 0), random);

		Assert.assertTrue(parentA.updateFromDataSource(childA0));
		LongArrayList[] parentAAfterFirstChild = copyColumns(parentA);

		// a different parent on the same thread uses the same scratch
		Assert.assertTrue(parentB.updateFromDataSource(childB0));
		assertColumnsEqual("other parent's update changed this parent", parentAAfterFirstChild, parentA);

		// another corner of the same parent mustn't change the first corner
		Assert.assertTrue(parentA.updateFromDataSource(childA3));
		LongArrayList[] parentAAfterBothChildren = copyColumns(parentA);
		for (int relX = 0; relX < FullDataSourceV2.WIDTH / 2; relX++)
		{
			for (int relZ = 0; relZ < FullDataSourceV2.WIDTH / 2; relZ++)
			{
				int index = FullDataSourceV2.relativePosToIndex(relX, relZ);
				Assert.assertEquals("first corner changed at [" + relX + "," + relZ + "]", parentAAfterFirstChild[index], parentAAfterBothChildren[index]);
			}
		}

		// the same data again isn't a change
		Assert.assertFalse(parentA.updateFromDataSource(childA0));
		assertColumnsEqual("re-applying the same child changed the parent", parentAAfterBothChildren, parentA);

		for (int i = 0; i < COLUMN_COUNT; i++)
		{
			LongArrayList column = parentA.getColumnAtRelPos(i % FullDataSourceV2.WIDTH, i / FullDataSourceV2.WIDTH);
			if (column != null)
			{
				FullDataSourceV2.throwIfDataColumnInWrongOrder(parentPosA, column);
			}
		}
	}

	//=========//
	// helpers //
	//=========//

	private static FullDataSourceV2 createRandomDataSource(long pos, Random random) throws DataCorruptedException
	{
		FullDataPointIdMap mapping = new FullDataPointIdMap(pos);
		LongArrayList[] columns = new LongArrayList[COLUMN_COUNT];
		for (int i = 0; i < COLUMN_COUNT; i++)
		{
			columns[i] = new LongArrayList();

			int pointCount = random.nextInt(12) + 1;
			int maxY = 300;
			for (int point = pointCount; point >= 0; point--)
			{
				int height = random.nextInt(20) + 1;
				maxY -= height;

				int id = mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("b" + random.nextInt(3)), new TestBlockStateWrapper("s" + random.nextInt(6)));
				columns[i].add(FullDataPointUtil.encode(id, height, maxY, (byte) random.nextInt(LodUtil.MAX_MC_LIGHT + 1), (byte) random.nextInt(LodUtil.MAX_MC_LIGHT + 1)));
			}
		}

		byte[] generationSteps = new byte[COLUMN_COUNT];
		Arrays.fill(generationSteps, EDhApiWorldGenerationStep.FEATURES.value);
		byte[] compressionModes = new byte[COLUMN_COUNT];
		Arrays.fill(compressionModes, EDhApiWorldCompressionMode.VISUALLY_EQUAL.value);

		return FullDataSourceV2.createWithData(pos, mapping, columns, generationSteps, compressionModes);
	}

	private static LongArrayList[] copyColumns(FullDataSourceV2 dataSource)
	{
		LongArrayList[] copy = new LongArrayList[COLUMN_COUNT];
		for (int relX = 0; relX < FullDataSourceV2.WIDTH; relX++)
		{
			for (int relZ = 0; relZ < FullDataSourceV2.WIDTH; relZ++)
			{
				LongArrayList column = dataSource.getColumnAtRelPos(relX, relZ);
				copy[FullDataSourceV2.relativePosToIndex(relX, relZ)] = (column != null) ? new LongArrayList(column) : null;
			}
		}
		return copy;
	}

	private static void assertColumnsEqual(String message, LongArrayList[] expected, FullDataSourceV2 actual)
	{
		LongArrayList[] actualColumns = copyColumns(actual);
		for (int i = 0; i < COLUMN_COUNT; i++)
		{
			Assert.assertEquals(message + " at index [" + i + "]", expected[i], actualColumns[i]);
		}
	}
}
