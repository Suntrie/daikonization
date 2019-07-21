package utils;

import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static utils.CommandExecutors.executeMaven;
import static utils.CommandExecutors.getPathThroughProps;

public abstract class MavenProjectUtils {

    protected final String evosuitePath = System.getenv("EVOSUITE") + "/evosuite-1.0.6.jar";
    protected final String daikonPath = System.getenv("DAIKONDIR") + "/daikon.jar";

    protected Map<String, Path> projectDirs = new HashMap<>();

    public MavenProjectUtils(String baseDir) throws IOException, XmlPullParserException {
        projectDirs.put("baseDir", Paths.get(baseDir));
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

        addPlugin("org.codehaus.mojo", "properties-maven-plugin", "1.0.0", pluginExecution
        );  //TODO: probably, through parent hierarchy


        addProperties(new HashMap<String, String>() {{
            put("outputDirectory", "${project.build.outputDirectory}");
            put("testOutputDirectory", "${project.build.testOutputDirectory}");
            put("testSourceDirectory", "${basedir}/.evosuite/best-tests");
        }});


        Path baseDir = projectDirs.get("baseDir");

        executeMaven(baseDir,
                new String[]{"initialize"});

        Map<String, Path> propsDirs = getPathThroughProps(Paths.get(String.valueOf(baseDir), propertiesFileName));
        projectDirs.putAll(propsDirs);
    }


    protected interface GeneratorFunction {
        public void generateMavenObject(Model model, String groupId, String artifactId, String version);
    }

    protected String getMavenCoords() throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();

        String baseDir = String.valueOf(projectDirs.get("baseDir"));

        File pomFile = new File(baseDir, "/pom.xml");
        Model model = reader.read(new FileInputStream(pomFile));

        return model.getGroupId()+":"+model.getArtifactId()+":"+model.getVersion();


    }

    protected void generateAndSaveMavenObject(GeneratorFunction generatorFunction,
                                              String groupId, String artifactId, String version) throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();

        String baseDir = String.valueOf(projectDirs.get("baseDir"));

        File pomFile = new File(baseDir, "/pom.xml");
        Model model = reader.read(new FileInputStream(pomFile));

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

            for(Dependency dependency1: model.getDependencies()){
                if (dependency1.equals(dependency)){
                    dependency.setVersion(version1);
                    previous = true;
                    break;
                }
            }

            if (!previous)
                model.addDependency(dependency);

        }, groupId, artifactId, version);
    }


    protected void addPlugin(String groupId, String artifactId, String version, PluginExecution... pluginExecutions) throws IOException, XmlPullParserException {

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
            }else{
                for (Plugin plugin1: model.getBuild().getPlugins()){
                    if (plugin1.equals(plugin)){
                        plugin = plugin1;
                        break;
                    }
                }
            }

            if (pluginExecutions != null) {
                for (PluginExecution pluginExecution : pluginExecutions)
                    if (!plugin.getExecutions().stream().map(PluginExecution::getPhase)
                            .collect(Collectors.toSet()).contains(pluginExecution.getPhase())) {
                    plugin.addExecution(pluginExecution);
                }
            }

        }, groupId, artifactId, version);
    }


    private void addProperties(Map<String, String> additionalProperties) throws IOException, XmlPullParserException {

        MavenXpp3Reader reader = new MavenXpp3Reader();
        String baseDir = String.valueOf(projectDirs.get("baseDir"));
        File pomFile = new File(baseDir, "/pom.xml");
        Model model = reader.read(new FileInputStream(pomFile));

        Properties properties = model.getProperties();

        if (properties == null)
            model.setProperties(new Properties());

        for (Map.Entry<String, String> entry : additionalProperties.entrySet()) {
            model.getProperties().setProperty(entry.getKey(), entry.getValue());
        }

        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new FileOutputStream(pomFile), model);
    }

    protected String getEvosuiteTestsFolder(){
        return String.valueOf(projectDirs.get("testSourceDirectory"));
    }
}
