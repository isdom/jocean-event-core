/**
 * 
 */
package org.jocean.syncfsm.api;

import org.jocean.idiom.ExceptionUtils;
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
}
