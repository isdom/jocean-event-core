/**
 * 
 */
package org.jocean.event.core;

import org.jocean.event.api.internal.EventHandler;

/**
 * @author isdom
 *
 */
public interface FlowContext {

	public EventHandler getCurrentHandler();
	
	public Object getEndReason();

	public long getCreateTime();
	
	public long getLastModify();

	public long getTimeToActive();

	public long getTimeToLive();
	
	public interface ReactorBuilder {
		/**
		 * @param ctx
		 * @return	reactors 0~N个反应器实例, 反应器可以为: 
	     *  EventNameAware, 
			EventHandlerAware, 
			EndReasonProvider,
			EndReasonAware,
			ExectionLoopAware,
			FlowLifecycleListener, 
			FlowStateChangedListener
		 */
		public Object[] buildReactors(final FlowContext ctx);
	}
}
