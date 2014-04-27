jocean-syncfsm
==============

jocean's event over fsm to normalize business logic

TODO:

  1、在 FLowContainer 中添加 getAllEventReceiver 这样的接口，获取当前有效的事件接收器，便于跟踪全局业务逻辑细节，及时发现逻辑问题。
  
  2、在 Api/Core 中，支持在发送事件时，判断Eventable对象如果实现 ArgsHandlerSource接口，则进行事件参数的事件处理前/后的保护，以解决ReferenceCounted(引用计数)实例保护问题。
  
  3、定义 ExectionLoopSwitcher接口，允许实现了 ExectionLoopSwitcherAware 接口的 flow 可以手动指定其被执行的 ExectionLoop实例。
