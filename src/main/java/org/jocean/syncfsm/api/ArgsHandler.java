/**
 * 
 */
package org.jocean.syncfsm.api;

/**
 * @author isdom
 *
 */
public interface ArgsHandler {
	
	public Object[] beforeAcceptEvent(final Object[] args);
	
	public void afterAcceptEvent(final Object[] args);
}
