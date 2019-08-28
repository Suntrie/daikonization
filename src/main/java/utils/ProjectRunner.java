package utils;

import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static utils.CommandExecutors.executeMaven;
import static utils.CommandExecutors.getPathThroughProps;

public class ProjectRunner {

    public static final String randoopPath = System.getenv("RANDOOP_JAR");
    public static final String daikonPath = System.getenv("DAIKONDIR") + "/daikon.jar";

    protected Map<String, Path> projectDirs = new HashMap<>();


    public Long getTimestamp() {
        return timestamp;
    }

    protected Long timestamp = System.currentTimeMillis();
    protected Long incCounter = timestamp;
    protected File pomFile;
    protected Model model;

    public ProjectRunner(String baseDir) throws IOException, XmlPullParserException {
        projectDirs.put("baseDir", Paths.get(baseDir));

        pomFile = new File(baseDir, "/pom.xml");
        MavenXpp3Reader reader = new MavenXpp3Reader();
        model = reader.read(new FileInputStream(pomFile));
        initializeTestsDir();
    }

    private void initializeTestsDir() throws IOException, XmlPullParserException {
        PluginExecution pluginExecution = new PluginExecution();
        pluginExecution.addGoal("write-project-properties");
        pluginExecution.setPhase("initialize");

        String propertiesFileName = "app.properties";

        final Xpp3Dom configuration = new Xpp3Dom("configuration");
        final Xpp3Dom quiet = new Xpp3Dom("outputFile");
        quiet.setValue(propertiesFileName);
        configuration.addChild(quiet);

        pluginExecution.setConfiguration(configuration);

        Plugin plugin = addPlugin("org.codehaus.mojo", "properties-maven-plugin", "1.0.0");  //TODO: probably, through parent hierarchy

        addPluginExecution(plugin, pluginExecution);

        addProperties(new HashMap<String, String>() {{
            put("outputDirectory", "${project.build.outputDirectory}");
            put("testOutputDirectory", "${project.build.testOutputDirectory}");
            put("testSourceDirectory", "${project.build.testSourceDirectory}");
        }});


        Path baseDir = projectDirs.get("baseDir");

        executeMaven(baseDir,
                new String[]{"initialize"});

        Map<String, Path> propsDirs = getPathThroughProps(Paths.get(String.valueOf(baseDir), propertiesFileName));
        projectDirs.putAll(propsDirs);
    }


    protected interface GeneratorFunction {
        public void generateMavenObject(Model model, String groupId, String artifactId, String version) throws IOException;
    }

    protected String getMavenCoords() {
        return model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion();
    }

    protected void generateAndSaveMavenObject(GeneratorFunction generatorFunction,
                                              String groupId, String artifactId, String version) throws IOException, XmlPullParserException {

        generatorFunction.generateMavenObject(model, groupId, artifactId, version);

        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new FileOutputStream(pomFile), model);

    }


    public void addDependency(String groupId, String artifactId, String version) throws IOException, XmlPullParserException {


        generateAndSaveMavenObject((model, groupId1, artifactId1, version1) -> {
            Dependency dependency = new Dependency();
            dependency.setGroupId(groupId1);
            dependency.setArtifactId(artifactId1);
            dependency.setVersion(version1);

            boolean previous = false;

            for (Dependency dependency1 : model.getDependencies()) {
                if (dependency1.getGroupId().equals(dependency.getGroupId()) &&
                        dependency1.getArtifactId().equals(dependency.getArtifactId())) {
                    dependency.setVersion(version1);
                    previous = true;
                    break;
                }
            }

            if (!previous)
                model.addDependency(dependency);

        }, groupId, artifactId, version);
    }


    protected Plugin addPlugin(String groupId, String artifactId, String version) throws IOException, XmlPullParserException {

        final Plugin[] result = {null};

        generateAndSaveMavenObject((model, groupId1, artifactId1, version1) -> {
            Plugin plugin = new Plugin();
            plugin.setGroupId(groupId1);
            plugin.setArtifactId(artifactId1);
            plugin.setVersion(version1);

            Build build = model.getBuild();

            if (build == null)
                model.setBuild(new Build());

            if (!model.getBuild().getPlugins().contains(plugin)) {
                model.getBuild().addPlugin(plugin);
                result[0] = plugin;
            } else {

                for (Plugin plugin1 : model.getBuild().getPlugins()) {
                    if (plugin1.equals(plugin)) {
                        result[0] = plugin;
                        return;
                    }
                }
            }


        }, groupId, artifactId, version);

        return result[0];
    }

    protected PluginExecution addPluginExecution(Plugin plugin, PluginExecution pluginExecution) throws IOException, XmlPullParserException {

        generateAndSaveMavenObject((model, groupId1, artifactId1, version1) -> {

            Plugin pomPlugin = null;

            Build build = model.getBuild();

            if (build == null)
                throw new IOException();

            if (!model.getBuild().getPlugins().contains(plugin)) {
                throw new IOException();
            } else {

                for (Plugin plugin1 : model.getBuild().getPlugins()) {
                    if (plugin1.equals(plugin)) {
                        pomPlugin = plugin1;
                    }
                }

                if (pomPlugin == null)
                    throw new IOException("No such plugin");

                pluginExecution.setId(String.valueOf(incCounter));
                incCounter++;
                pomPlugin.addExecution(pluginExecution);

            }
        }, null, null, null);
        return pluginExecution;
    }


    private void addProperties(Map<String, String> additionalProperties) throws IOException, XmlPullParserException {

        Properties properties = model.getProperties();

        if (properties == null)
            model.setProperties(new Properties());

        for (Map.Entry<String, String> entry : additionalProperties.entrySet()) {
            model.getProperties().setProperty(entry.getKey(), entry.getValue());
        }

        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new FileOutputStream(pomFile), model);
    }

    protected String getEvosuiteTestsFolder() {
        return String.valueOf(projectDirs.get("testSourceDirectory"));
    }

    protected PluginExecution addCompilerPlugin(String baseDir) throws IOException, XmlPullParserException {
        PluginExecution pluginExecution1 = new PluginExecution();
        pluginExecution1.setPhase("compile");

        final Xpp3Dom configuration = new Xpp3Dom("configuration");
        final Xpp3Dom quiet = new Xpp3Dom("debug");
        quiet.setValue("true");
        configuration.addChild(quiet);

        PluginExecution pluginExecution2 = new PluginExecution();
        pluginExecution2.setPhase("test-compile");

        String compilerVersion = null;
        Xpp3Dom target = null;
        Xpp3Dom source = null;

        for (Plugin plugin : model.getBuild().getPlugins()) {
            if (plugin.getGroupId().contains("org.apache.maven.plugins") &&
                    plugin.getArtifactId().contains("maven-compiler-plugin")) {
                compilerVersion = plugin.getVersion();

                Xpp3Dom configurationEx = (Xpp3Dom) plugin.getConfiguration();


                if (configurationEx != null) {
                    target = configurationEx.getChild("target");
                    source = configurationEx.getChild("source");
                }
            }
        }

        if (source == null) {
            final Xpp3Dom quiet2 = new Xpp3Dom("source");
            quiet2.setValue("1.8");
            configuration.addChild(quiet2);
        }

        if (target == null) {
            final Xpp3Dom quiet3 = new Xpp3Dom("target");
            quiet3.setValue("1.8");
            configuration.addChild(quiet3);
        }

        pluginExecution1.setConfiguration(configuration);
        pluginExecution2.setConfiguration(configuration);

        Plugin plugin = addPlugin("org.apache.maven.plugins", "maven-compiler-plugin",
                compilerVersion == null ? "3.8.1" : compilerVersion);

        addPluginExecution(plugin, pluginExecution1);
        return addPluginExecution(plugin, pluginExecution2);
    }

    protected String getFullClassPathForProjectExploration() throws IOException {

        String propertiesFileName = "classpath.txt";

        String classpath = new String(Files.readAllBytes(Paths.get(String.valueOf(projectDirs.get("baseDir")), propertiesFileName)));

        String testsOutputDirectory = projectDirs.get("testOutputDirectory").toAbsolutePath().toString();
        String outputDirectory = projectDirs.get("outputDirectory").toAbsolutePath().toString();

        return classpath.concat(File.pathSeparator).
                concat(testsOutputDirectory).concat(File.pathSeparator).
                concat(outputDirectory).concat(File.pathSeparator).
                concat(daikonPath);
    }

    public boolean prepareProject() throws IOException, XmlPullParserException {
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
                "maven-dependency-plugin", "2.9");

        addPluginExecution(plugin, pluginExecution);


        return executeMaven(projectDirs.get("baseDir"),
                new String[]{"install"}); //due to Maven projects

    }

    public boolean buildProject(){
        return executeMaven(projectDirs.get("baseDir"),
                new String[]{"package"});

    }

}
