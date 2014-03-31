/**
 * 
 */
package org.jocean.syncfsm.api;

/**
 * @author isdom
 *
 */
/**
 * @author isdom
 *
 * @param <FLOW>
 */
public interface EventReceiverSource {
	
	public <FLOW> EventReceiver create(final FlowSource<FLOW> source);

	public <FLOW> EventReceiver create(final FLOW flow, final ExectionLoop exectionLoop, final EventHandler initHandler);
}
