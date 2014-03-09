/**
 * 
 */
package org.jocean.syncfsm.common;

import org.jocean.syncfsm.api.EventHandler;

/**
 * @author isdom
 *
 */
public interface FlowStateChangeListener {
	
	public void beforeFlowDispatchTo(
			final EventHandler 	nextHandler, 
			final FlowContext 	ctx, 
			final String 		causeEvent, 
			final Object[] 		causeArgs);
	
	public void afterFlowDestroy(final FlowContext ctx);
}
  