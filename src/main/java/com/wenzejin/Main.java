package com.wenzejin;

import com.wenzejin.core.TestCaseDataBaseDriver;
import com.wenzejin.core.TesterCore;
import com.wenzejin.core.mr.assertion.TestAssertionRegistry;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.sql.SQLException;


public class Main {

    public static void main(String[] args) {
        TestAssertionRegistry.register();

        Options options = new Options();
        options.addOption("g", "gui", false, "Run in GUI mode");
        options.addOption("c","config",true,"Specify the configuration file");
        options.addOption("l", "look", true, "Show specified case.");
        // Parse the command line arguments

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        String configPath = "./config.yml";
        int caseId = -1;
        boolean gui = false;
        boolean fullTest = true;
        boolean look = false;

        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("g")) {
                System.out.println("GUI is not implemented yet.");
                gui = true;
            }
            if (cmd.hasOption("c")) {
                configPath = cmd.getOptionValue("c");
            }
            if (cmd.hasOption("l")) {
                caseId = Integer.parseInt(cmd.getOptionValue("l"));
                fullTest = false;
                look = true;
            }
        } catch (ParseException e) {
            System.out.println("Error parsing command line: " + e.getMessage());
        }

        if (!gui && fullTest) {
            TesterCore testerCore = new TesterCore();
            try {
                testerCore.loadSettingsFromYaml(configPath);
                testerCore.execute();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (look) {
            try {
                System.out.println(TestCaseDataBaseDriver.getTestCaseById(caseId));
            } catch (SQLException e) {
                System.out.println("SQL Exception Occurred");
            } catch (IOException e) {
                System.out.println("IO Exception Occurred");
            }
        }

    }
}