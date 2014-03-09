/**
 * 
 */
package org.jocean.syncfsm.common;

import org.jocean.syncfsm.api.EventHandler;

/**
 * @author isdom
 *
 */
public interface FlowContext {

	public EventHandler getCurrentHandler();
	
	public Object getEndReason();

	public long getCreateTime();
	
	public long getLastModify();

	public long getTimeToLive();
}
