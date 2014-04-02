/**
 * 
 */
package org.jocean.syncfsm.api;

import org.jocean.idiom.ExectionLoop;

/**
 * @author isdom
 *
 */
public interface ExectionLoopAware {
    public void setExectionLoop(final ExectionLoop exectionLoop) throws Exception;
}
