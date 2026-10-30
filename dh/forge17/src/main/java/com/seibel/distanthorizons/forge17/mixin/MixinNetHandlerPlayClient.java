package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.forge17.ForgeClientProxy;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient
{
	@Shadow
	private WorldClient clientWorldController;



	@Inject(method = "handleJoinGame", at = @At("RETURN"))
	private void connect(S01PacketJoinGame packetIn, CallbackInfo ci)
	{ ClientApi.INSTANCE.onClientOnlyConnected(); }

	@Inject(method = "cleanup", at = @At("RETURN"))
	private void disconnect(CallbackInfo ci)
	{ ClientApi.INSTANCE.onClientOnlyDisconnected(); }

	// blocks changed by the server (players breaking/placing, machines, etc.) update the LODs of client only DH
	@Inject(method = "handleBlockChange", at = @At("RETURN"))
	private void blockChanged(S23PacketBlockChange packet, CallbackInfo ci)
	{ ForgeClientProxy.onClientBlocksChanged(this.clientWorldController, packet.func_148879_d() >> 4, packet.func_148877_f() >> 4); }

	@Inject(method = "handleMultiBlockChange", at = @At("RETURN"))
	private void multiBlockChanged(S22PacketMultiBlockChange packet, CallbackInfo ci)
	{ ForgeClientProxy.onClientBlocksChanged(this.clientWorldController, packet.func_148920_c().chunkXPos, packet.func_148920_c().chunkZPos); }

}
