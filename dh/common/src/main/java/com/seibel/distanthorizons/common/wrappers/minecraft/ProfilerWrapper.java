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

package com.seibel.distanthorizons.common.wrappers.minecraft;

import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;

import net.minecraft.profiler.Profiler;

public class ProfilerWrapper implements IProfilerWrapper
{
	public Profiler profiler;



	public ProfilerWrapper(Profiler newProfiler)
	{ this.profiler = newProfiler; }



	@Override
	public IProfileBlock push(String newSection)
	{
		this.profiler.startSection(newSection);
		return new ProfileBlock(this.profiler);
	}

	@Override
	public void popPush(String newSection)
	{
		this.profiler.endStartSection(newSection);
	}



	//================//
	// helper classes //
	//================//
	//region

	public static class ProfileBlock implements IProfileBlock
	{
		public Profiler profiler;

		public ProfileBlock(Profiler newProfiler)
		{ this.profiler = newProfiler; }

		@Override
		public void close()
		{
			this.profiler.endSection();
		}
	}

	//endregion



}
