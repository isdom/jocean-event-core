/**
 * 
 */
package org.jocean.syncfsm.api;

/**
 * @author isdom
 *
 */
public abstract class AbstractUnhandleAware implements EventUnhandleAware {

    public AbstractUnhandleAware(final String event) {
        this._event = event;
    }
    
    @Override
    public String event() {
        return this._event;
    }

    private final String _event;
}
