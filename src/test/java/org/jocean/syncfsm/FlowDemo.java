/**
 * 
 */
package org.jocean.syncfsm;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jocean.syncfsm.api.AbstractFlow;
import org.jocean.syncfsm.api.BizStep;
import org.jocean.syncfsm.api.EventHandler;
import org.jocean.syncfsm.api.EventReceiver;
import org.jocean.syncfsm.api.ExectionLoop;
import org.jocean.syncfsm.api.FlowSource;
import org.jocean.syncfsm.api.annotion.OnEvent;
import org.jocean.syncfsm.container.FlowContainer;

/**
 * @author isdom
 *
 */
public class FlowDemo {

    private static final Logger LOG = 
    		LoggerFactory.getLogger(FlowDemo.class);

    public class DemoFlow extends AbstractFlow<DemoFlow> {
        final BizStep LOCKED = 
        		new BizStep("LOCKED")
        		.handler( selfInvoker("onCoin") )
        		.freeze();
        		
        final BizStep UNLOCKED = 
        		new BizStep("UNLOCKED")  
        		.handler( selfInvoker( "onPass") )
        		.freeze();

		@OnEvent(event="coin")
		EventHandler onCoin() {
			System.out.println("handler:" + currentEventHandler() + ",event:" + currentEvent());
			LOG.info("{}: accept {}", new Object[]{
				currentEventHandler().getName(),  currentEvent()
			});
			return UNLOCKED;
		}
		  
		@OnEvent(event="pass")
		EventHandler onPass() {
			System.out.println("handler:" + currentEventHandler() + ",event:" + currentEvent());
			LOG.info("{}: accept {}", new Object[]{
					currentEventHandler().getName(),  currentEvent()
				});
			return LOCKED;
		}
    }
    
	private void run() throws Exception {
		
		final EventReceiver receiver = new FlowContainer("demo").genEventReceiverSource().create(
    				new FlowSource<DemoFlow>() {
    			
    			@Override
    			public DemoFlow getFlow() {
    				return new DemoFlow();
    			}

				@Override
				public EventHandler getInitHandler(final DemoFlow flow) {
		        	return new BizStep("INIT")
		        		.handler( flow.selfInvoker("onCoin") )
		        		.handler( flow.selfInvoker("onPass") )
		        		.freeze();
				}

                @Override
                public ExectionLoop getExectionLoop(final DemoFlow flow) {
                    return new ExectionLoop() {

                        @Override
                        public boolean inExectionLoop() {
                            return true;
                        }

                        @Override
                        public void submit(Runnable runnable) {
                            runnable.run();
                        }

                        @Override
                        public void schedule(Runnable runnable, long delayMillis) {
                            runnable.run();
                        }};
                }});
    		
		new Thread(new Runnable(){

			@Override
			public void run() {
				try {
					while (true) {
						final String event = genEvent();
			    		boolean ret = receiver.acceptEvent(event);
			    		LOG.debug("acceptEvent {} return value {}", event, ret);
			    		
			    		Thread.sleep(1000L);
			    	}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}}).start();
	}
	
    public static void main(String[] args) throws Exception {
    	new FlowDemo().run();
    }
    
	private static String genEvent() {
		final Random r = new Random();
		
		int i1 = r.nextInt();
		int i2 = r.nextInt();
		
		return (i1 % 2 == 1 ? "coin" : "pass");
	}

}
