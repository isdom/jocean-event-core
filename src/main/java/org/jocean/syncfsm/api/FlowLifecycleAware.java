/**
 * 
 */
package org.jocean.syncfsm.api;

/**
 * @author isdom
 *
 */
public interface FlowLifecycleAware {
	public void afterEventReceiverCreated(final EventReceiver receiver) throws Exception;
	public void afterFlowDestroy() throws Exception;
}
