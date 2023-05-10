package com.demo.fixture.command;

import org.springframework.stereotype.Component;

public interface CommandAdapter {
    Object adapt(Object obj);

    @Component("CommandAdapter")
    public static class CommandAdapterImpl implements CommandAdapter {

        @Override
        public Object adapt(Object obj) {
            // TODO Auto-generated method stub
            return null;
        }

    }
}
