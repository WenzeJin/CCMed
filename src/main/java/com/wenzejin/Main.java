package com.wenzejin;

import com.wenzejin.core.Config;
import com.wenzejin.core.Core;
import org.apache.commons.cli.*;

import java.io.IOException;


public class Main {

    public static void main(String[] args) {

        Options options = new Options();
        options.addOption("c","config",true,"Specify the configuration file");
        options.addOption("d", "detail-data", false,
            "Enable follow-up detail data export (saved in job workspace)");
        // Parse the command line arguments

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        String configPath = "./config.json";
        boolean detailData = false;

        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("c")) {
                configPath = cmd.getOptionValue("c");
            }
            if (cmd.hasOption("detail-data")) {
                detailData = true;
            }
        } catch (ParseException e) {
            System.out.println("Error parsing command line: " + e.getMessage());
        }


        try {
            Config conf = Config.loadFromJson(configPath);
            if (detailData) {
                conf.setDetailData(true);
            }
            if (!conf.validate(true)) {
                System.exit(-1);
            }
            Core core = new Core(conf);
            core.execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}