package com.wenzejin;

import com.wenzejin.core.Core;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.sql.SQLException;


public class Main {

    public static void main(String[] args) {

        Options options = new Options();
        options.addOption("g", "gui", false, "Run in GUI mode");
        options.addOption("c","config",true,"Specify the configuration file");
        // Parse the command line arguments

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        String configPath = "./config.yml";
        boolean gui = false;
        boolean fullTest = true;

        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("g")) {
                System.out.println("GUI is not implemented yet.");
                gui = true;
            }
            if (cmd.hasOption("c")) {
                configPath = cmd.getOptionValue("c");
            }
        } catch (ParseException e) {
            System.out.println("Error parsing command line: " + e.getMessage());
        }

        if (!gui && fullTest) {
            Core core = new Core();
            try {
                core.loadSettingsFromYaml(configPath);
                core.execute();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }
}