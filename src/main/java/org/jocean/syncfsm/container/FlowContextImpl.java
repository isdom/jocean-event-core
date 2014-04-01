/**
 * 
 */
package org.jocean.syncfsm.container;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.jocean.syncfsm.api.ArgsHandler;
import org.jocean.syncfsm.api.ArgsHandlerSource;
import org.jocean.syncfsm.api.EventHandler;
import org.jocean.syncfsm.api.ExectionLoop;
import org.jocean.syncfsm.common.FlowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author isdom
 * 
 */
class FlowContextImpl implements FlowContext, Comparable<FlowContextImpl> {

    public interface Dispatcher {
        public void dispatchEvent(final FlowContextImpl ctx, final String event, final Object[] args);
    }
    
    boolean isFlowEventHandlerAware = false;
    boolean isFlowEventNameAware = false;
    boolean isFlowHasEndReason = false;

    private static final Logger LOG = LoggerFactory
            .getLogger(FlowContextImpl.class);

    public FlowContextImpl(final Object flow, final ExectionLoop exectionLoop, final Dispatcher dispatcher) {
        this._flow = flow;
        this._exectionLoop = exectionLoop;
        this._dispatcher = dispatcher;
        
        if ( this._flow instanceof ArgsHandlerSource ) {
            this._argsHandler = ((ArgsHandlerSource)this._flow).getArgsHandler();
        }
        else {
            this._argsHandler = null;
        }
        
        if (null == this._flow || null == this._exectionLoop) {
            throw new NullPointerException(
                    "invalid params: flow or exectionLoop is null");
        }
    }

    @SuppressWarnings("unchecked")
    public <FLOW> FLOW getFlow() {
        return (FLOW) this._flow;
    }

    @Override
    public EventHandler getCurrentHandler() {
        return this._currentHandler;
    }

    @Override
    public Object getEndReason() {
        return _reason;
    }

    @Override
    public long getCreateTime() {
        return this._createTime;
    }

    @Override
    public long getLastModify() {
        return this._lastModify;
    }

    @Override
    public long getTimeToLive() {
        return isDestroyed() ? (this._lastModify - this._createTime) : (System
                .currentTimeMillis() - _createTime);
    }

    public void setEndReason(final Object reason) {
        this._reason = reason;
    }

    FlowContextImpl setCurrentHandler(final EventHandler handler) {
        if (((this._currentHandler == null) && (handler != null))
                || ((this._currentHandler != null) && !this._currentHandler
                        .equals(handler))) {
            this._currentHandler = handler;
            this._lastModify = System.currentTimeMillis();
        }
        return this;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.skymobi.fsm.FSMContext#destroy()
     */
    public void destroy() {
        if (this._isAlive.compareAndSet(true, false)) {
            this._lastModify = System.currentTimeMillis();
            
            //  clear pending event and args
            while (!this._pendingEvents.isEmpty()) {
                final Iterator<Pair<String,Object[]>> iter = this._pendingEvents.iterator();
                final Pair<String, Object[]> eventAndArgs = iter.next();
                afterDispatchArgs(eventAndArgs.getFirst(), eventAndArgs.getSecond());
                iter.remove();
            }
        }
    }

    public boolean isDestroyed() {
        return !_isAlive.get();
    }

    private Pair<String, Object[]> popPendingEvent() {
        if (isDestroyed()) {
            return null;
        }
        return this._pendingEvents.poll();
    }

    private boolean pushPendingEvent(final String event, final Object[] args) throws Exception {
        if (!isDestroyed()) {
            this._pendingEvents.add(Pair.of(event, beforeAcceptArgs(args)));
            return true;
        } else {
            LOG.warn("ctx {} already destroy, ignore pending event {} ", this,
                    event);
            return false;
        }
    }

    private boolean hasPendingEvent() {
        if (isDestroyed()) {
            return false;
        }
        return !this._pendingEvents.isEmpty();
    }

    private void dispatchPendingEvent() {
        final Pair<String, Object[]> eventAndArgs = popPendingEvent();

        if (null != eventAndArgs) {
            try {
                this._dispatcher.dispatchEvent(this, 
                        eventAndArgs.getFirst(),
                        eventAndArgs.getSecond());
            } catch (Exception e) {
                LOG.warn("exception when process event {}, detail:{}",
                        eventAndArgs.getFirst(),
                        ExceptionUtils.exception2detail(e));
            }
            finally {
                afterDispatchArgs(eventAndArgs.getFirst(), eventAndArgs.getSecond());
            }
        } else {
            setUnactive();
        }
    }

    private Object[] beforeAcceptArgs(final Object[] args) throws Exception {
        if ( null != this._argsHandler ) {
            return this._argsHandler.beforeAcceptEvent(args);
        }
        else {
            return args;
        }
    }

    private void afterDispatchArgs(final String event, final Object[] args) {
        if ( null != this._argsHandler ) {
            try {
                this._argsHandler.afterAcceptEvent(args);
            } catch (Exception e) {
                LOG.warn("exception when afterAcceptEvent for event:{}, detail:{},", 
                        event, ExceptionUtils.exception2detail(e));
            }
        }
    }

    private void schedulePendingEvent() {
        if (this._exectionLoop.inExectionLoop()) {
            if ( LOG.isDebugEnabled()) {
                LOG.debug("flow {}'s currentHandler({}): schedulePendingEvent in exectionLoop, just invoke direct.", 
                        this._flow, this._currentHandler.getName());
            }
            dispatchPendingEvent();
        } else {
            LOG.debug("flow {}'s currentHandler({}): schedulePendingEvent NOT in exectionLoop, just invoke as submit.", 
                    this._flow, this._currentHandler.getName());
            this._exectionLoop.submit( this._dispatchPendingRunnable );
        }
    }

    private void checkIfSchedulePendingEvent() throws Exception {
        if (hasPendingEvent()) {
            if (setActived()) {
                schedulePendingEvent();
            }
            else {
                if ( LOG.isDebugEnabled() ) {
                    LOG.debug("flow {}'s currentHandler({}): already actived, can't schedulePendingEvent", 
                            this._flow, this._currentHandler.getName());
                }
            }
        }
    }

    private boolean setActived() throws Exception {
        if (isDestroyed()) {
            return false;
        }
        final boolean ret = this._isActive.compareAndSet(false, true);
        if (ret) {
            // if ( null != this._statusListener ) {
            // try {
            // this._statusListener.onActive(this);
            // }
            // catch (Exception e) {
            // // 重置 激活标记为 false
            // _isActive.set(false);
            // LOG.warn("exception when invoke this.statusListener.onActive, detail: {}",
            // ExceptionUtils.exception2detail(e));
            // throw e;
            // }
            // }
            // _isActivedOnce = true;
            this._lastActiveTime = System.currentTimeMillis();
        }
        return ret;
    }

    // 是否曾经激活过
    // 也就是至少在 runner 工作线程中执行过至少一次
    // public boolean isActivedOnce() {
    // return this._isActivedOnce;
    // }

    private void setUnactive() {
        if (this._isActive.compareAndSet(true, false)) {
            this._activeTime.addAndGet(System.currentTimeMillis()
                    - this._lastActiveTime);
            // if ( null != this._statusListener ) {
            // try {
            // this._statusListener.onUnactive(this);
            // }
            // catch (Exception e) {
            // LOG.warn("throw exception when invoke this.statusListener.onUnactive",
            // e);
            // }
            // }
        }
    }

    public void endOfDispatchEvent() throws Exception {
        setUnactive();
        checkIfSchedulePendingEvent();
    }

    // return true means event has been push to event-queue
    public boolean processEvent(final String event, final Object[] args) throws Exception {
        if (pushPendingEvent(event, args)) {
            checkIfSchedulePendingEvent();
            return true;
        } else {
            return false;
        }
    }
    
    private final Runnable _dispatchPendingRunnable = new Runnable() {
        public void run() {
            dispatchPendingEvent();
        }
    };
    
    private final Dispatcher _dispatcher;
    
    private final AtomicBoolean _isActive = new AtomicBoolean(false);

    private final Queue<Pair<String, Object[]>> _pendingEvents = new ConcurrentLinkedQueue<Pair<String, Object[]>>();
    private final ExectionLoop _exectionLoop;
    protected final long _createTime = System.currentTimeMillis();
    protected long _lastModify = System.currentTimeMillis();
    private final AtomicLong _activeTime = new AtomicLong(0);
    private volatile long _lastActiveTime;

    private final AtomicBoolean _isAlive = new AtomicBoolean(true);

    private volatile EventHandler _currentHandler = null;
    private Object _reason = null;
    private final Object _flow;
    private final ArgsHandler _argsHandler;
    
    private final int _id = _IDSRC.getAndIncrement();
    
    private static final AtomicInteger _IDSRC = new AtomicInteger(0);
    
    @Override
    public int compareTo(final FlowContextImpl o) {
        return this._id - o._id;
    }
}
