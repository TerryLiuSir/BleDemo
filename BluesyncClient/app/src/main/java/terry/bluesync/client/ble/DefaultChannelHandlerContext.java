package terry.bluesync.client.ble;

public class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {
    private final ChannelHandler handler;

    public DefaultChannelHandlerContext(String name, ChannelPipeline pipeline, ChannelHandlerInvoker invoker, ChannelHandler handler) {
        super(name, pipeline, invoker, AbstractChannelHandlerContext.skipFlags(checkNull(handler)));
        this.handler = handler;
    }

    private static ChannelHandler checkNull(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        return handler;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }
}
