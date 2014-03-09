/**
 * 
 */
package org.jocean.syncfsm.container;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jocean.syncfsm.api.EventHandler;
import org.jocean.syncfsm.api.annotion.SameThread;
import org.jocean.syncfsm.api.exception.NotInSameThreadException;
import org.jocean.syncfsm.common.FlowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author isdom
 *
 */
class FlowContextImpl implements FlowContext {

	boolean isFlowEventHandlerAware = false;
	boolean isFlowEventNameAware = false;
	boolean isFlowHasEndReason = false;
	
    private static final Logger LOG = 
    		LoggerFactory.getLogger(FlowContextImpl.class);

	public FlowContextImpl(final Object flow) {
		this._flow = flow;
		this._runInSameThread = ifRunInSameThread(flow);
	}

	private boolean ifRunInSameThread(final Object flow) {
		final SameThread same = flow.getClass().getAnnotation(SameThread.class);
		return	(null == same) ? false : same.value();
	}

	public void checkCurrentThread() {
		if ( this._runInSameThread ) {
			final long currentId = Thread.currentThread().getId();
			if ( !this._threadId.compareAndSet(-1, currentId) ) {
				if ( currentId != this._threadId.get() ) {
					throw new NotInSameThreadException("first action in thread (" + this._threadId + "), but now run in thread(" + currentId + ")");
				}
			}
		}
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
		return _createTime;
	}

	@Override
	public long getLastModify() {
		return _lastModify;
	}

	@Override
	public long getTimeToLive() {
		return	isDestroyed() 
				? (_lastModify - _createTime) 
				: (System.currentTimeMillis() - _createTime);
	}
	
	public void setEndReason(final Object reason) {
		this._reason = reason;
	}
	
	FlowContextImpl setCurrentHandler(final EventHandler handler) {
		if ( ( (this._currentHandler == null) && (handler != null) )
			|| ( (this._currentHandler != null) && !this._currentHandler.equals( handler ) ) ) {
			this._currentHandler = handler;
			_lastModify = System.currentTimeMillis();
		}
		return	this;
	}
	
	/* (non-Javadoc)
	 * @see com.skymobi.fsm.FSMContext#destroy()
	 */
	public void destroy() {
		
		if ( _isAlive.compareAndSet(true, false) ) {
			_lastModify = System.currentTimeMillis();
		}
	}

	public boolean isDestroyed() {
		return !_isAlive.get();
	}

	protected	final long	_createTime = System.currentTimeMillis();
	protected	long		_lastModify = System.currentTimeMillis();
	
	private	final AtomicBoolean	_isAlive = new AtomicBoolean(true);
	
	private	EventHandler 	_currentHandler = null;
	private Object 			_reason = null;
	private final Object	_flow;
	private	final boolean	_runInSameThread;
	private final AtomicLong	_threadId = new AtomicLong(-1);
}
