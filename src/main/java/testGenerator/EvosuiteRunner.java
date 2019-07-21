package testGenerator;

import daikon.PptMap;
import daikon.PptTopLevel;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import predicatesGenerator.DaikonRunner;
import utils.MavenProjectUtils;

import java.io.File;
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

            addPlugin("org.evosuite.plugins",
                    "evosuite-maven-plugin", "1.0.6",  null
            );

            PluginExecution pluginExecution1= new PluginExecution();
            pluginExecution1.setPhase("generate-test-sources");

            List<String> goals = new ArrayList(){{add("add-test-source");}};

            pluginExecution1.setGoals(goals);


/*            <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>build-helper-maven-plugin</artifactId>
        <version>1.8</version>
        <executions>
          <execution>
            <id>add-source</id>
            <phase>generate-sources</phase>
            <goals><goal>add-test-source</goal></goals>
            <configuration><sources><source>.evosuite/best-tests</source></sources></configuration>
          </execution>
        </executions>
      </plugin>
            */
            final Xpp3Dom configuration = new Xpp3Dom("configuration");
            final Xpp3Dom quiet = new Xpp3Dom("sources");
            Xpp3Dom source = new Xpp3Dom("source");
            quiet.addChild(source);
            source.setValue(".evosuite/best-tests");
            configuration.addChild(quiet);

            pluginExecution1.setConfiguration(configuration);

            addPlugin("org.codehaus.mojo", "build-helper-maven-plugin", "1.4", pluginExecution1);
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


    public static boolean generateAdoptedTests(String pathToPomFolder){

        EvosuiteRunner evosuiteRunner = null;
        try {
            evosuiteRunner = new EvosuiteRunner(pathToPomFolder);
        } catch (IOException | XmlPullParserException e) {
            logger.severe(e.getMessage());
            return false;
        }

        boolean result = evosuiteRunner.generateBaseTests();

        if(!result)
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


    public static void main(String[] args) throws IOException, XmlPullParserException,
            DependencyCollectionException, ArtifactDescriptorException, ArtifactResolutionException {

        String baseDir = "/home/suntrie/IdeaProjects/jackson-example/jackson-example";

        generateAdoptedTests(baseDir);

        /*
        *	<groupId>com.fasterxml.jackson.core</groupId>
			<artifactId>jackson-databind</artifactId>
			<version>2.2.3</version>
        * */

        PptMap pptMap = new DaikonRunner(baseDir).generateInvariantsPptMap("com.fasterxml.jackson.core:jackson-databind:2.2.3");

        for (PptTopLevel ppt : pptMap.all_ppts()) {
            if (ppt.getInvariants().size() != 0)
                System.out.print("");
        }

        System.out.println("");
    }

}
