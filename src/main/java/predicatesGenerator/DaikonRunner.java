package predicatesGenerator;

import com.repoMiner.AetherTreeConstructor;
import daikon.DaikonSimple;
import daikon.FileIO;
import daikon.PptMap;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import utils.MavenProjectUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static daikon.Daikon.setup_proto_invs;
import static java.lang.System.out;
import static utils.CommandExecutors.*;

public class DaikonRunner extends MavenProjectUtils {

    public DaikonRunner(String baseDir) throws IOException, XmlPullParserException {
        super(baseDir);
    }


    private String getFullClassPathForProjectExploration() throws IOException, XmlPullParserException {

        String propertiesFileName = "classpath.txt";

        String classpath = new String(Files.readAllBytes(Paths.get(String.valueOf(projectDirs.get("baseDir")), propertiesFileName)));

        String testsOutputDirectory = projectDirs.get("testOutputDirectory").toAbsolutePath().toString();
        String outputDirectory = projectDirs.get("outputDirectory").toAbsolutePath().toString();

        return classpath.concat(File.pathSeparator).
                concat(testsOutputDirectory).concat(File.pathSeparator).
                concat(outputDirectory).concat(File.pathSeparator).
                concat(daikonPath);
    }


    private Optional<Path> generateDataForExploration(String libraryCoords) throws IOException, XmlPullParserException
    {

        AetherTreeConstructor aetherTreeConstructor = new AetherTreeConstructor
                ("/home/suntrie/.m2/repository");   //TODO: config

        Set<Method> methodSet = new HashSet<>();

        Set<String> filters=new HashSet<>();
        filters.add("java.lang");

        Optional<Set<Class>> libraryClasses=aetherTreeConstructor.getPackageClasses(
                libraryCoords, filters);


        if (!libraryClasses.isPresent())
            return Optional.empty();


        Path dTracePath = null, mainClassPath;

        Set<Path> drivers = fileSearch(".*Driver.*\\.class", projectDirs.get("testOutputDirectory"));

        if (!drivers.iterator().hasNext()) {
            return Optional.ofNullable(dTracePath);
        }else{
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


        List<String> pptSelectPatterns = new ArrayList<String>(){{
            for (Class aClass: libraryClasses.get()){
             add("--ppt_select_pattern");
             add(aClass.getCanonicalName());
        }}};

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

        if (!executeTerminal(String.join(" ", cmd))) {
            return Optional.ofNullable(dTracePath);
        }

        dTracePath = Paths.get(pathToClassDirectory, traceFileName);

        return Optional.ofNullable(dTracePath);
    }

    private PptMap getInvariantsPptMap(Path dataTraceFullPath) {

        setup_proto_invs();

        PptMap ppts = new PptMap();

        try {
            FileIO.read_data_trace_files(Arrays.asList(String.valueOf(dataTraceFullPath)), ppts,
                    new DaikonSimple.SimpleProcessor(), false);
        } catch (Exception e) {
            out.println(e.getMessage());
            return null;
        }

        return ppts;
    }

    public PptMap generateInvariantsPptMap(String libraryCoords) throws IOException, XmlPullParserException {

        PluginExecution pluginExecution = new PluginExecution();
        pluginExecution.addGoal("build-classpath");
        pluginExecution.setPhase("generate-sources");

        String propertiesFileName = "classpath.txt";

        final Xpp3Dom configuration = new Xpp3Dom("configuration");
        final Xpp3Dom quiet = new Xpp3Dom("outputFile");
        quiet.setValue(propertiesFileName);
        configuration.addChild(quiet);

        pluginExecution.setConfiguration(configuration);

        Plugin plugin = addPlugin("org.apache.maven.plugins",
                "maven-dependency-plugin","2.9");

        addPluginExecution(plugin, pluginExecution);

        executeMaven(projectDirs.get("baseDir"),
                new String[]{ "test-compile","--fail-never"}); //later, than just 'compile'

        Optional<Path> pathToDataTraceFile = this.generateDataForExploration(libraryCoords);

        if (!pathToDataTraceFile.isPresent())
            return null;

        return this.getInvariantsPptMap(pathToDataTraceFile.get());
    };

}
