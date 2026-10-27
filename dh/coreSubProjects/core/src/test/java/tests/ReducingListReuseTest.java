package tests;

import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.util.RenderDataPointReducingList;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Assert;
import org.junit.Test;

/**
 * The reducing list is reused for every column of a data source,
 * an empty or all air column (IE a void dimension) mustn't keep the previous column's size.
 */
public class ReducingListReuseTest
{
	@Test
	public void emptyColumnAfterFullColumn()
	{
		try (RenderDataPointReducingList reducingList = new RenderDataPointReducingList();
			ColumnRenderView fullColumn = ColumnRenderView.getPooled();
			ColumnRenderView airColumn = ColumnRenderView.getPooled();
			ColumnRenderView output = ColumnRenderView.getPooled())
		{
			// separated blocks, top to bottom, so air is inserted between them
			LongArrayList fullData = new LongArrayList();
			for (int i = 0; i < 20; i++)
			{
				int maxY = 200 - (i * 3);
				fullData.add(RenderDataPointUtil.createDataPoint(255, 100, 100, 100, maxY, maxY - 1, 15, 0, (byte) 0));
			}
			fullColumn.populate(fullData, new LongArrayList(new long[1]), fullData.size(), 0, 0, fullData.size());

			LongArrayList airData = new LongArrayList();
			for (int i = 0; i < 20; i++)
			{
				airData.add(RenderDataPointUtil.EMPTY_DATA);
			}
			airColumn.populate(airData, new LongArrayList(new long[1]), airData.size(), 0, 0, airData.size());

			LongArrayList outputData = new LongArrayList(new long[4]);
			output.populate(outputData, new LongArrayList(new long[1]), outputData.size(), 0, 0, outputData.size());

			reducingList.populate(fullColumn);
			reducingList.reduce(4);
			reducingList.copyTo(output);

			reducingList.populate(airColumn);
			Assert.assertEquals(0, reducingList.getSizeWithAir());
			Assert.assertEquals(0, reducingList.getSizeWithoutAir());
			reducingList.reduce(4);
			reducingList.copyTo(output);
			Assert.assertEquals(RenderDataPointUtil.EMPTY_DATA, output.get(0));
		}
	}

}
