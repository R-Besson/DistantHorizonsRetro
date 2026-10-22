package com.seibel.distanthorizons.forge17;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

/**
 * Handles setting up mixins
 * similar to `... mixins.json` in newer MC versions.
 */
public enum Mixins implements IMixins 
{

    CORE(
		new MixinBuilder().setPhase(Phase.EARLY)
        .addCommonMixins("MixinBiomeGenBase")),
	
    THREADED_FILE_IO_NO_SLEEP(
		new MixinBuilder().setPhase(Phase.EARLY)
        .addExcludedMod(TargetedMod.HODGEPODGE)
        .addCommonMixins("MixinThreadedFileIOBase")),
	
    CLIENT_CORE(
		new MixinBuilder().setPhase(Phase.EARLY)
        .addClientMixins(
            "MixinActiveRenderInfo",
            "MixinChunk",
            "MixinEntity",
            "MixinEntityRenderer",
            "MixinNetHandlerPlayClient",
            "MixinOptionsScreen",
            "MixinRenderGlobal",
            "MixinTesselator",
            "MixinTextureAtlasSprite",
            "MixinTextureMap")),
	
    FIX_SIDE_FACING_UNLOADED_CHUNKS_BEING_RENDERED(
		new MixinBuilder().addExcludedMod(TargetedMod.ANGELICA)
        .addClientMixins("MixinBlock_SideFacingUnloadedChunk", "IMixinChunkCache_SideFacingUnloaded")
        .setPhase(Phase.EARLY)),
	
    CLIENT_FADE(
		new MixinBuilder().setPhase(Phase.EARLY)
        .addExcludedMod(TargetedMod.ANGELICA)
        .addClientMixins("MixinFramebuffer")),

	// makes shader packs fade vanilla terrain into LODs at the real render distance
    ANGELICA_FAR_UNIFORM(
		new MixinBuilder().setPhase(Phase.EARLY)
        .addRequiredMod(TargetedMod.ANGELICA)
        .addClientMixins("angelica.MixinCameraUniforms")),

	// draws block textures on LODs with shader packs
    ANGELICA_LOD_TEXTURES(
		new MixinBuilder().setPhase(Phase.EARLY)
        .addRequiredMod(TargetedMod.ANGELICA)
        .addClientMixins("angelica.MixinTransformPatcher", "angelica.MixinIrisLodRenderProgram"));
	
	
	
    private final MixinBuilder builder;
	
    Mixins(MixinBuilder builder) { this.builder = builder; }
	
    @Override
    @NotNull
    public MixinBuilder getBuilder() { return this.builder; }
	
	
	
}
