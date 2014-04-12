/**
 * 
 */
package org.jocean.event.core;

/**
 * @author isdom
 *
 */
public interface FlowTracker {
	
	public void registerFlowStateChangeListener(
			final FlowStateChangeListener listener);
	
	public void unregisterFlowStateChangeListener(
			final FlowStateChangeListener listener);

}
