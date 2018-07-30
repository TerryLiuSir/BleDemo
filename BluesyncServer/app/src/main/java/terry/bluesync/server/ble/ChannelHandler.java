package terry.bluesync.server.ble;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface ChannelHandler {
    @Skip
    void active(AbstractChannelHandlerContext ctx) throws Exception;

    @Skip
    void inactive(AbstractChannelHandlerContext ctx) throws Exception;

    @Skip
    void descriptorWrite(AbstractChannelHandlerContext ctx) throws Exception;

    @Skip
    void read(AbstractChannelHandlerContext ctx, Object msg) throws Exception;

    @Skip
    void write(AbstractChannelHandlerContext ctx, Object msg) throws Exception;

    @Skip
    void disconnect(AbstractChannelHandlerContext ctx) throws Exception;

    @Skip
    void exceptionCaught(AbstractChannelHandlerContext ctx, Throwable cause) throws Exception;

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Skip {
        // no value
    }
}
