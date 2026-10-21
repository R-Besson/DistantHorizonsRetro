/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package tests;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.sql.repo.FullDataSourceV2Repo;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Random;

/**
 * Saves and loads the same data with every compression mode. <br>
 * Uncompressed and ZStd data are read in bulk, LZ4 and XZ a byte at a time,
 * both have to give back exactly what was saved.
 */
public class CompressionRoundTripTest
{
	private static final String DB_FILE_NAME = "compressionRoundTripTest.sqlite";
	private static final int COLUMN_COUNT = FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH;

	private static final EDhApiDataCompressionMode[] COMPRESSION_MODES = {
		EDhApiDataCompressionMode.Z_STD_BLOCK,
		EDhApiDataCompressionMode.UNCOMPRESSED,
		EDhApiDataCompressionMode.LZ4,
		EDhApiDataCompressionMode.LZMA2,
	};



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
	public void everyCompressionModeRoundTrips() throws Exception
	{
		try (FullDataSourceV2Repo repo = new FullDataSourceV2Repo(DataSourceRepoTests.DATABASE_TYPE, new File(DB_FILE_NAME)))
		{
			for (int modeIndex = 0; modeIndex < COMPRESSION_MODES.length; modeIndex++)
			{
				EDhApiDataCompressionMode compressionMode = COMPRESSION_MODES[modeIndex];
				long pos = DhSectionPos.encode((byte) 6, modeIndex, 0);

				FullDataSourceV2 original = createRandomDataSource(pos, modeIndex);
				repo.save(FullDataSourceV2DTO.CreateFromDataSource(original, compressionMode));

				try (FullDataSourceV2DTO savedDto = repo.getByKey(pos);
					FullDataSourceV2 saved = savedDto.createUnitTestDataSource())
				{
					Assert.assertNotNull(compressionMode + ": failed to load", saved);
					Assert.assertEquals(compressionMode + ": generation steps", original.columnGenerationSteps, saved.columnGenerationSteps);
					Assert.assertEquals(compressionMode + ": world compression", original.columnWorldCompressionMode, saved.columnWorldCompressionMode);
					for (int i = 0; i < COLUMN_COUNT; i++)
					{
						Assert.assertEquals(compressionMode + ": column " + i, original.dataPoints[i], saved.dataPoints[i]);
					}
				}

				ByteArrayList generationSteps = new ByteArrayList();
				repo.getColumnGenerationStepForPos(pos, generationSteps);
				Assert.assertEquals(compressionMode + ": generation steps lookup", original.columnGenerationSteps, generationSteps);
			}
		}
	}



	/** random column heights and generation steps, so a shifted or truncated read shows up */
	private static FullDataSourceV2 createRandomDataSource(long pos, int seed) throws Exception
	{
		Random random = new Random(seed);

		LongArrayList[] dataPoints = new LongArrayList[COLUMN_COUNT];
		byte[] generationSteps = new byte[COLUMN_COUNT];
		byte[] worldCompressionModes = new byte[COLUMN_COUNT];
		for (int i = 0; i < COLUMN_COUNT; i++)
		{
			int height = random.nextInt(31) + 1;
			dataPoints[i] = new LongArrayList(height);
			for (int y = 0; y < height; y++)
			{
				dataPoints[i].add(FullDataPointUtil.encode(
					random.nextInt(64), // id
					1, // height
					y, // relative min Y
					(byte) random.nextInt(LodUtil.MAX_MC_LIGHT), // block light
					(byte) random.nextInt(LodUtil.MAX_MC_LIGHT))); // sky light
			}
			generationSteps[i] = (byte) (random.nextInt(4) + 1);
			worldCompressionModes[i] = (byte) random.nextInt(3);
		}

		return FullDataSourceV2.createWithData(pos, new FullDataPointIdMap(pos), dataPoints, generationSteps, worldCompressionModes);
	}

}
