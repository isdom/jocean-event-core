/**
 * 
 */
package org.jocean.syncfsm.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.ExectionLoop;
import org.jocean.syncfsm.api.annotation.OnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author isdom
 * 
 */
public class SyncFSMUtils {

    private static final Logger LOG = LoggerFactory
            .getLogger(SyncFSMUtils.class);

    public static EventReceiver combineEventReceivers(
            final EventReceiver... receivers) {
        return new EventReceiver() {

            @Override
            public boolean acceptEvent(final String event, final Object... args)
                    throws Exception {
                boolean handled = false;
                for (EventReceiver receiver : receivers) {
                    try {
                        if (receiver.acceptEvent(event, args)) {
                            handled = true;
                        }
                    } catch (final Exception e) {
                        LOG.error("failed to acceptEvent event:({}), detail: {}",
                                event, ExceptionUtils.exception2detail(e));
                    }
                }
                return handled;
            }

            @Override
            public boolean acceptEvent(final Eventable eventable, final Object... args)
                    throws Exception {
                boolean handled = false;
                for (EventReceiver receiver : receivers) {
                    try {
                        if (receiver.acceptEvent(eventable, args)) {
                            handled = true;
                        }
                    } catch (final Exception e) {
                        LOG.error("failed to acceptEvent event:({}), detail: {}",
                                eventable.event(), ExceptionUtils.exception2detail(e));
                    }
                }
                return handled;
            }
        };
    }

    public static EventReceiver genAsyncEventReceiver(
            final EventReceiver receiver, 
            final ExectionLoop exectionLoop,
            final ArgsHandler argsHandler) {
        return new EventReceiver() {
            @Override
            public boolean acceptEvent(final String event, final Object... args)
                    throws Exception {
                final Object[] safeArgs = (null != argsHandler) ? argsHandler
                        .beforeAcceptEvent(args) : args;
                if (exectionLoop.inExectionLoop()) {
                    try {
                        return receiver.acceptEvent(event, safeArgs);
                    } finally {
                        if (null != argsHandler) {
                            try {
                                argsHandler.afterAcceptEvent(safeArgs);
                            } catch (Exception e) {
                                LOG.warn(
                                        "exception when argsHandler.afterAcceptEvent for event:{}, detail:{},",
                                        event,
                                        ExceptionUtils.exception2detail(e));
                            }
                        }
                    }
                } else {
                    exectionLoop.submit(new Runnable() {

                        @Override
                        public void run() {
                            try {
                                receiver.acceptEvent(event, safeArgs);
                            } catch (Exception e) {
                                LOG.warn(
                                        "exception when acceptEvent for event:{}, detail:{},",
                                        event,
                                        ExceptionUtils.exception2detail(e));
                            } finally {
                                if (null != argsHandler) {
                                    try {
                                        argsHandler.afterAcceptEvent(safeArgs);
                                    } catch (Exception e) {
                                        LOG.warn(
                                                "exception when argsHandler.afterAcceptEvent for event:{}, detail:{},",
                                                event, ExceptionUtils.exception2detail(e));
                                    }
                                }
                            }
                        }
                    });
                }
                return true;
            }

            @Override
            public boolean acceptEvent(final Eventable eventable, final Object... args)
                    throws Exception {
                final Object[] safeArgs = (null != argsHandler) ? argsHandler
                        .beforeAcceptEvent(args) : args;
                if (exectionLoop.inExectionLoop()) {
                    try {
                        return receiver.acceptEvent(eventable, safeArgs);
                    } finally {
                        if (null != argsHandler) {
                            try {
                                argsHandler.afterAcceptEvent(safeArgs);
                            } catch (Exception e) {
                                LOG.warn(
                                        "exception when argsHandler.afterAcceptEvent for event:{}, detail:{},",
                                        eventable.event(),
                                        ExceptionUtils.exception2detail(e));
                            }
                        }
                    }
                } else {
                    exectionLoop.submit(new Runnable() {

                        @Override
                        public void run() {
                            try {
                                receiver.acceptEvent(eventable, safeArgs);
                            } catch (Exception e) {
                                LOG.warn(
                                        "exception when acceptEvent for event:{}, detail:{},",
                                        eventable.event(),
                                        ExceptionUtils.exception2detail(e));
                            } finally {
                                if (null != argsHandler) {
                                    try {
                                        argsHandler.afterAcceptEvent(safeArgs);
                                    } catch (Exception e) {
                                        LOG.warn(
                                                "exception when argsHandler.afterAcceptEvent for event:{}, detail:{},",
                                                eventable.event(), ExceptionUtils.exception2detail(e));
                                    }
                                }
                            }
                        }
                    });
                }
                return true;
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <INTF> INTF buildInterfaceAdapter(final Class<INTF> intf,
            final EventReceiver receiver) {
        return (INTF) Proxy.newProxyInstance(Thread.currentThread()
                .getContextClassLoader(), new Class<?>[] { intf },
                new ReceiverAdapterHandler(receiver));
    }

    private static final class ReceiverAdapterHandler implements
            InvocationHandler {

        ReceiverAdapterHandler(final EventReceiver receiver) {
            if (null == receiver) {
                throw new NullPointerException("EventReceiver can't be null");
            }
            this._receiver = receiver;
        }

        @Override
        public Object invoke(final Object proxy, final Method method,
                final Object[] args) throws Throwable {
            //   An invocation of the hashCode, equals, or toString methods
            // declared in java.lang.Object on a proxy instance will be 
            // encoded and dispatched to the invocation handler's invoke
            // method in the same manner as interface method invocations are
            // encoded and dispatched, as described above. The declaring 
            // class of the Method object passed to invoke will be
            // java.lang.Object. Other public methods of a proxy instance
            // inherited from java.lang.Object are not overridden by a proxy
            // class, so invocations of those methods behave like they do
            // for instances of java.lang.Object.
            if (method.getName().equals("hashCode")) {
                return this._receiver.hashCode();
            } else if (method.getName().equals("equals")) {
                return (proxy == args[0]);
            } else if (method.getName().equals("toString")) {
                return this._receiver.toString();
            }
            final OnEvent onevent = method.getAnnotation(OnEvent.class);
            final String eventName = (null != onevent) ? onevent.event()
                    : method.getName();
            boolean isAccepted = _receiver.acceptEvent(eventName, args);
            if (method.getReturnType().equals(Boolean.class)
                    || method.getReturnType().equals(boolean.class)) {
                return isAccepted;
            } else {
                return null;
            }
        }

        private final EventReceiver _receiver;
    }
}
