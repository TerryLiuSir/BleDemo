package terry.bluesync.client.ble;


import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import terry.bluesync.client.util.LogUtil;

public class ChannelHandlerInvoker {
    private static final String TAG = ChannelHandlerInvoker.class.getSimpleName();
    private static final boolean DEBUG = true;

    private ThreadPoolExecutor executor;

    public ChannelHandlerInvoker() {
        this.executor = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
    }

    public void destroy() {
        executor.shutdown();
        executor = null;
    }

    public void invokeRunnable(Runnable runnable) {
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
            logPrintObject(ctx, "descriptorWriteNow()");

            ctx.handler().write(ctx, msg);
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
        LogPrint("[" +  ctx.channel().gatt().getDevice() + "] " + ctx.handler().getClass().getSimpleName() + "." + msg);
    }

    private void LogPrint(String msg) {
        if(DEBUG) {
            LogUtil.d(TAG, msg);
        }
    }
}
