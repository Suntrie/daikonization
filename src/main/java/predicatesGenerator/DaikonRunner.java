package predicatesGenerator;

import com.repoMiner.AetherTreeConstructor;
import daikon.DaikonSimple;
import daikon.FileIO;
import daikon.PptMap;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import utils.ProjectRunner;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static daikon.Daikon.setup_proto_invs;
import static java.lang.System.out;
import static utils.CommandExecutors.*;

public class DaikonRunner extends ProjectRunner {

    public DaikonRunner(String baseDir) throws IOException, XmlPullParserException {
        super(baseDir);
    }

    public static final String pathToDaikonInvariantsConf = "config/Invariants_conf";

    private Optional<Path> generateDataForExploration(String libraryCoords) throws IOException {

        AetherTreeConstructor aetherTreeConstructor = new AetherTreeConstructor
                ("\\home\\suntrie\\.m2\\repository");   //TODO: config

        Set<Method> methodSet = new HashSet<>();

        Set<String> filters = new HashSet<>();
        filters.add("java.lang");

        Optional<Set<Class>> libraryClasses = aetherTreeConstructor.getPackageClasses(
                libraryCoords, filters);

        if (!libraryClasses.isPresent())
            return Optional.empty();


        Path dTracePath = null, mainClassPath;

        Set<Path> drivers = fileSearch(".*Driver.*\\.class", projectDirs.get("testOutputDirectory"));

        if (!drivers.iterator().hasNext()) {
            return Optional.ofNullable(dTracePath);
        } else {
            mainClassPath = drivers.iterator().next();
        }

        String mainClassName = stripExtension(String.valueOf(projectDirs.get("testOutputDirectory").toAbsolutePath()
                .relativize(mainClassPath))).replace('/', '.');

        //to get comparability sets

        //TODO: restrict test generation
        //TODO: filter out target classnames

        String comparabilityFileName = "comparability_set.decls-DynComp";
        String traceFileName = "chicory_trace.dtrace.gz";

        String pathToClassDirectory = projectDirs.get("testOutputDirectory").toAbsolutePath().toString();

        String fullProjectClassPath = getFullClassPathForProjectExploration();

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
            return Optional.ofNullable(dTracePath);
        }

        cmd.clear();
        cmd.add("java");
        cmd.add("-classpath");
        cmd.add(fullProjectClassPath);
        cmd.add("daikon.Chicory");
        cmd.add("--comparability-file");
        cmd.add(projectDirs.get("testOutputDirectory") + "/" + comparabilityFileName);
        cmd.add("--output-dir");
        cmd.add(pathToClassDirectory);

        cmd.addAll(pptSelectPatterns);

        cmd.add("--dtrace-file");
        cmd.add(traceFileName);
        cmd.add(mainClassName);
        cmd.add("--trace_line_depth=5");


        if (!executeTerminal(String.join(" ", cmd))) {
            return Optional.ofNullable(dTracePath);
        }

        dTracePath = Paths.get(pathToClassDirectory, traceFileName);

        return Optional.ofNullable(dTracePath);
    }

    private PptMap getInvariantsPptMap(Path dataTraceFullPath) {

        setup_proto_invs();

        BufferedReader objReader = null;
        try {
            objReader = new BufferedReader(new FileReader(pathToDaikonInvariantsConf));
        } catch (FileNotFoundException e) {
            out.println(e.getMessage()); //TODO
            return null;
        }

        String configLine;

        int i = 0;
        PptMap ppts = new PptMap();

        try {

           /* while ((configLine = objReader.readLine()) != null) {
                daikon.config.Configuration.getInstance().apply(configLine);
            }*/

            FileIO.read_data_trace_files(Arrays.asList(String.valueOf(dataTraceFullPath)), ppts,
                    new DaikonSimple.SimpleProcessor(), false);

        } catch (IOException e) {
            out.println(e.getMessage()); //TODO
            return null;
        }

        return ppts;
    }

    public PptMap generateInvariantsPptMap(String libraryCoords) throws IOException, XmlPullParserException {

        Optional<Path> pathToDataTraceFile = this.generateDataForExploration(libraryCoords);

        if (!pathToDataTraceFile.isPresent())
            return null;

        return this.getInvariantsPptMap(pathToDataTraceFile.get());
    }

    ;

}
