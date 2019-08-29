package predicatesGenerator;

import com.repoMiner.AetherTreeConstructor;
import daikon.FileIO;
import daikon.PptMap;
import daikon.PptTopLevel;
import utils.MavenProjectRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static utils.CommandExecutors.*;

public class DaikonRunner {

    private MavenProjectRunner projectRunner;

    public DaikonRunner(MavenProjectRunner projectRunner) {
        this.projectRunner = projectRunner;
    }

    public static final String pathToDaikonInvariantsConf = "config/Invariants_conf";

    private Optional<Path> generateDataForExploration(String libraryCoords) throws IOException {

        AetherTreeConstructor aetherTreeConstructor = new AetherTreeConstructor
                ("\\home\\suntrie\\.m2\\repository");   //TODO: config

        Set<String> filters = new HashSet<>();
        filters.add("java.lang");

        Optional<Set<Class>> libraryClasses = aetherTreeConstructor.getPackageClasses(
                libraryCoords, filters);

        if (!libraryClasses.isPresent())
            return Optional.empty();


        Path dTracePath, mainClassPath;

        Set<Path> drivers = fileSearch(".*Driver.*\\.class", projectRunner.getProjectDirs().get("testOutputDirectory"));

        if (!drivers.iterator().hasNext()) {
            return Optional.empty();
        } else {
            mainClassPath = drivers.iterator().next();
        }

        String mainClassName = stripExtension(String.valueOf(projectRunner.getProjectDirs().get("testOutputDirectory").toAbsolutePath()
                .relativize(mainClassPath))).replace('/', '.');

        //to get comparability sets

        //TODO: restrict test generation
        //TODO: filter out target classnames

        String comparabilityFileName = "comparability_set.decls-DynComp";
        String traceFileName = "chicory_trace.dtrace.gz";

        String pathToClassDirectory = projectRunner.getProjectDirs().get("testOutputDirectory").toAbsolutePath().toString();

        String fullProjectClassPath = projectRunner.getFullClassPathForProjectExploration();

        List<String> cmd = new LinkedList<String>();
        cmd.add("java");
        cmd.add("-classpath");
        cmd.add(fullProjectClassPath);
        cmd.add("daikon.DynComp");
        cmd.add(mainClassName);
        cmd.add("--output-dir");
        cmd.add(pathToClassDirectory);
        cmd.add("--decl-file");
        cmd.add(comparabilityFileName);
        cmd.add("--std-visibility");


        List<String> pptSelectPatterns = new ArrayList<String>() {{
            for (Class aClass : libraryClasses.get()) {
                add("--ppt_select_pattern=" + aClass.getName());
            }
        }};

        cmd.addAll(pptSelectPatterns);

        if (!executeTerminal(String.join(" ", cmd))) {
            return Optional.empty();
        }

        cmd.clear();
        cmd.add("java");
        cmd.add("-classpath");
        cmd.add(fullProjectClassPath);
        cmd.add("daikon.Chicory");
        cmd.add("--comparability-file");
        cmd.add(projectRunner.getProjectDirs().get("testOutputDirectory") + "/" + comparabilityFileName);
        cmd.add("--output-dir");
        cmd.add(pathToClassDirectory);

        cmd.addAll(pptSelectPatterns);

        cmd.add("--dtrace-file");
        cmd.add(traceFileName);
        cmd.add(mainClassName);
        cmd.add("--trace_line_depth=5");


        if (!executeTerminal(String.join(" ", cmd))) {
            return Optional.empty();
        }

        dTracePath = Paths.get(pathToClassDirectory, traceFileName);

        return Optional.of(dTracePath);
    }

    private boolean getInvariantsPptMap(Path dataTraceFullPath, Path pptMapFile) {

        List<String> cmd = new LinkedList<String>();
        cmd.add("java");
        cmd.add("-classpath");
        cmd.add(MavenProjectRunner.daikonPath);
        cmd.add("daikon.Daikon");
        cmd.add(String.valueOf(dataTraceFullPath));

        cmd.add("--omit_from_output");
        cmd.add("0rs");
        cmd.add("--disable-all-invariants");

        cmd.add("--config");
        cmd.add(pathToDaikonInvariantsConf);

        cmd.add("-o");
        cmd.add(String.valueOf(pptMapFile));

        return executeTerminal(String.join(" ", cmd));
    }

    public Optional<PptMap> generateInvariantsPptMap(String libraryCoords, Path invariantsFilePath) throws IOException {

        Optional<Path> pathToDataTraceFile = this.generateDataForExploration(libraryCoords);

        if (!pathToDataTraceFile.isPresent() || !this.getInvariantsPptMap(pathToDataTraceFile.get(), invariantsFilePath))
            return Optional.empty();

        PptMap pptMap = new PptMap();

        pptMap=FileIO.read_serialized_pptmap(new File(String.valueOf(invariantsFilePath)), true);

        for (PptTopLevel ppt : pptMap.all_ppts()) {
            if (ppt.getInvariants().size() != 0)
                System.out.print("");
        }

        return Optional.of(pptMap);
    }

}
