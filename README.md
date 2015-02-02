jocean-event-core
==============

jocean's event over sync fsm core to normalize business logic

TODO:

  1、在 FLowContainer 中添加 getAllEventReceiver 这样的接口，获取当前有效的事件接收器，便于跟踪全局业务逻辑细节，及时发现逻辑问题。

  2、~~在 Api/Core 中，支持在发送事件时，判断Eventable对象如果实现 ArgsHandlerSource接口，则进行事件参数的事件处理前/后的保护，以解决ReferenceCounted(引用计数)实例保护问题。~~
    (已实现: https://git.oschina.net/isdom/jocean-event-core/commit/c22da93400eb728c7019f536f1ec373d17a67c6d)

  3、定义 ExectionLoopSwitcher接口，允许实现了 ExectionLoopSwitcherAware 接口的 flow 可以手动指定其被执行的 ExectionLoop实例。

  4、可能存在 pushPendingEvent 与 destroy 中的  while (!this._pendingEvents.isEmpty()) {
                final Iterator<Pair<Object,Object[]>> iter = this._pendingEvents.iterator();
                final Pair<Object, Object[]> eventAndArgs = iter.next();
                notifyUnhandleEvent(eventAndArgs.getFirst(), eventAndArgs.getSecond());
                postprocessArgsByArgsHandler(eventAndArgs.getFirst(), eventAndArgs.getSecond());
                iter.remove();
            }
            
     该段代码，存在多线程时，_pendingEvents中的events没有全部处理完成。TO fix
  
2014-08-19： release 0.1.4 版本：
  1、在FlowContextImpl实现中支持 Eventable 具象类实现 ArgsHandler接口，此时会调用ArgsHandler.beforeInvoke / ArgsHandler.afterInvoke 对参数进行生命周期的保护

2014-06-11： release 0.1.3 版本：
  1、将 AbstractFlow.fireDelayEventAndPush 变更为 public 方法
  2、http://rdassist.widget-inc.com:65480/browse/CHANNEL-103:改进 AbstractFlow 中的一次性定时器 启动和移除API，将内部保存定时器任务更改为由外部提供Collection<Detachable>来保存定时器任务
