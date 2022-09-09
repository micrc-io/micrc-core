package io.micrc.core.application.businesses;

/**
 * 应用业务服务父接口，所有需要被支撑库支持的应用业务服务，都必须实现这个接口
 *
 * @author weiguan
 * @since 0.0.1
 * @date 2022-08-23 21:02
 */
public interface ApplicationBusinessesService<T> {
    void execute(T command);
}
