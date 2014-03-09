/**
 * 
 */
package org.jocean.syncfsm.api;

/**
 * @author isdom
 *
 */
public interface FlowSource<FLOW> {
	
	public 	FLOW getFlow();
	
	public	EventHandler getInitHandler(final FLOW flow);
}
