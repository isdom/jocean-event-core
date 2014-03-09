/**
 * 
 */
package org.jocean.syncfsm.api;

/**
 * @author isdom
 *
 */
public interface EventReceiver {
	
	public boolean acceptEvent(final String event, final Object... args) throws Exception;
}
