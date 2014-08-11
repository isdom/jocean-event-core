/**
 * 
 */
package org.jocean.event.core;

import java.util.Random;

import org.jocean.event.api.AbstractFlow;
import org.jocean.event.api.BizStep;
import org.jocean.event.api.EventReceiver;
import org.jocean.event.api.annotation.OnEvent;
import org.jocean.idiom.Detachable;
import org.jocean.idiom.ExectionLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author isdom
 *
 */
public class FlowDemo {

    private static final Logger LOG = 
    		LoggerFactory.getLogger(FlowDemo.class);

    public class DemoFlow extends AbstractFlow<DemoFlow> {
        
        final BizStep LOCKED = new BizStep("LOCKED") {
                    @OnEvent(event="coin")
                    BizStep onCoin() {
                        System.out.println("handler:" + currentEventHandler() + ",event:" + currentEvent());
                        LOG.info("{}: accept {}", new Object[]{
                            currentEventHandler().getName(),  currentEvent()
                        });
                        return UNLOCKED;
                    }
                }
        		.freeze();
        		
        final BizStep UNLOCKED = new BizStep("UNLOCKED") {
                    @OnEvent(event="pass")
                    BizStep onPass() {
                        System.out.println("handler:" + currentEventHandler() + ",event:" + currentEvent());
                        LOG.info("{}: accept {}", new Object[]{
                                currentEventHandler().getName(),  currentEvent()
                            });
                        return LOCKED;
                    }
                }
        		.freeze();
        		
        final BizStep INIT = new BizStep("INIT")
            .handler( handlersOf(LOCKED) )
            .handler( handlersOf(UNLOCKED) )
            .freeze();
    }
    
	private void run() throws Exception {
		
	    final DemoFlow flow = new DemoFlow();
	    final ExectionLoop exectionLoop = new ExectionLoop() {

            @Override
            public boolean inExectionLoop() {
                return true;
            }

            @Override
            public Detachable submit(Runnable runnable) {
                runnable.run();
                return new Detachable() {
                    @Override
                    public void detach() {
                    }};
            }

            @Override
            public Detachable schedule(Runnable runnable, long delayMillis) {
                runnable.run();
                return new Detachable() {
                    @Override
                    public void detach() {
                    }};
            }};
            
		final EventReceiver receiver = 
		        new FlowContainer("demo").genEventReceiverSource(exectionLoop)
		            .create(flow,  flow.INIT) ;
    		
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
		
		return (i1 % 2 == 1 ? "coin" : "pass");
	}

}
