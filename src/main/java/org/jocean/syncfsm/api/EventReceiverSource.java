/**
 * 
 */
package org.jocean.syncfsm.api;

/**
 * @author isdom
 *
 */
public interface EventReceiverSource {
	
	public <FLOW> EventReceiver create(final FlowSource<FLOW> source);

	public <FLOW> EventReceiver create(final FLOW flow, final ExectionLoop exectionLoop, final EventHandler initHandler);
}
