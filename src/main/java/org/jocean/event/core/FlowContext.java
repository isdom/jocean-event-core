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
}
