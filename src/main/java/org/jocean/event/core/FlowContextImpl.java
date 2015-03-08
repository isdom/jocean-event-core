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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jocean.event.api.BizStep;
import org.jocean.event.api.EndReasonAware;
import org.jocean.event.api.EventUnhandleAware;
import org.jocean.event.api.FlowLifecycleListener;
import org.jocean.event.api.FlowStateChangedListener;
import org.jocean.event.api.internal.EndReasonProvider;
import org.jocean.event.api.internal.EventHandler;
import org.jocean.event.api.internal.EventHandlerAware;
import org.jocean.event.api.internal.EventNameAware;
import org.jocean.event.api.internal.Eventable;
import org.jocean.event.api.internal.ExectionLoopAware;
import org.jocean.idiom.ArgsHandler;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.ExectionLoop;
import org.jocean.idiom.InterfaceUtils;
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
    
    private static final Logger DEFAULT_LOG = 
    		LoggerFactory.getLogger(FlowContextImpl.class);

    private final Logger logger;
    
	@Override
	public String toString() {
		return "Flow [" + this._name+ "]";
	}

	public FlowContextImpl(
			final String		name,
	        final ExectionLoop 	exectionLoop, 
			final StatusReactor statusReactor) {
		this._name = null != name ? name : super.toString();
		this._exectionLoop = exectionLoop;
        this._statusReactor = statusReactor;
        
        if (null == this._exectionLoop) {
            throw new NullPointerException(
                    "invalid params: exectionLoop is null");
        }
        
        this.logger = 
//        		flow instanceof Slf4jLoggerSource 
//                ? ((Slf4jLoggerSource)flow).getLogger() 
//                : 
                	DEFAULT_LOG;
    }

	@SuppressWarnings("unchecked")
	public void setReactors(final Object[] reactors) {
        this._eventNameAware = InterfaceUtils.compositeByType(reactors, EventNameAware.class);
        this._eventHandlerAware = InterfaceUtils.compositeByType(reactors, EventHandlerAware.class);
        this._endReasonProvider = InterfaceUtils.compositeByType(reactors, EndReasonProvider.class);
        this._exectionLoopAware = InterfaceUtils.compositeByType(reactors, ExectionLoopAware.class);
        this._flowLifecycleListener = InterfaceUtils.compositeByType(reactors, FlowLifecycleListener.class);
        this._flowStateChangedListener = 
        		InterfaceUtils.compositeByType(reactors, FlowStateChangedListener.class);
        
        if (null != this._endReasonProvider) {
        	this._endReasonProvider.setEndReasonAware(new EndReasonAware(){
				@Override
				public void setEndReason(final Object endreason) {
					_reason = endreason;
				}});
        }
        if (null!=this._exectionLoopAware) {
            try {
            	this._exectionLoopAware.setExectionLoop(this._exectionLoop);
            }
            catch (Exception e) {
                logger.error("exception when invoke flow {}'s setExectionLoop, detail: {}",
                        this._name, ExceptionUtils.exception2detail(e));
            }
        }
	}
	
    @Override
    public int compareTo(final FlowContextImpl o) {
        return this._id - o._id;
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
            if (null!=this._flowStateChangedListener) {
            	try {
            		_flowStateChangedListener.onStateChanged(
            				this._currentHandler, handler, causeEvent, causeArgs);
				} catch (Exception e) {
                    logger.warn("exception when _flow.onStateChanged for flow({}) with next handler({}), event:({}), detail:{}",
                            this._name, handler.getName(), causeEvent, ExceptionUtils.exception2detail(e));
				}
            }
            
            this._currentHandler = handler;
            this._lastModify = System.currentTimeMillis();
            
            if (null!=this._eventHandlerAware) {
                try {
                    ((EventHandlerAware)this._eventHandlerAware).setEventHandler(handler);
                }
                catch (Exception e) {
                    logger.error("exception when setEventHandler: handler {} to flow {}, detail: {}",
                            handler, this._name, ExceptionUtils.exception2detail(e));
                }
            }
        }
        return this;
    }
	
    public void destroy(final String causeEvent, final Object[] causeArgs) {
        this._destroyingLock.writeLock().lock();
        try {
            if (this._isAlive.compareAndSet(true, false)) {
                doDestroy(causeEvent, causeArgs);
            }
        } finally {
            this._destroyingLock.writeLock().unlock();
        }
    }

    private void doDestroy(final String causeEvent, final Object[] causeArgs) {
        if ( logger.isTraceEnabled() ) {
            logger.trace("flow({}) destroy with currentHandler({})", this._name, 
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
        
        if (null!=this._flowStateChangedListener) {
        	try {
        		_flowStateChangedListener.onStateChanged(
        				this._currentHandler, null, causeEvent, causeArgs);
			} catch (Exception e) {
                logger.warn("exception when _flow.onStateChanged for flow({}) when doDestroy, event:({}), detail:{}",
                        this._name, causeEvent, ExceptionUtils.exception2detail(e));
			}
        }
        
        if (null!=this._flowLifecycleListener) {
            try {
            	this._flowLifecycleListener.afterFlowDestroy();
            }
            catch (Exception e) {
                logger.error("exception when invoke flow {}'s afterFlowDestroy, detail: {}",
                        this._name, ExceptionUtils.exception2detail(e));
            }
        }
    }

	public boolean isDestroyed() {
        return !this._isAlive.get();
	}

    private Pair<Object, Object[]> popPendingEvent() {
        this._destroyingLock.readLock().lock();
        try {
            if (isDestroyed()) {
                return null;
            }
            return this._pendingEvents.poll();
        } finally {
            this._destroyingLock.readLock().unlock();
        }
    }

    private boolean pushPendingEvent(final Object eventable, final Object[] args) throws Exception {
        this._destroyingLock.readLock().lock();
        if (!isDestroyed()) {
            try {
                this._pendingEvents.add(Pair.of(eventable, preprocessArgsByArgsHandler(eventable, args)));
            } finally {
                this._destroyingLock.readLock().unlock();
            }
            return true;
        } else {
            this._destroyingLock.readLock().unlock();
            logger.warn("flow {} already destroy, ignore pending event:({})", 
            		this._name, obj2event(eventable));
            notifyUnhandleEvent(eventable, args);
            return false;
        }
    }

    private void notifyUnhandleEvent(final Object eventable, final Object[] args) {
        if ( eventable instanceof EventUnhandleAware ) {
            try {
                if ( logger.isTraceEnabled() ) {
                    logger.trace("flow({}) invoke EventUnhandleAware({})'s onEventUnhandle with event:({})",
                            this._name, eventable, obj2event(eventable));
                }
                ((EventUnhandleAware)eventable).onEventUnhandle(obj2event(eventable), args);
            }
            catch (Exception e) {
                logger.warn("exception when flow({}) notify EventUnhandleAware({})'s onEventUnhandle with event({}), detail: {}",
                        this._name, eventable, obj2event(eventable), ExceptionUtils.exception2detail(e));
            }
        }
    }

    private boolean hasPendingEvent() {
        this._destroyingLock.readLock().lock();
        try {
            if (isDestroyed()) {
                return false;
            }
            return !this._pendingEvents.isEmpty();
        } finally {
            this._destroyingLock.readLock().unlock();
        }
    }

    private void dispatchPendingEvent() {
        if ( this._isFirsttimeActived ) {
            this._isFirsttimeActived = false;
            if ( null != this._statusReactor ) {
                if ( this._statusReactor.checkIfExceedLimit(this) ) {
                    this._statusReactor.onDestroyByExceedLimit(this);
                    this.destroy(null, null);
                    return;
                }
            }
        }

        final Pair<Object, Object[]> eventAndArgs = popPendingEvent();
        
        if (null != eventAndArgs) {
            final String event = obj2event(eventAndArgs.getFirst());
            try {
                if ( logger.isTraceEnabled() ) {
                    logger.trace("flow({}) with currentHandler({}) before dispatch event:({})", 
                            this._name, this._currentHandler.getName(), event);
                }
                
                if ( !this.dispatchEvent(
                        event,
                        eventAndArgs.getSecond()) ) {
                    // event !NOT! handle by current EventHandler
                    if ( logger.isTraceEnabled() ) {
                        logger.trace("flow({}) with currentHandler({}) !NOT! handle event:({})", 
                                this._name, this._currentHandler.getName(), event);
                    }
                    notifyUnhandleEvent(eventAndArgs.getFirst(), eventAndArgs.getSecond());
                }
                
                if ( logger.isTraceEnabled() ) {
                    logger.trace("flow({}) with currentHandler({}) after dispatch event:({}) and _isActived({})", 
                            this._name, this._currentHandler.getName(), 
                            event, this._isActived.get());
                }
            } catch (Throwable e) {
                logger.warn("exception when flow({}) process event:({}), detail:{}",
                        this._name, event,
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
                logger.warn("exception when flow({})'s preprocessArgsByArgsHandler for event:({}), detail:{},", 
                        this._name, eventable, ExceptionUtils.exception2detail(e));
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
                logger.warn("exception when flow({})'s postprocessArgsByArgsHandler for event:({}), detail:{},", 
                        this._name, eventable, ExceptionUtils.exception2detail(e));
            }
        }
    }
    
    private void schedulePendingEvent(final String causeEvent) {
        if ( this._exectionLoop.inExectionLoop() ) {
            if ( logger.isTraceEnabled()) {
                logger.trace("flow {}'s currentHandler({}): schedulePendingEvent cause by event:({}) in exectionLoop, just invoke direct.", 
                        this._name, this._currentHandler.getName(), causeEvent);
            }
            dispatchPendingEvent();
        }
        else {
            if ( logger.isTraceEnabled() ) {
                logger.trace("flow {}'s currentHandler({}): schedulePendingEvent cause by event:({}) NOT in exectionLoop, just invoke as submit.", 
                        this._name, this._currentHandler.getName(), causeEvent);
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
                if ( logger.isTraceEnabled() ) {
                    logger.trace("flow {}'s currentHandler({}): already actived, can't schedulePendingEvent cause by event:({})", 
                            this._name, this._currentHandler.getName(), causeEvent);
                }
            }
        }
    }
	
    private boolean setActived() throws Exception {
        if (isDestroyed()) {
            if ( logger.isTraceEnabled() ) {
                logger.trace("try setActived for destroyed flow({}), return false", this._name);
            }
            return false;
        }
        final boolean ret = this._isActived.compareAndSet(false, true);
        if (ret) {
            if (null != this._statusReactor) {
                try {
                    this._statusReactor.onActive(this);
                } catch (Exception e) {
                    logger.warn("exception when invoke statusReactor.onActive, detail: {}",
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
                    logger.warn("exception when invoke statusReactor.onUnactive, detail:{}",
                            ExceptionUtils.exception2detail(e));
                }
            }
        }
    }
	
    private boolean dispatchEvent(final String event, final Object[] args) {
        final EventHandler currentHandler = this.getCurrentHandler();
        if ( null == currentHandler ) {
            logger.error("Internal Error: current handler is null when accept event:({}), destroy flow({})", 
                    event, this._name);
            this.destroy(event, args);
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
            logger.error("exception when ({}).acceptEvent(event:({}) ... ), detail:{}", 
                currentHandler.getName(), 
                event,
                ExceptionUtils.exception2detail(e));
        }
        finally {
            setCurrentAcceptedEvent(null);
        }
        
        if ( null == nextHandler ) {
            // handled and next handler is null
            this.destroy(event, args);
            
            if ( logger.isDebugEnabled() ) {
                logger.debug("flow ({}) end normally for event:({}).", this._name, event);
            }
            return  eventHandled;
        }
        else if ( currentHandler.equals( nextHandler ) 
                //  return handler is BizStep.CURRENT_BIZSTEP
                //  so keep current handler unchanged
                || nextHandler == BizStep.CURRENT_BIZSTEP) {
            // no change
        }
        else {
            setCurrentHandler(nextHandler, event, args);
        }
        
        dispatchPendingEvent();
        if ( logger.isTraceEnabled() ) {
            logger.trace("after end of dispatchEvent invoke dispatchPendingEvent for flow({}) cause by event:({}) and _isActived({})",
                    this._name, event, this._isActived.get());
        }
        
        return  eventHandled;
    }
    
    private void setCurrentAcceptedEvent(final String event) {
        if (null!=this._eventNameAware) {
            try {
            	this._eventNameAware.setEventName(event);
            }
            catch (Exception e) {
                logger.error("exception when setEventName: event:({}) to flow {}, detail: {}",
                    event, this._name, ExceptionUtils.exception2detail(e));
            }
        }
    }
    
    private final Runnable _dispatchPendingRunnable = new Runnable() {
        public void run() {
            dispatchPendingEvent();
        }
    };

    private final String _name;
    private final ExectionLoop _exectionLoop;
    
    private final StatusReactor _statusReactor;
    
    private EventNameAware 	_eventNameAware;
    private EventHandlerAware _eventHandlerAware;
    private EndReasonProvider _endReasonProvider;
    private ExectionLoopAware _exectionLoopAware;
    private FlowLifecycleListener 	_flowLifecycleListener;
    private FlowStateChangedListener<EventHandler>	_flowStateChangedListener;
    
    private final AtomicBoolean _isActived = new AtomicBoolean(false);

    private final Queue<Pair<Object, Object[]>> _pendingEvents = 
            new ConcurrentLinkedQueue<>();
    
    private final long _createTime = System.currentTimeMillis();
    private volatile long _lastModify = System.currentTimeMillis();
    private final AtomicLong _activeTime = new AtomicLong(0);
    private volatile long _lastActiveTime;

    private final AtomicBoolean _isAlive = new AtomicBoolean(true);
    private final ReadWriteLock _destroyingLock = new ReentrantReadWriteLock(false);

    private volatile EventHandler _currentHandler = null;
    private volatile Object _reason = null;
    
    private final int _id = _IDSRC.getAndIncrement();
    
    private volatile boolean	_isFirsttimeActived = true;
	
    private static final AtomicInteger _IDSRC = new AtomicInteger(0);
}
