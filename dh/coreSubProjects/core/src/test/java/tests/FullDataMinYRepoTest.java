package tests;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.sql.repo.FullDataSourceV2Repo;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

/**
 * The MinY column says what the data's Y values are relative to,
 * if it goes stale the data is shifted again every time it's read and saved.
 */
public class FullDataMinYRepoTest
{
	private static final String DB_FILE_NAME = "minYTest.sqlite";

	@Test
	public void minYIsUpdatedWhenARowIsOverwritten() throws Exception
	{
		File dbFile = new File(DB_FILE_NAME);
		if (dbFile.exists())
		{
			Assert.assertTrue("unable to delete old test DB File.", dbFile.delete());
		}

		try (FullDataSourceV2Repo repo = new FullDataSourceV2Repo(DataSourceRepoTests.DATABASE_TYPE, dbFile))
		{
			long pos = DhSectionPos.encode((byte) 6, 1, 2);

			// IE a world first played without Cubic Chunks
			repo.setLevelMinYGetter(() -> 0);
			repo.save(createDto(pos));
			try (FullDataSourceV2DTO savedDto = repo.getByKey(pos))
			{
				Assert.assertEquals(Integer.valueOf(0), savedDto.savedMinY);
			}

			// then with it
			repo.setLevelMinYGetter(() -> -64);
			repo.save(createDto(pos));
			try (FullDataSourceV2DTO savedDto = repo.getByKey(pos))
			{
				Assert.assertEquals(Integer.valueOf(-64), savedDto.savedMinY);
			}
		}
	}

	@Test
	public void dataFromAnotherMinYIsRegenerated() throws Exception
	{
		File dbFile = new File("genStep" + DB_FILE_NAME);
		if (dbFile.exists())
		{
			Assert.assertTrue("unable to delete old test DB File.", dbFile.delete());
		}

		try (FullDataSourceV2Repo repo = new FullDataSourceV2Repo(DataSourceRepoTests.DATABASE_TYPE, dbFile))
		{
			long pos = DhSectionPos.encode((byte) 6, 1, 2);
			repo.setLevelMinYGetter(() -> 0);
			repo.save(createDto(pos));

			ByteArrayList genSteps = new ByteArrayList();
			repo.getColumnGenerationStepForPos(pos, genSteps);
			Assert.assertEquals(3, genSteps.getByte(0));

			// IE Cubic Chunks was added, it stores the world separately so the data has to be regenerated
			repo.setLevelMinYGetter(() -> -64);
			genSteps.clear();
			repo.getColumnGenerationStepForPos(pos, genSteps);
			Assert.assertEquals(FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH, genSteps.size());
			for (int i = 0; i < genSteps.size(); i++)
			{
				Assert.assertEquals(EDhApiWorldGenerationStep.EMPTY.value, genSteps.getByte(i));
			}

			// regenerated
			repo.save(createDto(pos));
			genSteps.clear();
			repo.getColumnGenerationStepForPos(pos, genSteps);
			Assert.assertEquals(3, genSteps.getByte(0));
		}
	}

	private static FullDataSourceV2DTO createDto(long pos) throws Exception
	{
		LongArrayList[] dataArray = new LongArrayList[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];
		for (int i = 0; i < dataArray.length; i++)
		{
			dataArray[i] = new LongArrayList();
			dataArray[i].add(FullDataPointUtil.encode(1, 1, 64, (byte) 0, (byte) 15));
		}

		byte[] columnGenStep = new byte[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];
		Arrays.fill(columnGenStep, (byte) 3);
		byte[] columnWorldCompressionMode = new byte[FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH];

		FullDataSourceV2 dataSource = FullDataSourceV2.createWithData(pos, new FullDataPointIdMap(pos), dataArray, columnGenStep, columnWorldCompressionMode);
		return FullDataSourceV2DTO.CreateFromDataSource(dataSource, EDhApiDataCompressionMode.LZMA2);
	}
}
