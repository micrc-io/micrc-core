package io.micrc.core.integration.command.businesses;

public interface ApplicationCommandAdapter<T> {

    T adapt(String command);
}
