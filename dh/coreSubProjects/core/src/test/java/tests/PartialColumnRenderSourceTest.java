package tests;

import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import org.junit.Assert;
import org.junit.Test;

/** Partial render sources reuse pooled arrays without clearing them, their own columns still have to start empty. */
public class PartialColumnRenderSourceTest
{
	private static final int SLICE_COUNT = 8;

	@Test
	public void partialSourceColumnsStartEmpty()
	{
		long pos = DhSectionPos.encode(DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL, 0, 0);

		// leave data in every column of the pooled arrays
		try (ColumnRenderSource full = ColumnRenderSource.createEmpty(pos, SLICE_COUNT))
		{
			for (int i = 0; i < full.renderDataContainer.size(); i++)
			{
				full.renderDataContainer.set(i, 12345L);
			}
		}

		// the west edge, like a neighbor to the east of the section being built
		try (ColumnRenderSource partial = ColumnRenderSource.createEmptyPartial(pos, SLICE_COUNT, 0, 1, 0, ColumnRenderSource.WIDTH);
			ColumnRenderView view = ColumnRenderView.getPooled())
		{
			for (int z = 0; z < ColumnRenderSource.WIDTH; z++)
			{
				partial.populateColumnView(view, 0, z);
				for (int i = 0; i < SLICE_COUNT; i++)
				{
					Assert.assertEquals("column 0," + z + " index " + i, 0L, view.get(i));
				}
			}

			// columns it doesn't hold can't be read
			Assert.assertThrows(IllegalArgumentException.class, () -> partial.populateColumnView(view, 1, 0));
		}
	}
}
