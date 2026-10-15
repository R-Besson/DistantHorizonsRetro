package com.seibel.distanthorizons.common;

import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingEngine;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiGeneratorPlan;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDhInitEvent;
import com.seibel.distanthorizons.common.commands.CommandInitializer;
import com.seibel.distanthorizons.common.wrappers.DependencySetup;
import com.seibel.distanthorizons.common.wrappers.minecraft.AbstractMinecraftSharedWrapper;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftClientWrapper;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftServerWrapper;
import com.seibel.distanthorizons.core.Initializer;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigHandler;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.jar.ModJarInfo;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.renderer.AbstractDebugWireframeRenderer;
import com.seibel.distanthorizons.core.render.renderer.StubDebugWireframeRenderer;
import com.seibel.distanthorizons.common.wrappers.gui.NativeDialogUtil;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IVersionConstants;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModChecker;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import com.seibel.distanthorizons.core.logging.DhLogger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.command.ServerCommandManager;

/**
 * Base for the mod loader initializer
 * and handles most setup.
 */
public abstract class AbstractModInitializer
{
	protected static final DhLogger LOGGER = new DhLoggerBuilder().build();



	//==================//
	// abstract methods //
	//==================//
	//region abstract methods

	protected abstract void createInitialSharedBindings();
	protected abstract void createInitialClientBindings();
	protected abstract IEventProxy createClientProxy();
	protected abstract IEventProxy createServerProxy(boolean isDedicated);
	protected abstract void initializeModCompat();

	protected abstract void subscribeClientStartedEvent(Runnable eventHandler);
	protected abstract void subscribeServerStartingEvent(Consumer<MinecraftServer> eventHandler);
	protected abstract void runDelayedSetup();
	
	//endregion
	
	
	
	//===================//
	// initialize events //
	//===================//
	//region initialize events
	
	public void onInitializeClient()
	{
		DependencySetup.createClientBindings();
		this.createInitialClientBindings();
		
		LOGGER.info("Initializing " + ModInfo.READABLE_NAME + " client, firing DhApiBeforeDhInitEvent...");
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeDhInitEvent.class, null);
		
		this.startup();
		this.logBuildInfo();
		
		this.createClientProxy().registerEvents();
		this.createServerProxy(false).registerEvents();
		
		this.initializeModCompat();
		
		this.initConfig();
		logIncompatibilityWarnings(); // needs to be called after config loading
		setDisabledDhConfigBasedOnMods();
		setUnsupportedConfigsBasedOnMcVersion();
		Initializer.postConfigInit();

		LOGGER.info(ModInfo.READABLE_NAME + " client Initialized.");

		this.subscribeClientStartedEvent(this::postInit);
		this.subscribeClientStartedEvent(this::postClientInit);
	}
	
	public void onInitializeServer()
	{
		DependencySetup.createServerBindings();
		
		LOGGER.info("Initializing " + ModInfo.READABLE_NAME + " server, firing DhApiBeforeDhInitEvent event...");
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeDhInitEvent.class, null);
		
		this.startup();
		this.logBuildInfo();
		
		// This prevents returning uninitialized Config values,
		// resulting from a circular reference mid-initialization in a static class
		// noinspection ResultOfMethodCallIgnored
		ThreadPresetConfigEventHandler.INSTANCE.toString();
		
		this.createServerProxy(true).registerEvents();
		
		this.initializeModCompat();
		
		LOGGER.info(ModInfo.READABLE_NAME + " server Initialized, adding event subscribers...");

		this.subscribeServerStartingEvent(server ->
		{
			MinecraftServerWrapper.INSTANCE.dedicatedServer = (DedicatedServer)server;

			this.initConfig();
			setUnsupportedConfigsBasedOnMcVersion();
			Initializer.postConfigInit();
			this.postInit();
			this.postServerInit();

			((ServerCommandManager) server.getCommandManager()).registerCommand(CommandInitializer.initCommands());

			LOGGER.info(ModInfo.READABLE_NAME + " server Initialized");
		});
	}
	
	//endregion
	
	
	
	//===========================//
	// inner initializer methods //
	//===========================//
	//region inner initializer methods
	
	private void startup()
	{
		DependencySetup.createSharedBindings();
		Initializer.preConfigInit();
		this.createInitialSharedBindings();
	}
	
	private void logBuildInfo()
	{
		LOGGER.info(ModInfo.READABLE_NAME + ", Version: " + ModInfo.VERSION);
		
		// if the build is stable the branch/commit/etc shouldn't be needed
		if (ModInfo.IS_DEV_BUILD)
		{
			LOGGER.info("DH Branch: " + ModJarInfo.Git_Branch);
			LOGGER.info("DH Commit: " + ModJarInfo.Git_Commit);
			LOGGER.info("DH Jar Build Source: " + ModJarInfo.Build_Source);
		}
	}
	
	/** 
	 * Adds the accessor if any of the given IDs are found. <br>
	 * Can be used when a mod has multiple potential IDs. 
	 */
	protected <T extends IModAccessor> void tryCreateModCompatAccessor(String[] modIdArray, Class<? super T> accessorClass, Supplier<T> accessorConstructor)
	{
		IModChecker modChecker = SingletonInjector.INSTANCE.get(IModChecker.class);
		if (modChecker.isModLoaded(modIdArray))
		{
			//noinspection unchecked
			ModAccessorInjector.INSTANCE.bind((Class<? extends IModAccessor>) accessorClass, accessorConstructor.get());
		}
		else
		{
			LOGGER.debug("Skipping mod compatibility accessor for: ["+ String.join(", ", modIdArray)+"]");
		}
	}
	protected <T extends IModAccessor> void tryCreateModCompatAccessor(String modId, Class<? super T> accessorClass, Supplier<T> accessorConstructor)
	{
		IModChecker modChecker = SingletonInjector.INSTANCE.get(IModChecker.class);
		if (modChecker.isModLoaded(modId))
		{
			//noinspection unchecked
			ModAccessorInjector.INSTANCE.bind((Class<? extends IModAccessor>) accessorClass, accessorConstructor.get());
		}
		else
		{
			LOGGER.debug("Skipping mod compatibility accessor for: ["+modId+"]");
		}
	}
	@SuppressWarnings("unchecked")
	protected <T extends IModAccessor> void addModCompatAccessor(Class<? super T> accessorClass, Supplier<T> accessorConstructor)
	{
		// logging is done internally
		ModAccessorInjector.INSTANCE.bind((Class<? extends IModAccessor>) accessorClass, accessorConstructor.get());
	}
	
	private void initConfig()
	{
		ConfigHandler.tryRunFirstTimeSetup();
		Config.completeDelayedSetup();
		DhLogger.runDelayedConfigSetup();
	}
	
	
	private void postInit()
	{
		LOGGER.info("Running Delayed setup...");
		this.runDelayedSetup();
		
		if (ConfigHandler.INSTANCE == null)
		{
			throw new IllegalStateException("Config was not initialized. Make sure to call LodCommonMain.initConfig() before calling this method.");
		}
		
		LOGGER.info("Delayed setup complete, firing DhApiAfterDhInitEvent event...");
		
		// should be fired after all delayed setup so singletons and config can be accessed
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterDhInitEvent.class, null);
	}
	
	private void postClientInit() 
	{
		CompletableFuture<Void> future = new CompletableFuture<>();
		
		// This method may be called from either the render thread,
		// or some other random setup thread depending on the mod loader.
		// In order to avoid confusion/inconsistent problems, we're always going 
		// to run setup on our own thread.
		Thread dhSetupThread = new Thread(() -> 
		{
			try
			{
				DependencySetup.setRenderingApiBindings();
			}
			catch (Exception e)
			{
				NativeDialogUtil.showDialog(ModInfo.READABLE_NAME, e.getMessage(), "ok", "error");
				MinecraftClientWrapper.INSTANCE.crashMinecraft(e.getMessage(), e);
				future.completeExceptionally(e);
			}
			finally
			{
				future.complete(null);
			}
		});
		dhSetupThread.setName(ThreadUtil.THREAD_NAME_PREFIX + "PostClientInit Thread");
		dhSetupThread.start();
		
		future.join();
	}
	private void postServerInit() { SingletonInjector.INSTANCE.bind(AbstractDebugWireframeRenderer.class, new StubDebugWireframeRenderer()); }
	
	//endregion
	
	
	
	//======================//
	// compatibility checks //
	//======================//
	//region compatibility checks
	
	// TODO merge with ClientApi.detectAndSendBootTimeWarnings
	//  probably put in a separate class
	
	/** 
	 * Some mods will work with a few tweaks
	 * or will partially work but have some known issues we can't solve.
	 * This method will log (and display to chat if enabled)
	 * these warnings and potential fixes.
	 */
	private static void logIncompatibilityWarnings()
	{
		IVersionConstants versionConstants = SingletonInjector.INSTANCE.get(IVersionConstants.class);



		//======//
		// iris //
		//======//
		//region

		IIrisAccessor iris = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
		if (iris != null)
		{
			// get the currently selected rendering API
			EDhApiRenderingEngine renderEngine = Config.Client.Advanced.Graphics.Experimental.renderingEngine.get();
			
			// Iris only supports native OpenGL
			if (renderEngine == EDhApiRenderingEngine.BLAZE_3D)
			{
				String irisUnsupportedMessage = "Iris doesn't support DH when using the ["+ EDhApiRenderingEngine.BLAZE_3D+"] rendering engine, this will need to be fixed on Iris end. As a temporary fix please change the rendering engine to ["+ EDhApiRenderingEngine.OPEN_GL+"] or ["+ EDhApiRenderingEngine.AUTO+"] in the DH config file.";
				LOGGER.fatal(irisUnsupportedMessage);
				NativeDialogUtil.showDialog(ModInfo.READABLE_NAME, irisUnsupportedMessage, "ok", "error");
				
				IMinecraftClientWrapper mc = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
				String errorMessage = "loading Distant Horizons. "+irisUnsupportedMessage;
				String exceptionError = "Distant Horizons conditional mod config Exception";
				mc.crashMinecraft(errorMessage, new Exception(exceptionError));
			}
			else if (renderEngine == EDhApiRenderingEngine.AUTO)
			{
				Config.Client.Advanced.Graphics.Experimental.renderingEngine.setApiValue(EDhApiRenderingEngine.OPEN_GL, ModInfo.READABLE_NAME + " / " + IIrisAccessor.READABLE_NAME);
				
				EDhApiRenderingEngine recommendedEngine = versionConstants.getDefaultRenderingEngine();
				if (recommendedEngine != EDhApiRenderingEngine.OPEN_GL)
				{
					LOGGER.warn("Changing Distant Horizons' rendering engine to [" + EDhApiRenderingEngine.OPEN_GL + "] to allow for Iris rendering. This renderer will be unavailable once Minecraft moves to Vulkan and must be fixed on Iris' end.");
				}
			}
		}
		
		//endregion

	}

	/**
	 * Minecraft 1.7.10 doesn't support all
	 * DH options, so override what options are available.
	 */
	private static void setUnsupportedConfigsBasedOnMcVersion()
	{
		// graphics/rendering
		Config.Client.Advanced.Graphics.Experimental.renderingEngine.setMcVersionOverrideValue(EDhApiRenderingEngine.OPEN_GL);
		Config.Common.WorldGenerator.generatorPlan.setMcVersionOverrideValue(EDhApiGeneratorPlan.CHUNKS_ONLY);
		Config.Common.WorldGenerator.chunkGeneratorMode.setMcVersionOverrideValue(EDhApiDistantGeneratorMode.INTERNAL_SERVER);

		// Disabled since it prevents the JVM from exiting
		Config.Client.Advanced.Debugging.OpenGl.overrideVanillaGLLogger.setMcVersionOverrideValue(false);

		// worldgen
		AbstractMinecraftSharedWrapper.supportsSurfaceGeneration = false;
		EDhApiGeneratorPlan genPlan = Config.Common.WorldGenerator.generatorPlan.getTrueValue();
		if (genPlan.surfaceGenEnabled)
		{
			Config.Common.WorldGenerator.generatorPlan.set(EDhApiGeneratorPlan.CHUNKS_ONLY);
		}
	}
	
	/**
	 * Some DH configs should be disabled if a given
	 * mod is present.
	 */
	private static void setDisabledDhConfigBasedOnMods()
	{
		IIrisAccessor irisAccessor = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
		if (irisAccessor != null)
		{
			// Transparency is required when Iris shaders are present, otherwise
			// rendering will not work properly.
			// Note: this fix doesn't prevent disabling transparency
			// due to a lack of vertical LOD slices, so that may still cause issues.
			Config.Client.Advanced.Graphics.Quality.transparency.setApiValue(EDhApiTransparency.COMPLETE, ModInfo.READABLE_NAME + " / " + IIrisAccessor.READABLE_NAME);
		}
	}
	
	//endregion
	
	
	
	//================//
	// helper classes //
	//================//
	//region helper classes
	
	public interface IEventProxy
	{
		void registerEvents();
	}
	
	//endregion
	
	
	
}
