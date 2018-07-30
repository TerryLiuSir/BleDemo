package terry.bluesync.client.ble;

import android.os.Handler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import terry.bluesync.client.util.ByteUtil;
import terry.bluesync.client.util.DataSplitUtil;
import terry.bluesync.client.util.LogUtil;

public class ChannelPipeline {
    private static final String TAG = ChannelPipeline.class.getSimpleName();
    private static final boolean DEBUG = true;

    private Channel channel;
    final HeadContext head;
    final TailContext tail;

    private ChannelHandlerInvoker invoker;
    private final Map<String, AbstractChannelHandlerContext> name2ctx =
            new HashMap<String, AbstractChannelHandlerContext>(4);

    public ChannelPipeline(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;

        invoker = new ChannelHandlerInvoker();
        tail = new TailContext(this, invoker);
        head = new HeadContext(this, invoker);

        head.next = tail;
        tail.prev = head;
    }

    public void destroy() {
        invoker.destroy();
    }

    public Channel channel() {
        return channel;
    }

    public ChannelPipeline addLast(String name, ChannelHandler handler) {
        synchronized (this) {
            addLast0(name, new DefaultChannelHandlerContext(name, this, invoker, handler));
        }

        return this;
    }

    private void addLast0(final String name, AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;

        name2ctx.put(name, newCtx);
    }

    public void remove(String name) {
        AbstractChannelHandlerContext node = name2ctx.get(name);
        if (node == null) {
            return;
        }

        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;

        name2ctx.remove(name);
    }

    public void active() {
        head.fireActive();
    }

    public void inactive() {
        head.fireInactive();
    }

    public void read(Object msg) {
        head.fireRead(msg);
    }

    public void write(Object msg) {
        tail.fireWrite(msg);
    }

    /**Notes: user should not call this function*/
    public void writeNext() {
        invoker.invokeRunnable(new Runnable() {
            @Override
            public void run() {
                printLog("writeNext()");
                head.writeNext();
            }
        });
    }

    public void exceptionCaught(Throwable throwable) {
        head.fireExceptionCaught(throwable);
    }

    static final class TailContext extends AbstractChannelHandlerContext implements ChannelHandler {
        private static final String TAIL_NAME = TailContext.class.getName();
        private static final int SKIP_FLAGS = skipFlags0(TailContext.class);

        public TailContext(ChannelPipeline pipeline, ChannelHandlerInvoker invoker) {
            super(TAIL_NAME, pipeline, invoker, SKIP_FLAGS);
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public void read(AbstractChannelHandlerContext ctx, Object msg) throws Exception {
            printLog("Discarded inbound message \"" + msg + "\" that reached at the tail of the pipeline. " +
                            "Please check your pipeline configuration.");
        }

        @Skip
        @Override
        public void active(AbstractChannelHandlerContext ctx) throws Exception {}

        @Skip
        @Override
        public void inactive(AbstractChannelHandlerContext ctx) throws Exception {}

        @Skip
        @Override
        public void write(AbstractChannelHandlerContext ctx, Object msg) throws Exception {}

        @Override
        public void exceptionCaught(AbstractChannelHandlerContext ctx, Throwable cause) throws Exception {
            String logMsg = "\"An exceptionCaught() event was fired, and it reached at the tail of the pipeline. \" +\n" +
                                "\"It usually means the last handler in the pipeline did not handle the exception.\"";

            printLog(logMsg);
            LogUtil.e(TAIL_NAME, logMsg, cause);
        }
    }

    static final class HeadContext extends AbstractChannelHandlerContext implements ChannelHandler {
        private static final String HEAD_NAME = HeadContext.class.getName();
        private static final int SKIP_FLAGS = skipFlags0(HeadContext.class);
        private static final int DATA_CHUNK = 20;
        private static final int MAX_WRITE_DATA_LENGTH = 4 * 1024;
        private static final int WRITE_TIMEOUT = 1 * 1000;

        private Channel mChannel;
        private DataSplitUtil mSpliteUtil;
        private final LinkedList<byte[]> mListDataToSending;
        private volatile boolean mIsDataSending = false;
        private Handler mHandler;

        public HeadContext(ChannelPipeline pipeline, ChannelHandlerInvoker invoker) {
            super(HEAD_NAME, pipeline, invoker, SKIP_FLAGS);

            mChannel = pipeline.channel;
            mSpliteUtil = new DataSplitUtil(DATA_CHUNK);
            mListDataToSending = new LinkedList<>();
            mIsDataSending = false;
            mHandler = new Handler();
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public synchronized void write(AbstractChannelHandlerContext ctx, Object msg) throws Exception {
            byte[] bytes = (byte[]) msg;
            if (bytes.length > MAX_WRITE_DATA_LENGTH) {
                throw new RuntimeException("write data length exceed " + MAX_WRITE_DATA_LENGTH);
            }

            mListDataToSending.add(bytes);
            writeAsync();

            printLog("write size=" + bytes.length + ", bytes=" + ByteUtil.byte2HexString(bytes));
        }

        public void writeAsync() {
            if (!mIsDataSending) {
                writeNext();
            }
        }

        public void writeNext() {
            mIsDataSending = true;
            if (writeChannelChunk()) {
                return;
            }

            if (!mListDataToSending.isEmpty()) {
                mSpliteUtil.setData(mListDataToSending.pop());
                if (writeChannelChunk()) {
                    return;
                }
            }

            mIsDataSending = false;
            mHandler.removeCallbacks(mWriteTimeoutRunnable);
        }

        private boolean writeChannelChunk() {
            byte[] chuck = mSpliteUtil.getDataChunk();
            if (chuck != null) {
                mChannel.writeChannel(chuck);

                mHandler.removeCallbacks(mWriteTimeoutRunnable);
                mHandler.postDelayed(mWriteTimeoutRunnable, WRITE_TIMEOUT);
                return true;
            }

            return false;
        }

        private Runnable mWriteTimeoutRunnable = new Runnable() {

            @Override
            public void run() {
                String logMsg = "write channel timeout, maybe channel already closed.";

                printLog(logMsg);
                LogUtil.e(TAG, logMsg);

                synchronized (HeadContext.this) {
                    mIsDataSending = false;
                    mSpliteUtil.setData(null);
                    mListDataToSending.clear();
                }
            }
        };

        @Skip
        @Override
        public void active(AbstractChannelHandlerContext ctx) throws Exception {}

        @Skip
        @Override
        public void inactive(AbstractChannelHandlerContext ctx) throws Exception {}

        @Skip
        @Override
        public void read(AbstractChannelHandlerContext ctx, Object msg) throws Exception {}

        @Skip
        @Override
        public void exceptionCaught(AbstractChannelHandlerContext ctx, Throwable cause) throws Exception {}
    }

    private static void printLog(String message) {
        if (DEBUG) {
            LogUtil.d(TAG, message);
        }
    }
}
