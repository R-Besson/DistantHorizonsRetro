/*
 * This file is part of the Distant Horizons mod
 * licensed under the GNU LGPL v3 License.
 * Copyright (C) 2020 James Seibel
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.forge17;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.util.ProxyUtil;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.gui.DhScreenUtil;
import com.seibel.distanthorizons.common.wrappers.gui.GetConfigScreen;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.util.KeyCodesUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * This handles all events sent to the client.
 */
public class ForgeClientProxy implements AbstractModInitializer.IEventProxy 
{

    private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
    private static final DhLogger LOGGER = new DhLoggerBuilder().build();

	// unbound by default, modpacks like GTNH already use most keys
	private static final String KEY_CATEGORY = "distanthorizons.key.category";
	private static final KeyBinding TOGGLE_RENDERING_KEY = new KeyBinding("distanthorizons.key.toggleRendering", Keyboard.KEY_NONE, KEY_CATEGORY);
	private static final KeyBinding OPEN_CONFIG_KEY = new KeyBinding("distanthorizons.key.openConfig", Keyboard.KEY_NONE, KEY_CATEGORY);

	
	//=======//
	// setup //
	//=======//
	//region
	
    @Override
    public void registerEvents()
    {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);

		ClientRegistry.registerKeyBinding(TOGGLE_RENDERING_KEY);
		ClientRegistry.registerKeyBinding(OPEN_CONFIG_KEY);

        ForgePluginPacketSender.setPacketHandler((player, message) -> 
        {
            ClientApi.INSTANCE.pluginMessageReceived(message);
            ServerApi.INSTANCE.pluginMessageReceived(player, message);
        });
    }
	
	//endregion
	
	
	
    //==============//
    // chunk events //
    //==============//
	//region
	
    @SubscribeEvent
    public void clickBlockEvent(PlayerInteractEvent event)
    {
        // RIGHT_CLICK_AIR has no block position (x/z are always 0)
        if (MC.clientConnectedToDedicatedServer()
	        && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR)
		{
            ILevelWrapper wrappedLevel = ProxyUtil.getLevelWrapper(event.world);
            if (SharedApi.isChunkAtBlockPosAlreadyUpdating(wrappedLevel, event.x, event.z)) 
			{
                return;
            }
			
            Minecraft.getMinecraft()
	            // add to render thread executor
                .addScheduledTask(() -> 
                {
	                World level = event.world;
                    Chunk chunk = level.getChunkFromBlockCoords(event.x, event.z);
                    ChunkWrapper chunkWrapper = new ChunkWrapper(chunk, wrappedLevel);
                    SharedApi.INSTANCE.applyChunkUpdate(chunkWrapper, wrappedLevel, true);
                });
        }
    }
	
	public static void onClientChunkFilled(Chunk chunk)
	{
		if (!MC.clientConnectedToDedicatedServer())
		{
			return;
		}
		
		World level = chunk.worldObj;
		if (level == null || !level.isRemote)
		{
			return;
		}
		
		if (!ChunkWrapper.canSaveChunk(chunk))
		{
			LOGGER.info(
				"Skipping client chunk update for not-ready chunk [" + chunk.xPosition + "," + chunk.zPosition + "].");
			return;
		}
		
		ILevelWrapper wrappedLevel = ProxyUtil.getLevelWrapper(level);
		ChunkWrapper chunkWrapper = new ChunkWrapper(chunk, wrappedLevel);
		SharedApi.INSTANCE.applyChunkUpdate(chunkWrapper, wrappedLevel, true);
	}
	
	//endregion
	
	
	
    //==============//
    // key bindings //
    //==============//
	//region

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null)
		{
            return;
        }

		// isPressed() consumes one press each call, so a held key only triggers once
		while (TOGGLE_RENDERING_KEY.isPressed())
		{
			boolean enabled = !Config.Client.quickEnableRendering.get();
			Config.Client.quickEnableRendering.set(enabled);
			MC.sendOverlayMessage(I18n.format(enabled
				? "distanthorizons.key.toggleRendering.enabled"
				: "distanthorizons.key.toggleRendering.disabled"));
		}

		while (OPEN_CONFIG_KEY.isPressed())
		{
			DhScreenUtil.setScreen(GetConfigScreen.getScreen(null));
		}

		// the core's debug keys (F6-F8) use GLFW key codes
		if (Keyboard.getEventKeyState())
		{
			switch (Keyboard.getEventKey())
			{
				case Keyboard.KEY_F6: ClientApi.INSTANCE.keyPressedEvent(KeyCodesUtil.F6); break;
				case Keyboard.KEY_F7: ClientApi.INSTANCE.keyPressedEvent(KeyCodesUtil.F7); break;
				case Keyboard.KEY_F8: ClientApi.INSTANCE.keyPressedEvent(KeyCodesUtil.F8); break;
			}
		}
    }
	
	//endregion
	
	
	
    //===========//
    // rendering //
    //===========//
	//region

    @SubscribeEvent
    public void afterLevelRenderEvent(TickEvent.RenderTickEvent event)
    {
	    if (event.type.equals(TickEvent.RenderTickEvent.Type.RENDER))
	    {
		    boolean framebufferMixinWasEnabled = MixinFlags.framebufferMixinEnabled;
		    MixinFlags.framebufferMixinEnabled = true;
            try 
            {
                if (!framebufferMixinWasEnabled) 
				{
                    // The splash screen may have created MC's main framebuffer before our redirect was enabled.
                    // Rebuild it once after the first real render tick so the depth attachment becomes a texture.
                    Framebuffer framebuffer = Minecraft.getMinecraft()
                        .getFramebuffer();
                    if (framebuffer != null) 
					{
                        framebuffer.createBindFramebuffer(framebuffer.framebufferWidth, framebuffer.framebufferHeight);
                    }
                }
            } 
			catch (Throwable e) 
			{
                LOGGER.error("Unexpected error in afterLevelRenderEvent: [" + e.getMessage() + "].", e);
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) 
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.isCanceled()
	        || !mc.gameSettings.showDebugInfo)
        {
			return;
        }

        F3Screen.addStringToDisplay(event.right);
    }
	
	//endregion
	
	
	
}
