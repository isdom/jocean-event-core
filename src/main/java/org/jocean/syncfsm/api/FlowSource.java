/**
 * 
 */
package org.jocean.syncfsm.api;

import org.jocean.idiom.ExectionLoop;

/**
 * @author isdom
 *
 */
public interface FlowSource<FLOW> {
	
	public 	FLOW getFlow();
	
	public	EventHandler getInitHandler(final FLOW flow);
	
    public  ExectionLoop getExectionLoop(final FLOW flow);
}
