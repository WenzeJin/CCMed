package com.wenzejin.core.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class InfuseWorkerTest {

    InfuseWorker worker;

    @BeforeEach
    void initializeWorker() {
        worker = new InfuseWorker("workspace",
                "cc_utils/INFUSE_ORI/INFUSE.jar",
                "cc_utils/INFUSE_ORI/Bfunction.class");
    }

    @Test
    void testInitWorkspace() {
        try {
            worker.initWorkspace();
            worker.destroyWorkspace();
        } catch (IOException e) {
            fail("Init Workspace Failed.");
        }

    }

}