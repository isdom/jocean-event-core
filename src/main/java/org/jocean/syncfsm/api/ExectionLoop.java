/**
 * 
 */
package org.jocean.syncfsm.api;

/**
 * @author isdom
 *
 */
public interface ExectionLoop {
    public boolean inExectionLoop();
    public void submit(final Runnable runnable);
    public void schedule(final Runnable runnable, final long delayMillis);
}
