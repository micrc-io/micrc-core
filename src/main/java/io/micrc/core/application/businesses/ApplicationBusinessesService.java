package io.micrc.core.application.businesses;

public interface ApplicationBusinessesService<T> {
    void exec(T command);
}
