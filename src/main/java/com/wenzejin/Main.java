package com.wenzejin;

import com.wenzejin.core.TesterCore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);


    public static void main(String[] args) {

        TesterCore tester = new TesterCore();

        logger.info("Tester Initialized.");
    }


}