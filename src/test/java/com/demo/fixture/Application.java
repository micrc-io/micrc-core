package com.demo.fixture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.micrc.core.EnableMicrcSupport;
import io.micrc.core.MicrcApplication;

@SpringBootApplication
@EnableMicrcSupport
public class Application {
    public static void main(String[] args) {
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        // argList.add("local");
        MicrcApplication.run(Application.class, argList.toArray(String[]::new));
    }
}
