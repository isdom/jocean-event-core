/**
 * 
 */
package org.jocean.event.core;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jocean.event.api.EventReceiver;
import org.jocean.event.api.EventReceiverSource;
import org.jocean.event.api.internal.EventHandler;
import org.jocean.event.api.internal.Eventable;
import org.jocean.event.api.internal.FlowLifecycleAware;
import org.jocean.idiom.COWCompositeSupport;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.ExectionLoop;
import org.jocean.idiom.ReflectUtils;
import org.jocean.idiom.Visitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author isdom
 *
 */
public class FlowContainer {
	
    private static final Logger LOG = 
    	LoggerFactory.getLogger(FlowContainer.class);

    private static final AtomicInteger ALL_CONTAINER_COUNTER = new AtomicInteger(0);
    
	public FlowContainer(final String name) {
    	this.name = ( null != name ? name : super.toString() );	// ensure this.name is not null
    	this._id = ALL_CONTAINER_COUNTER.incrementAndGet();
    }

	public EventReceiverSource genEventReceiverSource(final ExectionLoop exectionLoop) {
		return	new EventReceiverSource() {
            @Override
            public EventReceiver create(final Object flow, final EventHandler initState) {
                return  createEventReceiverOf(flow, initState, exectionLoop);
            }

            @Override
            public EventReceiver createFromInnerState(final EventHandler initState) {
                final Object flow = ReflectUtils.getOuterFromInnerObject(initState);
                if (null == flow) {
                    LOG.warn("invalid inner initState {},can't get it's outer flow.", initState);
                    throw new RuntimeException("invalid inner initState " + initState +",can't get it's outer flow.");
                }
                return createEventReceiverOf(flow, initState, exectionLoop);
            }};
	}
	
	public FlowTracker genFlowTracker() {
		
		return	new FlowTracker() {
			@Override
			public void registerFlowStateChangeListener(
					final FlowStateChangeListener listener) {
				if ( null == listener ) {
					LOG.warn("registerFlowStateChangeListener: listener is null, just ignore");
				}
				else {
					if ( !_flowStateChangeListenerSupport.addComponent(listener) ) {
						LOG.warn("registerFlowStateChangeListener: listener {} has already registered", 
								listener);
					}
				}
			}
			
			@Override
			public void unregisterFlowStateChangeListener(
					final FlowStateChangeListener listener) {
				if ( null == listener ) {
					LOG.warn("unregisterFlowStateChangeListener: listener is null, just ignore");
				}
				else {
					_flowStateChangeListenerSupport.removeComponent(listener);
				}
			}
		};
	}

	private <FLOW> EventReceiver createEventReceiverOf(
	        final FLOW flow, 
	        final EventHandler initHandler,
            final ExectionLoop exectionLoop
	        ) {
		//	create new receiver
		final FlowContextImpl ctx = initFlowCtx(flow, initHandler, exectionLoop);
		
        final EventReceiver newReceiver = genEventReceiverWithCtx(ctx);
        
		if ( flow instanceof FlowLifecycleAware ) {
			try {
				((FlowLifecycleAware)flow).afterEventReceiverCreated(newReceiver);
			}
			catch (Exception e) {
				LOG.error("exception when invoke flow {}'s afterEventReceiverCreated, detail: {}",
						flow, ExceptionUtils.exception2detail(e));
			}
		}
		
		return	newReceiver;
	}
	
	/**
	 * @param ctx
	 * @return
	 */
	private EventReceiver genEventReceiverWithCtx(final FlowContextImpl ctx) {
		return	new EventReceiver() {

			@Override
			public boolean acceptEvent(final String event, final Object... args) throws Exception {
		        try {
		            return ctx.processEvent(event, args);
		        }
		        catch (final Throwable e) {
		            LOG.error("exception when flow({})'s processEvent, detail:{}, try end flow", 
		                    ctx.getFlow(), ExceptionUtils.exception2detail(e));
		            ctx.destroy();
//		            throw e;
		            return false;
		        }
			}

            @Override
            public boolean acceptEvent(final Eventable eventable, final Object... args)
                    throws Exception {
                try {
                    return ctx.processEvent(eventable, args);
                }
                catch (final Throwable e) {
                    LOG.error("exception when flow({})'s processEvent, detail:{}, try end flow", 
                            ctx.getFlow(), ExceptionUtils.exception2detail(e));
                    ctx.destroy();
//                    throw e;
                    return false;
                }
            }
            
            @Override
            public String toString() {
                return "EventReceiver [flow=" + ctx.getFlow() +"]";
            }
		};
	}
	
	public String getName() {
		return this.name;
	}

	public int getId() {
		return	this._id;
	}

	public int getFlowTotalCount() {
		return this._totalFlowCount.get();
	}

	public long getDealHandledCount() {
		return dealHandledCount.get();
	}
	
	public long getDealCompletedCount() {
		return dealCompletedCount.get();
	}

	public long getDealBypassCount() {
		return dealBypassCount.get();
	}

	private FlowContextImpl initFlowCtx(
	        final Object flow, 
	        final EventHandler initHandler,
            final ExectionLoop exectionLoop 
	        ) {
		final FlowContextImpl newCtx = 
	        new FlowContextImpl(flow, exectionLoop, null, this._flowStateChangeListener)
                .setCurrentHandler(initHandler, null, null);
		
		if ( this._flowContexts.add(newCtx) ) {
    		// add new context
    		this._totalFlowCount.incrementAndGet();
		}
		
        incDealHandledCount();
        
		return	newCtx;
	}
	
	private void onFlowCtxDestroyed(final FlowContextImpl ctx) {
		if ( this._flowContexts.remove(ctx) ) {
			//	移除操作有效
			this._totalFlowCount.decrementAndGet();
		}
		
		incDealCompletedCount();
		
		this._flowStateChangeListenerSupport.foreachComponent(new Visitor<FlowStateChangeListener>() {

			@Override
			public void visit(final FlowStateChangeListener listener) throws Exception {
				listener.afterFlowDestroy(ctx);
			}});
	}

	private void incDealHandledCount() {
		dealHandledCount.incrementAndGet();
	}
	
	private void incDealCompletedCount() {
		dealCompletedCount.incrementAndGet();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.name + "-" + this._id;
	}

	private final FlowStateChangeListener _flowStateChangeListener = new FlowStateChangeListener() {

        @Override
        public void beforeFlowChangeTo(
                final FlowContext ctx,
                final EventHandler nextHandler, 
                final String causeEvent, 
                final Object[] causeArgs)
                throws Exception {
            //  if causeEvent is null, means it's initHandler
            if ( null != causeEvent ) {
                _flowStateChangeListenerSupport.foreachComponent(
                        new Visitor<FlowStateChangeListener>() {
                    @Override
                    public void visit(final FlowStateChangeListener listener)
                            throws Exception {
                        listener.beforeFlowChangeTo(ctx, nextHandler, causeEvent, causeArgs);
                    }});
            }
        }

        @Override
        public void afterFlowDestroy(final FlowContext ctx) throws Exception {
            onFlowCtxDestroyed((FlowContextImpl)ctx);
        }};
	
	private	final Set<FlowContextImpl> _flowContexts = 
			new ConcurrentSkipListSet<FlowContextImpl>();
	
	private final String		name;
	private	final int			_id;
	
	private	final AtomicInteger	_totalFlowCount = new AtomicInteger(0);
	
	private	final AtomicLong dealHandledCount = new AtomicLong(0);
	private	final AtomicLong dealCompletedCount = new AtomicLong(0);
	private	final AtomicLong dealBypassCount = new AtomicLong(0);
	
	private final COWCompositeSupport<FlowStateChangeListener> _flowStateChangeListenerSupport
		= new COWCompositeSupport<FlowStateChangeListener>();
	
}
