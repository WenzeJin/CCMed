package com.wenzejin.core.worker;

import com.wenzejin.core.cases.TestCase;
import com.wenzejin.core.result.Link;
import com.wenzejin.core.result.Result;
import com.wenzejin.core.result.VAPair;
import com.wenzejin.core.rule.Bfunc;
import com.wenzejin.core.rule.Pattern;
import com.wenzejin.core.rule.Rule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.json.JSONArray;

public class InfuseWorker implements TestWorker {

    private static final Logger logger = LogManager.getLogger("InfuseWorker");

    // Configurable Arguments
    private final String workspaceRoot;
    private final String jarTargetPath;
    private final String bfuncClassPath;
    private final List<String> arguments;

    // UUID
    private final UUID uuid;

    private Path workspace;
    private Path copiedJar;
    private Path copiedBfuncClass;

    /**
     * The InfuseWorker class is responsible for initializing and managing the workspace
     * and configuration required for the infusion process. It extracts and validates
     * parameters from the provided arguments map and assigns them to the appropriate
     * fields. Additionally, it generates a unique identifier (UUID) for each instance.
     *
     * @param args A map containing the following keys:
     *             - "infuse.workspace-root": The root directory of the workspace (String).
     *             - "infuse.jar-target-path": The target path for the JAR file (String).
     *             - "infuse.bfunc-class-path": The class path for the bfunc (String).
     *             - "infuse.arguments": A list of arguments (List<String>).
     *
     * Fields initialized:
     * - workspaceRoot: The root directory of the workspace.
     * - jarTargetPath: The target path for the JAR file.
     * - bfuncClassPath: The class path for the bfunc.
     * - arguments: A list of arguments, filtered and cast to strings.
     * - uuid: A unique identifier for the instance.
     */
    public InfuseWorker(Map<String, Object> args) {
        workspaceRoot = (String) args.get("infuse.workspace-root");
        jarTargetPath = (String) args.get("infuse.jar-target-path");
        bfuncClassPath = (String) args.get("infuse.bfunc-class-path");
        Object argumentsObj = args.get("infuse.arguments");

        if (argumentsObj instanceof List<?>) {
            arguments = ((List<?>) argumentsObj).stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        } else {
            arguments = new ArrayList<>();
        }

        uuid = UUID.randomUUID();
    }


    /**
     * Initialize temporary workspace for InfuseWorker
     * the workspace path will be "[workspaceRoot]/infuse_worker_[UUID]"
     * @throws IOException if initialize failed.
     */
    @Override
    public void initWorkspace() throws IOException {
        String workspacePath = getWorkspacePath();
        workspace = Paths.get(workspacePath);

        // Create workspace directory if it doesn't exist
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            logger.fatal("Failed to create workspace directory: {}", workspacePath, e);
            throw new IOException("Failed to create workspace directory: " + workspacePath, e);
        }

        // Copy jar file to workspace
        copiedJar = copyFileToWorkspace(jarTargetPath, workspace, "Jar target file");

        // Copy bfunc class file to workspace
        copiedBfuncClass = copyFileToWorkspace(bfuncClassPath, workspace, "Bfunc class file");
    }

    /**
     * Copies a file from the specified source path to the given workspace directory.
     * If the file already exists in the workspace, it will be replaced.
     *
     * @param sourcePath      The path of the source file to be copied.
     * @param workspace       The target workspace directory where the file will be copied.
     * @param fileDescription A description of the file being copied, used for logging purposes.
     * @return The path of the copied file in the workspace.
     * @throws IOException           If an I/O error occurs during the file copy operation.
     * @throws FileNotFoundException If the source file does not exist at the specified path.
     */
    private Path copyFileToWorkspace(String sourcePath, Path workspace, String fileDescription) throws IOException {
        Path sourceFile = Paths.get(sourcePath);
        if (!Files.exists(sourceFile)) {
            logger.fatal("{} not found at: {}", fileDescription, sourcePath);
            throw new FileNotFoundException(fileDescription + " not found at: " + sourcePath);
        }

        Path destinationFile = workspace.resolve(sourceFile.getFileName());
        try {
            Files.copy(sourceFile, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.fatal("Failed to copy {} to workspace: {}", fileDescription, destinationFile, e);
            throw new IOException("Failed to copy " + fileDescription + " to workspace: " + destinationFile, e);
        }

        return destinationFile;
    }

    /**
     * Destroy temporary workspace.
     * @throws IOException if destroy failed.
     */
    @Override
    public void destroyWorkspace() throws IOException {
        if (workspace == null) {
            logger.warn("Workspace not initialized yet but trying to destroy it.");
            return;
        }

        if (Files.exists(workspace)) {
            try {
                // Recursively delete the workspace directory and its contents
                Files.walk(workspace)
                        .sorted(Comparator.reverseOrder()) // Ensure directories are deleted after their contents
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                                logger.debug("Deleted: {}", path);
                            } catch (IOException e) {
                                logger.error("Failed to delete: {} due to: {}", path, e.getMessage());
                            }
                        });
            } catch (IOException e) {
                logger.error("Failed to clean up workspace: {}", workspace, e);
                throw new IOException("Failed to clean up workspace: " + workspace, e);
            }
        } else {
            logger.info("Workspace does not exist: {}", workspace);
        }

        workspace = null;
    }

    /**
     * Run a single test case.
     * @param tc TestCase to run
     *           The result will be stored in the TestCase object.
     * @return 0 if success, 1 if failed.
     */
    @Override
    public int singleRun(TestCase tc) {
        int error = 0;
        List<Rule> rules = List.of(tc.getRule());
        Set<Pattern> patterns = new HashSet<>(tc.getPatterns());
        List<Bfunc> bfuncs = tc.getBfuncs();

        try {
            generateRuleInput(rules);
            generateBfuncInput(bfuncs);
            generatePatternAndContextInput(patterns);
            int result = execute();
            if (result != 0) {
                return result;
            }
            analyzeSingleResult(tc);
        } catch (IOException e) {
            logger.warn("IOException occurred when running test case: {}", tc.getId());
            error = 1;
        }
        return error;
    }

    private int execute() {
        Path absoluteJar = copiedJar.toAbsolutePath();
        Path absoluteBfunc = copiedBfuncClass.toAbsolutePath();
        Path contextPool = workspace.resolve("context_pool.json").toAbsolutePath();
        Path ruleInput = workspace.resolve("rule.xml").toAbsolutePath();
        Path outputIncs = workspace.resolve("incs.json").toAbsolutePath();
        Path outputCCT = workspace.resolve("cct.txt").toAbsolutePath();
        Path logPath = workspace.resolve("infuse_log.txt").toAbsolutePath();
        Path errPath = workspace.resolve("infuse_err.txt").toAbsolutePath();

        try {
            Files.deleteIfExists(outputIncs);
        } catch (IOException e) {
            logger.error("Failed to delete existing output file: {}", outputIncs, e);
            return 1;
        }

        List<String> args = new ArrayList<>(List.of(
                "java", "-jar", absoluteJar.toString(),
                "-test",
                "-rules", ruleInput.toString(),
                "-bfuncs", absoluteBfunc.toString(),
                "-contextpool", contextPool.toString(),
                "-incs", outputIncs.toString(),
                "-cct", outputCCT.toString()
        ));
        args.addAll(arguments);

        ProcessBuilder builder = new ProcessBuilder(args)
                .redirectOutput(logPath.toFile())
                .redirectError(errPath.toFile());

        try {
            Process process = builder.start();

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                logger.warn("Execution time limit exceeded. Terminating process.");
                process.destroy();
                return 2;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warn("Process exited with non-zero code: {}", exitCode);
                return 1;
            }
        } catch (IOException e) {
            logger.error("IOException occurred during process execution.", e);
            return 1;
        } catch (InterruptedException e) {
            logger.error("Process execution was interrupted.", e);
            return 1;
        }

        return 0;
    }

    private String getWorkspacePath() {
        return Paths.get(workspaceRoot, "infuse_worker_" + uuid.toString()).toString();
    }

    private void generatePatternAndContextInput(Set<Pattern> patterns) throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }

        Path contextPoolPath = Path.of(workspace.toString(), "context_pool.json");
        try (FileOutputStream fos = new FileOutputStream(contextPoolPath.toString())) {
            JSONArray contextPool = new JSONArray();
            int context_cnt = 0;
            for (Pattern pat: patterns) {
                String name = pat.getName();
                JSONArray contextArray = new JSONArray();
                for (String data: pat.getData()) {
                    context_cnt++;
                    JSONObject context = new JSONObject();
                    context.put("ctx_id", "" + context_cnt);
                    context.put("fields", new JSONObject().put("data", data));
                    contextArray.put(context);
                }
                contextPool.put(new JSONObject().put("pat_id", name).put("contexts", contextArray));
            }
            fos.write(contextPool.toString(2).getBytes());
        }
    }

    private void generateBfuncInput(List<Bfunc> bfuncs) throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }

        Path bfuncInputPath = Path.of(workspace.toString(), "bfunc_in.txt");
        try (FileOutputStream fos = new FileOutputStream(bfuncInputPath.toString())) {
            for (Bfunc bfunc: bfuncs) {
                String name = "#" + bfunc.getName() + "\n";
                StringBuilder vb = new StringBuilder("@");
                for (String param: bfunc.getParamList()) {
                    vb.append(param).append(",");
                }
                vb.append("\n");
                StringBuilder mb = new StringBuilder();
                for (String key: bfunc.getValueMap().keySet()) {
                    mb.append(key).append(" ").append(bfunc.getValueMap().get(key));
                    mb.append("\n");
                }
                fos.write((name + vb + mb).getBytes());
            }
        }

    }

    private void generateRuleInput(List<Rule> rules) throws IOException {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        Path ruleInputPath = Path.of(workspace.toString(), "rule.xml");
        try (FileOutputStream fos = new FileOutputStream(ruleInputPath.toString())) {
            fos.write("<?xml version=\"1.0\"?>\n<rules>\n".getBytes());
            for (Rule rule: rules) {
                fos.write(rule.toXmlString().getBytes());
            }
            fos.write("</rules>".getBytes());
        }
    }

    private void analyzeSingleResult(TestCase tc) throws IOException {

        Path incsPath = Path.of(workspace.toString(), "incs.json");

        try (BufferedReader reader = new BufferedReader(new FileReader(incsPath.toString()))) {
            // read lines
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            // parse json
            JSONObject incs = new JSONObject(sb.toString());
            Result result = null;
            for (String ruleId: incs.keySet()) {
                if (!ruleId.equals(tc.getRule().id)) {
                    continue;
                }
                JSONObject rule = incs.getJSONObject(ruleId);
                result = new Result(rule.getBoolean("truth"));

                // parse links
                Set<Link> links = new HashSet<>();
                for (Object linkObj: rule.getJSONArray("links")) {
                    JSONArray link = (JSONArray) linkObj;
                    Link.Type type = rule.getBoolean("truth") ? Link.Type.SATISFIED : Link.Type.VIOLATED;
                    Set<VAPair> vaSet = new HashSet<>();
                    for (Object vaObj: link) {
                        JSONObject va = (JSONObject) vaObj;
                        vaSet.add(new VAPair(va.getString("var"), va.getJSONObject("value").getJSONObject("fields").getString("data")));
                    }
                    links.add(new Link(type, vaSet));
                }
                result.links = links;
            }
            tc.setResult(result);
            tc.setChecked(true);
        }
    }
}
