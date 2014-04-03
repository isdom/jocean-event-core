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
	
    public boolean acceptEvent(final Eventable eventable, final Object... args) throws Exception;
}
