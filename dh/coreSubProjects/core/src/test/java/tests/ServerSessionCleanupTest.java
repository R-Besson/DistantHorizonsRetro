package tests;

import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayloadSender;
import com.seibel.distanthorizons.core.multiplayer.fullData.SharedBandwidthLimit;
import com.seibel.distanthorizons.core.network.messages.base.RequestLevelInitMessage;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Server-side per-player cleanup.
 *
 * @see NetworkSession#unregisterHandler
 * @see FullDataPayloadSender
 */
public class ServerSessionCleanupTest
{

	@Test
	public void unregisteredHandlerIsNoLongerCalled()
	{
		NetworkSession session = new NetworkSession(null);

		AtomicInteger removedHandlerCount = new AtomicInteger();
		AtomicInteger keptHandlerCount = new AtomicInteger();
		Consumer<RequestLevelInitMessage> removedHandler = (msg) -> removedHandlerCount.incrementAndGet();
		Consumer<RequestLevelInitMessage> keptHandler = (msg) -> keptHandlerCount.incrementAndGet();

		session.registerHandler(RequestLevelInitMessage.class, removedHandler);
		session.registerHandler(RequestLevelInitMessage.class, keptHandler);

		session.tryHandleMessage(new RequestLevelInitMessage());
		Assert.assertEquals(1, removedHandlerCount.get());
		Assert.assertEquals(1, keptHandlerCount.get());

		session.unregisterHandler(RequestLevelInitMessage.class, removedHandler);
		session.tryHandleMessage(new RequestLevelInitMessage());
		Assert.assertEquals("unregistered handler must not receive messages", 1, removedHandlerCount.get());
		Assert.assertEquals("other handlers must keep working", 2, keptHandlerCount.get());

		// re-registering after an unregister must work (the handler set is kept)
		session.registerHandler(RequestLevelInitMessage.class, removedHandler);
		session.tryHandleMessage(new RequestLevelInitMessage());
		Assert.assertEquals(2, removedHandlerCount.get());
	}

	@Test
	public void idleAndClosedSendersDontHoldBandwidthShare() throws Exception
	{
		NetworkSession session = new NetworkSession(null);
		SharedBandwidthLimit sharedBandwidthLimit = new SharedBandwidthLimit();
		Set<?> senders = getSenders(sharedBandwidthLimit);

		FullDataPayloadSender sender = new FullDataPayloadSender(session, () -> 500, sharedBandwidthLimit);
		try
		{
			// let the sender tick a few times with nothing queued
			Thread.sleep(250);
			Assert.assertFalse("a sender with nothing queued must not take a bandwidth share", senders.contains(sender));

			sharedBandwidthLimit.setSenderActive(sender, true);
			sender.close();
			Assert.assertFalse("a closed sender must release its bandwidth share", senders.contains(sender));
		}
		finally
		{
			sender.close();
		}
	}

	private static Set<?> getSenders(SharedBandwidthLimit sharedBandwidthLimit) throws Exception
	{
		Field sendersField = SharedBandwidthLimit.class.getDeclaredField("senders");
		sendersField.setAccessible(true);
		return (Set<?>) sendersField.get(sharedBandwidthLimit);
	}

}
