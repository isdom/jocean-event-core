/**
 * 
 */
package org.jocean.event.core;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jocean.event.api.EventUnhandleAware;
import org.jocean.event.api.internal.EndReasonSource;
import org.jocean.event.api.internal.EventHandler;
import org.jocean.event.api.internal.EventHandlerAware;
import org.jocean.event.api.internal.EventNameAware;
import org.jocean.event.api.internal.Eventable;
import org.jocean.event.api.internal.ExectionLoopAware;
import org.jocean.event.api.internal.FlowLifecycleAware;
import org.jocean.idiom.ArgsHandler;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.ExectionLoop;
import org.jocean.idiom.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author isdom
 *
 */
public class FlowContextImpl implements FlowContext, Comparable<FlowContextImpl> {

	public interface StatusReactor {
	    public boolean checkIfExceedLimit(final FlowContextImpl ctx);
        public void onDestroyByExceedLimit(final FlowContextImpl ctx);
		public void onActive(final FlowContextImpl ctx);
		public void onUnactive(final FlowContextImpl ctx);
	}
    
    private static final Logger LOG = 
    		LoggerFactory.getLogger(FlowContextImpl.class);

	public FlowContextImpl(
	        final Object flow, 
	        final ExectionLoop exectionLoop, 
			final StatusReactor statusReactor,
            final FlowStateChangeListener stateChangeListener) {
		this._flow = flow;
		this._exectionLoop = exectionLoop;
        this._statusReactor = statusReactor;
        this._stateChangeListener = stateChangeListener;
        
        if (null == this._flow || null == this._exectionLoop) {
            throw new NullPointerException(
                    "invalid params: flow or exectionLoop is null");
        }
        
        this._isFlowEventNameAware = (this._flow instanceof EventNameAware);
        this._isFlowEventHandlerAware = (this._flow instanceof EventHandlerAware);
        this._isFlowHasEndReason = (this._flow instanceof EndReasonSource);
        
        if ( this._flow instanceof ExectionLoopAware ) {
            try {
                ((ExectionLoopAware)this._flow).setExectionLoop(this._exectionLoop);
            }
            catch (Exception e) {
                LOG.error("exception when invoke flow {}'s setExectionLoop, detail: {}",
                        this._flow, ExceptionUtils.exception2detail(e));
            }
        }
    }

    @Override
    public int compareTo(final FlowContextImpl o) {
        return this._id - o._id;
    }

	@SuppressWarnings("unchecked")
	public <FLOW> FLOW getFlow() {
		return	(FLOW)this._flow;
	}
	
	@Override
	public EventHandler getCurrentHandler() {
		return	this._currentHandler;
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
	public long getTimeToActive() {
		return this._activeTime.get();
	}

	@Override
	public long getTimeToLive() {
		return	isDestroyed() 
				? ( this._lastModify - this._createTime) 
				: (System.currentTimeMillis() - this._createTime);
	}
	
    // return true means event has been push to event-queue
    public boolean processEvent(final Object eventable, final Object[] args) throws Exception {
        if (pushPendingEvent(eventable, args)) {
            checkIfSchedulePendingEvent( obj2event(eventable));
            return true;
        } else {
            return false;
        }
    }
    	
    public FlowContextImpl setCurrentHandler(
            final EventHandler handler, 
            final String        causeEvent, 
            final Object[]      causeArgs) {
        if (((this._currentHandler == null) && (handler != null))
                || ((this._currentHandler != null) && !this._currentHandler
                        .equals(handler))) {
            if ( null != this._stateChangeListener ) {
                try {
                    this._stateChangeListener.beforeFlowChangeTo(this, handler, causeEvent, causeArgs);
                }
                catch (Exception e) {
                    LOG.warn("exception when _stateChangeListener.beforeFlowChangeTo for flow({}) with next handler({}), event:({}), detail:{}",
                            this._flow, handler.getName(), causeEvent, ExceptionUtils.exception2detail(e));
                }
            }
            this._currentHandler = handler;
            this._lastModify = System.currentTimeMillis();
            
            if ( this._isFlowEventHandlerAware ) {
                try {
                    ((EventHandlerAware)this._flow).setEventHandler(handler);
                }
                catch (Exception e) {
                    LOG.error("exception when setEventHandler: handler {} to flow {}, detail: {}",
                            handler, this._flow, ExceptionUtils.exception2detail(e));
                }
            }
        }
        return this;
    }
	
    public void destroy() {
        if (this._isAlive.compareAndSet(true, false)) {
            
            if ( LOG.isTraceEnabled() ) {
                LOG.trace("flow({}) destroy with currentHandler({})", this._flow, 
                        ( null == this._currentHandler ? "null" : this._currentHandler.getName()));
            }
            
            this._lastModify = System.currentTimeMillis();
            
            setUnactive();
            
            //  clear pending event and args
            while (!this._pendingEvents.isEmpty()) {
                final Iterator<Pair<Object,Object[]>> iter = this._pendingEvents.iterator();
                final Pair<Object, Object[]> eventAndArgs = iter.next();
                notifyUnhandleEvent(eventAndArgs.getFirst(), eventAndArgs.getSecond());
                postprocessArgsByArgsHandler(eventAndArgs.getFirst(), eventAndArgs.getSecond());
                iter.remove();
            }
            
            if ( this._isFlowHasEndReason ) {
                // fetch end reason
                try {
                    this._reason = ( ((EndReasonSource)this._flow).getEndReason() );
                }
                catch (Exception e) {
                    LOG.error("exception when getEndReason: flow {}, detail: {}",
                            this._flow, ExceptionUtils.exception2detail(e));
                }
            }
            
            if ( null != this._stateChangeListener ) {
                try {
                    this._stateChangeListener.afterFlowDestroy(this);
                }
                catch (Exception e) {
                    LOG.warn("exception when _stateChangeListener.afterFlowDestroy for flow({}), detail:{}",
                            this._flow, ExceptionUtils.exception2detail(e));
                }
            }
            
            if ( this._flow instanceof FlowLifecycleAware ) {
                try {
                    ((FlowLifecycleAware)this._flow).afterFlowDestroy();
                }
                catch (Exception e) {
                    LOG.error("exception when invoke flow {}'s afterFlowDestroy, detail: {}",
                            this._flow, ExceptionUtils.exception2detail(e));
                }
            }
        }
    }
    
	public boolean isDestroyed() {
		return !this._isAlive.get();
	}

    private Pair<Object, Object[]> popPendingEvent() {
        if (isDestroyed()) {
            return null;
        }
        return this._pendingEvents.poll();
    }

    private boolean pushPendingEvent(final Object eventable, final Object[] args) throws Exception {
        if (!isDestroyed()) {
            this._pendingEvents.add(Pair.of(eventable, preprocessArgsByArgsHandler(eventable, args)));
            return true;
        } else {
            LOG.warn("flow {} already destroy, bypass pending event:({})", this._flow,
                    obj2event(eventable));
            notifyUnhandleEvent(eventable, args);
            return false;
        }
    }

    private void notifyUnhandleEvent(final Object eventable, final Object[] args) {
        if ( eventable instanceof EventUnhandleAware ) {
            try {
                if ( LOG.isTraceEnabled() ) {
                    LOG.trace("flow({}) invoke EventUnhandleAware({})'s onEventUnhandle with event:({})",
                            this._flow, eventable, obj2event(eventable));
                }
                ((EventUnhandleAware)eventable).onEventUnhandle(obj2event(eventable), args);
            }
            catch (Exception e) {
                LOG.warn("exception when flow({}) notify EventUnhandleAware({})'s onEventUnhandle with event({}), detail: {}",
                        this._flow, eventable, obj2event(eventable), ExceptionUtils.exception2detail(e));
            }
        }
    }

    private boolean hasPendingEvent() {
        if (isDestroyed()) {
            return false;
        }
        return !this._pendingEvents.isEmpty();
    }

    private void dispatchPendingEvent() {
        if ( this._isFirsttimeActived ) {
            this._isFirsttimeActived = false;
            if ( null != this._statusReactor ) {
                if ( this._statusReactor.checkIfExceedLimit(this) ) {
                    this._statusReactor.onDestroyByExceedLimit(this);
                    this.destroy();
                    return;
                }
            }
        }

        final Pair<Object, Object[]> eventAndArgs = popPendingEvent();
        
        if (null != eventAndArgs) {
            final String event = obj2event(eventAndArgs.getFirst());
            try {
                if ( LOG.isTraceEnabled() ) {
                    LOG.trace("flow({}) with currentHandler({}) before dispatch event:({})", 
                            this._flow, this._currentHandler.getName(), event);
                }
                
                if ( !this.dispatchEvent(
                        event,
                        eventAndArgs.getSecond()) ) {
                    // event !NOT! handle by current EventHandler
                    if ( LOG.isTraceEnabled() ) {
                        LOG.trace("flow({}) with currentHandler({}) !NOT! handle event:({})", 
                                this._flow, this._currentHandler.getName(), event);
                    }
                    notifyUnhandleEvent(eventAndArgs.getFirst(), eventAndArgs.getSecond());
                }
                
                if ( LOG.isTraceEnabled() ) {
                    LOG.trace("flow({}) with currentHandler({}) after dispatch event:({}) and _isActived({})", 
                            this._flow, this._currentHandler.getName(), 
                            event, this._isActived.get());
                }
            } catch (Throwable e) {
                LOG.warn("exception when flow({}) process event:({}), detail:{}",
                        this._flow, event,
                        ExceptionUtils.exception2detail(e));
            }
            finally {
                postprocessArgsByArgsHandler(eventAndArgs.getFirst(), eventAndArgs.getSecond());
            }
        } else {
            setUnactive();
        }
    }

    private static String obj2event(final Object obj) {
        if ( obj instanceof String) {
            return (String)obj;
        }
        else if ( obj instanceof Eventable ) {
            return ((Eventable)obj).event();
        }
        else {
            throw new RuntimeException("Internal Error:obj must be String or Eventable.");
        }
    }
    
    /**
     * @param eventable
     * @param args
     * @throws Exception
     */
    private Object[] preprocessArgsByArgsHandler(
            final Object eventable,
            final Object[] args) throws Exception {
        if ( eventable instanceof ArgsHandler ) {
            try {
                return ((ArgsHandler)eventable).beforeInvoke(args);
            } catch (Throwable e) {
                LOG.warn("exception when flow({})'s preprocessArgsByArgsHandler for event:({}), detail:{},", 
                        this._flow, eventable, ExceptionUtils.exception2detail(e));
            }
        }
        return args;
    }

    /**
     * @param eventable
     * @param args
     * @throws Exception
     */
    private void postprocessArgsByArgsHandler(
            final Object eventable,
            final Object[] args) {
        if ( eventable instanceof ArgsHandler ) {
            try {
                ((ArgsHandler)eventable).afterInvoke(args);
            } catch (Throwable e) {
                LOG.warn("exception when flow({})'s postprocessArgsByArgsHandler for event:({}), detail:{},", 
                        this._flow, eventable, ExceptionUtils.exception2detail(e));
            }
        }
    }
    
    private void schedulePendingEvent(final String causeEvent) {
        if ( this._exectionLoop.inExectionLoop() ) {
            if ( LOG.isTraceEnabled()) {
                LOG.trace("flow {}'s currentHandler({}): schedulePendingEvent cause by event:({}) in exectionLoop, just invoke direct.", 
                        this._flow, this._currentHandler.getName(), causeEvent);
            }
            dispatchPendingEvent();
        }
        else {
            if ( LOG.isTraceEnabled() ) {
                LOG.trace("flow {}'s currentHandler({}): schedulePendingEvent cause by event:({}) NOT in exectionLoop, just invoke as submit.", 
                        this._flow, this._currentHandler.getName(), causeEvent);
            }
            this._exectionLoop.submit( this._dispatchPendingRunnable );
        }
    }	
	
    private void checkIfSchedulePendingEvent(final String causeEvent) throws Exception {
        if (hasPendingEvent()) {
            if (setActived()) {
                schedulePendingEvent(causeEvent);
            }
            else {
                if ( LOG.isTraceEnabled() ) {
                    LOG.trace("flow {}'s currentHandler({}): already actived, can't schedulePendingEvent cause by event:({})", 
                            this._flow, this._currentHandler.getName(), causeEvent);
                }
            }
        }
    }
	
    private boolean setActived() throws Exception {
        if (isDestroyed()) {
            if ( LOG.isTraceEnabled() ) {
                LOG.trace("try setActived for destroyed flow({}), return false", this._flow);
            }
            return false;
        }
        final boolean ret = this._isActived.compareAndSet(false, true);
        if (ret) {
            if (null != this._statusReactor) {
                try {
                    this._statusReactor.onActive(this);
                } catch (Exception e) {
                    LOG.warn("exception when invoke statusReactor.onActive, detail: {}",
                            ExceptionUtils.exception2detail(e));
                }
            }
            this._lastActiveTime = System.currentTimeMillis();
        }
        return ret;
    }

    private void setUnactive() {
        if (this._isActived.compareAndSet(true, false)) {
            this._activeTime.addAndGet(System.currentTimeMillis()
                    - this._lastActiveTime);
            if (null != this._statusReactor) {
                try {
                    this._statusReactor.onUnactive(this);
                } catch (Exception e) {
                    LOG.warn("exception when invoke statusReactor.onUnactive, detail:{}",
                            ExceptionUtils.exception2detail(e));
                }
            }
        }
    }
	
    private boolean dispatchEvent(final String event, final Object[] args) {
        final EventHandler currentHandler = this.getCurrentHandler();
        if ( null == currentHandler ) {
            LOG.error("Internal Error: current handler is null when accept event:({}), destroy flow({})", 
                    event, this._flow);
            this.destroy();
            return  false;
        }
        
        setCurrentAcceptedEvent(event);
        
        EventHandler nextHandler = null;
        boolean     eventHandled = false;

        try {
            Pair<EventHandler, Boolean> result = currentHandler.process(event, args);
            nextHandler = result.getFirst();
            eventHandled = result.getSecond();
        }
        catch (Exception e) {
            LOG.error("exception when ({}).acceptEvent(event:({}) ... ), detail:{}", 
                currentHandler.getName(), 
                event,
                ExceptionUtils.exception2detail(e));
        }
        finally {
            setCurrentAcceptedEvent(null);
        }
        
        if ( null == nextHandler ) {
            // handled and next handler is null
            this.destroy();
            
            if ( LOG.isDebugEnabled() ) {
                LOG.debug("flow ({}) end normally for event:({}).", this._flow, event);
            }
            return  eventHandled;
        }
        else if ( currentHandler.equals( nextHandler ) ) {
            // no change
        }
        else {
            setCurrentHandler(nextHandler, event, args);
        }
        
        dispatchPendingEvent();
        if ( LOG.isTraceEnabled() ) {
            LOG.trace("after end of dispatchEvent invoke dispatchPendingEvent for flow({}) cause by event:({}) and _isActived({})",
                    this._flow, event, this._isActived.get());
        }
        
        return  eventHandled;
    }
    
    private void setCurrentAcceptedEvent(final String event) {
        if ( this._isFlowEventNameAware ) {
            try {
                ((EventNameAware)this._flow).setEventName(event);
            }
            catch (Exception e) {
                LOG.error("exception when setEventName: event:({}) to flow {}, detail: {}",
                    event, this._flow, ExceptionUtils.exception2detail(e));
            }
        }
    }
    
    private final Runnable _dispatchPendingRunnable = new Runnable() {
        public void run() {
            dispatchPendingEvent();
        }
    };

    private final ExectionLoop _exectionLoop;
    
    private final StatusReactor _statusReactor;
    private final FlowStateChangeListener _stateChangeListener;
    
    private final boolean _isFlowEventHandlerAware;
    private final boolean _isFlowEventNameAware;
    private final boolean _isFlowHasEndReason;

    private final AtomicBoolean _isActived = new AtomicBoolean(false);

    private final Queue<Pair<Object, Object[]>> _pendingEvents = 
            new ConcurrentLinkedQueue<Pair<Object, Object[]>>();
    
    private final long _createTime = System.currentTimeMillis();
    private volatile long _lastModify = System.currentTimeMillis();
    private final AtomicLong _activeTime = new AtomicLong(0);
    private volatile long _lastActiveTime;

    private final AtomicBoolean _isAlive = new AtomicBoolean(true);

    private volatile EventHandler _currentHandler = null;
    private volatile Object _reason = null;
    private final Object _flow;
    
    private final int _id = _IDSRC.getAndIncrement();
    
    private volatile boolean	_isFirsttimeActived = true;
	
    private static final AtomicInteger _IDSRC = new AtomicInteger(0);
}
