/**
 * 
 */
package org.jocean.syncfsm.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Visitor;
import org.jocean.syncfsm.api.annotion.OnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author isdom
 *
 */
public class SyncFSMUtils {
	
	private static final Logger LOG = 
        	LoggerFactory.getLogger(SyncFSMUtils.class);
	
	public static EventReceiver combineEventReceivers(final EventReceiver ... receivers) {
		return new EventReceiver() {

			@Override
			public boolean acceptEvent(final String event, final Object... args)
					throws Exception {
				boolean handled = false;
				for ( EventReceiver receiver : receivers ) {
					try {
						if ( receiver.acceptEvent(event, args) ) {
							handled = true;
						}
					}
					catch (final Exception e) {
						LOG.error("failed to acceptEvent: {}", ExceptionUtils.exception2detail(e));
					}
				}
				return handled;
			}};
	}

	public static EventReceiver wrapAsyncEventReceiver(final EventReceiver receiver, final Visitor<Runnable> visitor, final ArgsHandler argsHandler) {
		return new EventReceiver() {

			@Override
			public boolean acceptEvent(final String event, final Object... args)
					throws Exception {
				final Object[] safeArgs = argsHandler.beforeAcceptEvent(args);
				visitor.visit(new Runnable() {

					@Override
					public void run() {
						try {
							receiver.acceptEvent(event, safeArgs);
						} catch (Exception e) {
							e.printStackTrace();
						}
						finally {
							argsHandler.afterAcceptEvent(safeArgs);
						}
					}});
				return true;
			}};
	}
	
	@SuppressWarnings("unchecked")
	public static <INTF> INTF buildInterfaceAdapter(final Class<?> intf, final EventReceiver receiver) {
		return (INTF)Proxy.newProxyInstance(null, new Class<?>[]{intf}, new ReceiverAdapterHandler(receiver));
	}

	private static final class ReceiverAdapterHandler implements InvocationHandler {

		ReceiverAdapterHandler(final EventReceiver receiver ) {
			if ( null == receiver ) {
				throw new NullPointerException("EventReceiver can't be null");
			}
			this._receiver = receiver;
		}
		
		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args)
				throws Throwable {
			final OnEvent onevent = method.getAnnotation(OnEvent.class);
			if ( null == onevent ) {
				throw new UnsupportedOperationException("method [" + method.getName() + "] has no OnEvent annotation, can't generate event");
			}
			boolean isAccepted = _receiver.acceptEvent(onevent.event(), args);
			if ( method.getReturnType().equals(Boolean.class)
				|| method.getReturnType().equals(boolean.class)) {
				return isAccepted;
			}
			else {
				return null;
			}
		}
		
		private final EventReceiver _receiver;
	}
}
