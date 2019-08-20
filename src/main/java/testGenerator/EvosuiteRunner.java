package testGenerator;

import daikon.PptMap;
import daikon.PptTopLevel;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import predicatesGenerator.DaikonRunner;
import utils.MavenProjectUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static utils.CommandExecutors.executeMaven;

public class EvosuiteRunner extends MavenProjectUtils {

    private static Logger logger = Logger.getLogger(EvosuiteRunner.class.getSimpleName());

    public EvosuiteRunner(String baseDir) throws IOException, XmlPullParserException {
        super(baseDir);

    }

    public boolean generateBaseTests() {

        try {
            addDependency("junit", "junit", "4.12");

            Plugin pluginEvosuite=addPlugin("org.evosuite.plugins",
                    "evosuite-maven-plugin", "1.0.6");

            PluginExecution pluginExecution = new PluginExecution();
            final Xpp3Dom configuration1 = new Xpp3Dom("configuration");
            final Xpp3Dom timeout = new Xpp3Dom("global_timeout");
            timeout.setValue("30");
            configuration1.addChild(timeout);
            pluginExecution.setConfiguration(configuration1);

            addPluginExecution(pluginEvosuite, pluginExecution);

            PluginExecution pluginExecution1 = new PluginExecution();
            pluginExecution1.setPhase("generate-test-sources");

            List<String> goals = new ArrayList<String>() {{
                add("add-test-source");
            }};

            pluginExecution1.setGoals(goals);

            final Xpp3Dom configuration = new Xpp3Dom("configuration");
            final Xpp3Dom quiet = new Xpp3Dom("sources");
            Xpp3Dom source = new Xpp3Dom("source");
            quiet.addChild(source);
            source.setValue(".evosuite/best-tests/selected");
            configuration.addChild(quiet);

            pluginExecution1.setConfiguration(configuration);

            Plugin plugin = addPlugin("org.codehaus.mojo", "build-helper-maven-plugin", "1.4");
            addPluginExecution(plugin, pluginExecution1);
        } catch (IOException | XmlPullParserException e) {
            logger.severe(e.getMessage());
            return false;
        }

        boolean result;

        result = executeMaven(projectDirs.get("baseDir"),
                new String[]{"evosuite:generate"});

        if (!result) {
            logger.severe("evosuite:generate failed");
            return result;
        }

        return result;
    }


    public static boolean generateAdoptedTests(String pathToPomFolder) {

        EvosuiteRunner evosuiteRunner = null;
        try {
            evosuiteRunner = new EvosuiteRunner(pathToPomFolder);
        } catch (IOException | XmlPullParserException e) {
            logger.severe(e.getMessage());
            return false;
        }

        boolean result = evosuiteRunner.generateBaseTests();

        if (!result)
            return result;

        SimpleTestsDownGrader simpleTestsDownGrader = null;
        try {
            simpleTestsDownGrader = new SimpleTestsDownGrader(evosuiteRunner.projectDirs.get("baseDir"));
        } catch (IOException | XmlPullParserException e) {
            logger.severe(e.getMessage());
            return false;                                   //TODO: aspects
        }

        return simpleTestsDownGrader.createExecutableTestSuite();
    }


    public static void main(String[] args) {

        String baseDir = "/home/suntrie/IdeaProjects/jackson-example/jackson-example";

        generateAdoptedTests(baseDir);

        /*try {
            PptMap pptMap = new DaikonRunner(baseDir).generateInvariantsPptMap("com.fasterxml.jackson.core:jackson-databind:2.2.3");

            for (PptTopLevel ppt : pptMap.all_ppts()) {
                if (ppt.getInvariants().size() != 0)
                    System.out.print("");
            }

            System.out.println("");
        } catch (IOException | XmlPullParserException e) {
            logger.severe(e.getMessage());
            //TODO: ret val
        }*/
    }

}
