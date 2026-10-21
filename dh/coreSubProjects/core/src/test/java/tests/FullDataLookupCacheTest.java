package tests;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.sql.repo.FullDataLookupCache;
import com.seibel.distanthorizons.core.sql.repo.FullDataSourceV2Repo;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

/**
 * Checks that {@link FullDataSourceV2Repo}'s lookup cache
 * never returns data from before a write.
 */
public class FullDataLookupCacheTest
{
	private static final String DB_FILE_NAME = "lookupCacheTest.sqlite";
	private static final int COLUMN_COUNT = FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH;



	@BeforeClass
	public static void testSetup()
	{
		File dbFile = new File(DB_FILE_NAME);
		if (dbFile.exists())
		{
			Assert.assertTrue("unable to delete old test DB File.", dbFile.delete());
		}
	}



	@Test
	public void repoWritesInvalidateCachedLookups() throws Exception
	{
		try (FullDataSourceV2Repo repo = new FullDataSourceV2Repo(DataSourceRepoTests.DATABASE_TYPE, new File(DB_FILE_NAME)))
		{
			long pos = DhSectionPos.encode((byte) 6, 4, 5);

			// missing, and cached as missing
			Assert.assertFalse(repo.existsWithKey(pos));
			Assert.assertFalse(repo.existsWithKey(pos));
			Assert.assertNull(repo.getAdjByPosAndDirection(pos, EDhDirection.NORTH));

			// saving must replace the cached "missing"
			repo.save(createDto(pos, (byte) 3));
			Assert.assertTrue(repo.existsWithKey(pos));
			assertGenerationSteps(repo, pos, (byte) 3);
			// read twice, the second read comes from the cache
			assertGenerationSteps(repo, pos, (byte) 3);
			try (FullDataSourceV2DTO adjDto = repo.getAdjByPosAndDirection(pos, EDhDirection.NORTH))
			{
				Assert.assertNotNull(adjDto);
			}

			// overwriting must replace the cached generation steps
			repo.save(createDto(pos, (byte) 5));
			assertGenerationSteps(repo, pos, (byte) 5);

			// deleting must forget the position
			repo.deleteWithKey(pos);
			Assert.assertFalse(repo.existsWithKey(pos));
			ByteArrayList untouchedOutput = new ByteArrayList();
			repo.getColumnGenerationStepForPos(pos, untouchedOutput);
			Assert.assertTrue("missing positions shouldn't write any generation steps", untouchedOutput.isEmpty());

			// and deleteAll forgets everything
			repo.save(createDto(pos, (byte) 4));
			assertGenerationSteps(repo, pos, (byte) 4);
			repo.deleteAll();
			Assert.assertFalse(repo.existsWithKey(pos));
		}
	}

	@Test
	public void lookupThatRacedAWriteIsNotCached()
	{
		FullDataLookupCache cache = new FullDataLookupCache();
		long pos = DhSectionPos.encode((byte) 6, 7, 8);

		// a lookup starts, a write to the same position commits, then the lookup finishes with old data
		long writeCountBeforeQuery = cache.writeCount(pos);
		cache.invalidate(pos);
		cache.putMissing(pos, writeCountBeforeQuery);
		Assert.assertNull("a lookup that raced a write must not be cached", cache.exists(pos));

		// without a write in between it is cached
		writeCountBeforeQuery = cache.writeCount(pos);
		cache.putMissing(pos, writeCountBeforeQuery);
		Assert.assertEquals(Boolean.FALSE, cache.exists(pos));

		// generation steps are copied, changing the source list doesn't change the cache
		ByteArrayList generationSteps = new ByteArrayList(new byte[] { 1, 2, 3 });
		cache.invalidate(pos);
		cache.putGenerationSteps(pos, generationSteps, cache.writeCount(pos));
		generationSteps.set(0, (byte) 9);
		ByteArrayList output = new ByteArrayList();
		Assert.assertTrue(cache.getGenerationSteps(pos, output));
		Assert.assertEquals(new ByteArrayList(new byte[] { 1, 2, 3 }), output);
	}



	//=========//
	// helpers //
	//=========//

	private static FullDataSourceV2DTO createDto(long pos, byte generationStep) throws Exception
	{
		LongArrayList[] dataPoints = new LongArrayList[COLUMN_COUNT];
		for (int i = 0; i < COLUMN_COUNT; i++)
		{
			dataPoints[i] = new LongArrayList(1);
			dataPoints[i].add(FullDataPointUtil.encode(0, 1, 0, (byte) 0, (byte) 15));
		}

		byte[] generationSteps = new byte[COLUMN_COUNT];
		Arrays.fill(generationSteps, generationStep);
		byte[] worldCompressionModes = new byte[COLUMN_COUNT];

		FullDataSourceV2 dataSource = FullDataSourceV2.createWithData(pos, new FullDataPointIdMap(pos), dataPoints, generationSteps, worldCompressionModes);
		return FullDataSourceV2DTO.CreateFromDataSource(dataSource, EDhApiDataCompressionMode.LZMA2);
	}

	private static void assertGenerationSteps(FullDataSourceV2Repo repo, long pos, byte expectedStep)
	{
		ByteArrayList output = new ByteArrayList();
		repo.getColumnGenerationStepForPos(pos, output);
		Assert.assertEquals(COLUMN_COUNT, output.size());
		for (int i = 0; i < output.size(); i++)
		{
			Assert.assertEquals("column " + i, expectedStep, output.getByte(i));
		}
	}

}
