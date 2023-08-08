package io.micrc.core._camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class EchoProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange);
    }
}
