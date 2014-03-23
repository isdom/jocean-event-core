/**
 * 
 */
package org.jocean.syncfsm.api;

/**
 * @author isdom
 *
 */
public interface FlowLifecycleListener<FLOW> {
	
	public void afterEventReceiverCreated(final FLOW flow, final EventReceiver receiver) 
			throws Exception;
	
	public void afterFlowDestroy(final FLOW flow) throws Exception;	
}
