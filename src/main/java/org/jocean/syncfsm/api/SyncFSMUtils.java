/**
 * 
 */
package org.jocean.syncfsm.api;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Visitor;
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
}
