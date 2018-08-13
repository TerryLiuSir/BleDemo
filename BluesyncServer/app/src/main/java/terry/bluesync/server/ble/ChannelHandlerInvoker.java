package terry.bluesync.server.ble;



import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import terry.bluesync.server.util.LogUtil;

public class ChannelHandlerInvoker {
    private static final String TAG = ChannelHandlerInvoker.class.getSimpleName();
    private static final boolean DEBUG = false;

    private ExecutorService executor;

    public ChannelHandlerInvoker() {
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void destroy() {
        executor.shutdown();
        executor = null;
    }

    public void invokeRunable(Runnable runnable) {
        if (executor == null) {
            runnable.run();
        } else {
            executor.execute(runnable);
        }
    }

    public void invokeActive(final AbstractChannelHandlerContext ctx) {
        if (executor == null) {
            invokeActiveNow(ctx);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeActiveNow(ctx);
                }
            });
        }
    }

    private void invokeActiveNow(final AbstractChannelHandlerContext ctx) {
        try {
            logPrintObject(ctx, "active()");

            ctx.handler().active(ctx);
        } catch (Throwable t) {
            notifyHandlerException(ctx, t);
        }
    }

    public void invokeInactive(final AbstractChannelHandlerContext ctx) {
        if (executor == null) {
            invokeInactiveNow(ctx);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeInactiveNow(ctx);
                }
            });
        }
    }

    private void invokeInactiveNow(final AbstractChannelHandlerContext ctx) {
        try {
            logPrintObject(ctx, "inactive()");

            ctx.handler().inactive(ctx);
        } catch (Throwable t) {
            notifyHandlerException(ctx, t);
        }
    }

    public void invokeDescriptorWrite(final AbstractChannelHandlerContext ctx) {
        if (executor == null) {
            invokeDescriptorWriteNow(ctx);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeDescriptorWriteNow(ctx);
                }
            });
        }
    }

    private void invokeDescriptorWriteNow(final AbstractChannelHandlerContext ctx) {
        try {
            logPrintObject(ctx, "descriptorWrite");

            ctx.handler().descriptorWrite(ctx);
        } catch (Throwable t) {
            notifyHandlerException(ctx, t);
        }
    }

    public void invokeRead(final AbstractChannelHandlerContext ctx, final Object msg) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        if (executor == null) {
            invokeReadNow(ctx, msg);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeReadNow(ctx, msg);
                }
            });
        }
    }

    private void invokeReadNow(final AbstractChannelHandlerContext ctx, final Object msg) {
        try {
            logPrintObject(ctx, "read()" + ", msg=" + String.valueOf(msg));

            ctx.handler().read(ctx, msg);
        } catch (Throwable t) {
            notifyHandlerException(ctx, t);
        }
    }

    public void invokeWrite(final AbstractChannelHandlerContext ctx, final Object msg) {
        if (msg == null) {
            throw new NullPointerException("msg");
        }

        if (executor == null) {
            invokeWriteNow(ctx, msg);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeWriteNow(ctx, msg);
                }
            });
        }
    }

    private void invokeWriteNow(final AbstractChannelHandlerContext ctx, Object msg) {
        try {
            logPrintObject(ctx, "write()" + ", msg=" + String.valueOf(msg));

            ctx.handler().write(ctx, msg);
        } catch (Throwable t) {
            notifyHandlerException(ctx, t);
        }
    }

    public void invokeDisconnect(final AbstractChannelHandlerContext ctx) {
        if (executor == null) {
            invokeDisconnectNow(ctx);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    invokeDisconnectNow(ctx);
                }
            });
        }
    }

    private void invokeDisconnectNow(final AbstractChannelHandlerContext ctx) {
        try {
            logPrintObject(ctx, "disconnect()");

            ctx.handler().disconnect(ctx);
        } catch (Throwable t) {
            notifyHandlerException(ctx, t);
        }
    }

    public void invokeExceptionCaught(final AbstractChannelHandlerContext ctx, final Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }

        if (executor == null) {
            invokeExceptionCaughtNow(ctx, cause);
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        invokeExceptionCaughtNow(ctx, cause);
                    }
                });
            } catch (Throwable t) {
                LogUtil.e(TAG, "Failed to submit an exceptionCaught() event.", t);
                LogUtil.e(TAG, "The exceptionCaught() event that was failed to submit was:", cause);
            }
        }
    }

    private void notifyHandlerException(AbstractChannelHandlerContext ctx, Throwable cause) {
        if (inExceptionCaught(cause)) {
            LogUtil.e(TAG, "An exception was thrown by a user handler " +
                    "while handling an exceptionCaught event", cause);
            return;
        }

        invokeExceptionCaughtNow(ctx, cause);
    }

    private static boolean inExceptionCaught(Throwable cause) {
        do {
            StackTraceElement[] trace = cause.getStackTrace();
            if (trace != null) {
                for (StackTraceElement t : trace) {
                    if (t == null) {
                        break;
                    }
                    if ("exceptionCaught".equals(t.getMethodName())) {
                        return true;
                    }
                }
            }

            cause = cause.getCause();
        } while (cause != null);

        return false;
    }

    public void invokeExceptionCaughtNow(final AbstractChannelHandlerContext ctx, final Throwable cause) {
        try {
            logPrintObject(ctx, "exceptionCaught()" + ", cause=" + cause.toString());

            ctx.handler().exceptionCaught(ctx, cause);
        } catch (Throwable t) {
            LogUtil.e(TAG, "An exception was thrown by a user handler's exceptionCaught() method:", t);
            LogUtil.e(TAG, ".. and the ause of the exceptionCaught() was:", cause);
        }
    }

    private void logPrintObject(AbstractChannelHandlerContext ctx, String msg) {
        LogPrint("[" +  ctx.channel().device() + "] " + ctx.handler().getClass().getSimpleName() + "." + msg);
    }

    private void LogPrint(String msg) {
        if(DEBUG) {
            LogUtil.d(TAG, msg);
        }
    }
}
