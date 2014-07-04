package io.takari.maven.plugins.plugin.testing;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInputMetadata;
import io.takari.maven.plugins.resources.ResourcesProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.m2e.workspace.MutableWorkspaceState;

@Mojo(name = "testProperties", defaultPhase = LifecyclePhase.GENERATE_TEST_RESOURCES)
public class TestPropertiesMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.properties}")
  private Properties properties;

  @Parameter(defaultValue = "${project}")
  @Incremental(configuration = Configuration.ignore)
  protected MavenProject project;

  // oddly, ${localRepository} did not work
  @Parameter(defaultValue = "${settings.localRepository}")
  private File localRepository;

  @Parameter(defaultValue = "${session.request.userSettingsFile}")
  private File userSettingsFile;

  @Parameter(defaultValue = "${project.basedir}/src/test/test.properties")
  private File testProperties;

  @Parameter(defaultValue = "${project.build.testOutputDirectory}/test.properties")
  private File outputFile;

  @Parameter(defaultValue = "${plugin.artifactMap(io.takari.m2e.workspace:org.eclipse.m2e.workspace.cli)}", readonly = true)
  private Artifact workspaceResolver;

  @Parameter(defaultValue = "${project.build.directory}/workspacestate.properties")
  private File workspaceState;

  @Parameter(defaultValue = "${session.projectDependencyGraph}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  private ProjectDependencyGraph reactorDependencies;

  @Component
  private DefaultBuildContext<?> context;

  @Component
  private ResourcesProcessor resourceProcessor;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      Properties properties = new Properties();

      if (testProperties.canRead()) {
        mergeCustomTestProperties(properties);
      }

      if (!context.isProcessingRequired()) {
        context.markOutputsAsUptodate();
        return;
      }

      writeWorkspaceState();

      // well-known properties
      putIfAbsent(properties, "localRepository", localRepository.getAbsolutePath());
      putIfAbsent(properties, "userSettingsFile", userSettingsFile.getAbsolutePath());
      putIfAbsent(properties, "project.groupId", project.getGroupId());
      putIfAbsent(properties, "project.artifactId", project.getArtifactId());
      putIfAbsent(properties, "project.version", project.getVersion());
      putIfAbsent(properties, "workspaceStateProperties", workspaceState.getAbsolutePath());
      putIfAbsent(properties, "workspaceResolver", workspaceResolver.getFile().getAbsolutePath());

      try (OutputStream os = context.processOutput(outputFile).newOutputStream()) {
        properties.store(os, "Generated by " + getClass().getName());
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Could not create test.properties file", e);
    }
  }

  private void writeWorkspaceState() throws MojoExecutionException {
    MutableWorkspaceState state = new MutableWorkspaceState();
    putProject(state, project);
    for (MavenProject other : reactorDependencies.getUpstreamProjects(project, true)) {
      putProject(state, other);
    }
    try {
      state.store(workspaceState);
    } catch (IOException e) {
      throw new MojoExecutionException("Could not create reactory state file " + workspaceState, e);
    }
  }

  private void putProject(MutableWorkspaceState state, MavenProject other) {
    state.putPom(other.getFile(), other.getGroupId(), other.getArtifactId(), other.getVersion());
    if (other.getArtifact().getFile() != null) {
      putArtifact(state, other.getArtifact());
    }
    for (Artifact artifact : other.getAttachedArtifacts()) {
      putArtifact(state, artifact);
    }
  }

  private void putArtifact(MutableWorkspaceState state, Artifact artifact) {
    state.putArtifact(artifact.getFile(), artifact.getGroupId(),
        artifact.getArtifactId(), //
        artifact.getArtifactHandler().getExtension(), artifact.getClassifier(),
        artifact.getBaseVersion());
  }

  private void mergeCustomTestProperties(Properties properties) throws MojoExecutionException {
    DefaultInputMetadata<File> metadata = context.registerInput(testProperties);
    if (metadata.getStatus() != ResourceStatus.UNMODIFIED) {
      Properties custom = new Properties();
      try (InputStream is = new FileInputStream(metadata.process().getResource())) {
        custom.load(is);
      } catch (IOException e) {
        throw new MojoExecutionException("Could not read test.properties file " + testProperties, e);
      }
      for (String key : custom.stringPropertyNames()) {
        properties.put(key, expand(custom.getProperty(key)));
      }
    }
  }

  private void putIfAbsent(Properties properties, String key, String value) {
    if (!properties.containsKey(key)) {
      properties.put(key, value);
    }
  }

  private String expand(String value) {
    // resource filtering configuration should match AbstractProcessResourcesMojo
    // TODO figure out how to move this to a common component
    Map<Object, Object> properties = new HashMap<Object, Object>(this.properties);
    properties.put("project", project);
    properties.put("localRepository", localRepository);
    properties.put("userSettingsFile", userSettingsFile);
    StringWriter writer = new StringWriter();
    try {
      resourceProcessor.filter(new StringReader(value), writer, properties);
      return writer.toString();
    } catch (IOException e) {
      return value; // shouldn't happen
    }
  }
}
