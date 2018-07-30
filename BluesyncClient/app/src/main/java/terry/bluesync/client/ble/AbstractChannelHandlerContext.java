package terry.bluesync.client.ble;


import terry.bluesync.client.util.LogUtil;

public abstract class AbstractChannelHandlerContext {
    private static final String TAG = AbstractChannelHandlerContext.class.getSimpleName();

    private static final int MASK_EXCEPTION_CAUGHT = 1 ;
    private static final int MASK_ACTIVE = 1 << 1;
    private static final int MASK_INACTIVE = 1 << 2;
    private static final int MASK_READ = 1 << 3;
    private static final int MASK_WRITE = 1 << 4;

    private static final int MASKGROUP_INBOUND = MASK_EXCEPTION_CAUGHT | MASK_READ | MASK_ACTIVE | MASK_INACTIVE;
    private static final int MASKGROUP_OUTBOUND = MASK_WRITE;

    public static int skipFlags(ChannelHandler handler) {
        return skipFlags0(handler.getClass());
    }

    protected static int skipFlags0(Class<? extends ChannelHandler> handlerType) {
        int flags = 0;
        try {

            if (isSkippable(handlerType, "active")) {
                flags |= MASK_ACTIVE;
            }

            if (isSkippable(handlerType, "inactive")) {
                flags |= MASK_INACTIVE;
            }

            if (isSkippable(handlerType, "read", Object.class)) {
                flags |= MASK_READ;
            }

            if (isSkippable(handlerType, "write", Object.class)) {
                flags |= MASK_WRITE;
            }

            if (isSkippable(handlerType, "exceptionCaught", Throwable.class)) {
                flags |= MASK_EXCEPTION_CAUGHT;
            }
        } catch (Exception e) {
            LogUtil.e(TAG, e.toString());
        }

        return flags;
    }

    @SuppressWarnings("Since15")
    private static boolean isSkippable (
            Class<?> handlerType, String methodName, Class<?>... paramTypes) throws Exception {

        Class[] newParamTypes = new Class[paramTypes.length + 1];
        newParamTypes[0] = AbstractChannelHandlerContext.class;
        System.arraycopy(paramTypes, 0, newParamTypes, 1, paramTypes.length);

        return handlerType.getMethod(methodName, newParamTypes).isAnnotationPresent(ChannelHandler.Skip.class);
    }

    final private String name;
    private ChannelPipeline pipeline;
    private ChannelHandlerInvoker invoker = null;
    private final int skipFlags;

    volatile AbstractChannelHandlerContext next;
    volatile AbstractChannelHandlerContext prev;

    abstract public ChannelHandler handler();

    public AbstractChannelHandlerContext(String name, ChannelPipeline pipeline, ChannelHandlerInvoker invoker, int flag) {
        this.name = name;
        this.pipeline = pipeline;
        this.invoker = invoker;
        this.skipFlags = flag;
    }

    public String name() {
        return name;
    }

    public Channel channel() {
        return pipeline.channel();
    }

    public ChannelHandlerInvoker invoker() {
        return invoker;
    }

    public ChannelPipeline pipeline() {
        return pipeline;
    }

    public void fireActive() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker.invokeActive(next);
    }

    public void fireInactive() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker.invokeInactive(next);
    }

    public void fireRead(Object msg) {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker().invokeRead(next, msg);
    }

    public void fireWrite(Object msg) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invoker().invokeWrite(next, msg);
    }

    public AbstractChannelHandlerContext fireExceptionCaught(Throwable cause) {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker().invokeExceptionCaught(next, cause);
        return this;
    }

    private AbstractChannelHandlerContext findContextInbound() {
        AbstractChannelHandlerContext ctx = this;
        do {
            ctx = ctx.next;
        } while ((ctx.skipFlags & MASKGROUP_INBOUND) == MASKGROUP_INBOUND);
        return ctx;
    }

    private AbstractChannelHandlerContext findContextOutbound() {
        AbstractChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while ((ctx.skipFlags & MASKGROUP_OUTBOUND) == MASKGROUP_OUTBOUND);
        return ctx;
    }
}
