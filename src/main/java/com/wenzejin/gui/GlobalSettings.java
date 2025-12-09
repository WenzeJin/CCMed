package com.wenzejin.gui;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class GlobalSettings {
    public String workspaceRoot = "./workspace";

    private static final String SETTINGS_FILE = "settings.json";

    public static GlobalSettings load() {
        ObjectMapper mapper = new ObjectMapper();
        File file = new File(SETTINGS_FILE);
        if (file.exists()) {
            try {
                return mapper.readValue(file, GlobalSettings.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new GlobalSettings();
    }

    public void save() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(SETTINGS_FILE), this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
