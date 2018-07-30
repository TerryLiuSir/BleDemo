package terry.bluesync.client.ble;

public class ChannelHandlerAdapter implements ChannelHandler {

    @Skip
    @Override
    public void active(AbstractChannelHandlerContext ctx) throws Exception {
        ctx.fireActive();
    }

    @Skip
    @Override
    public void inactive(AbstractChannelHandlerContext ctx) throws Exception {
        ctx.fireInactive();
    }

    @Skip
    @Override
    public void read(AbstractChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireRead(msg);
    }

    @Skip
    @Override
    public void write(AbstractChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.fireWrite(msg);
    }

    @Skip
    @Override
    public void exceptionCaught(AbstractChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}
