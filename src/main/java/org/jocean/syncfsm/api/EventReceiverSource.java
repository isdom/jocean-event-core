/**
 * 
 */
package org.jocean.syncfsm.api;

import org.jocean.idiom.ExectionLoop;

/**
 * @author isdom
 * 
 */
public interface EventReceiverSource {

    public <FLOW> EventReceiver create(final FlowSource<FLOW> source);

    public <FLOW> EventReceiver create(final FLOW flow,
            final EventHandler initHandler, final ExectionLoop exectionLoop);
}
