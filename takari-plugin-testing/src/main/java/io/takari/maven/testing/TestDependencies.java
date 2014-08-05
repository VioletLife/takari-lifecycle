package io.takari.maven.testing;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class TestDependencies {

  public static final String KEY_CLASSPATH = "classpath";

  private final TestProperties properties;

  public TestDependencies(TestProperties properties) {
    this.properties = properties;
  }

  /**
   * Returns location of the current project classes, i.e. target/classes directory, and all project dependencies with scope=runtime.
   */
  public List<File> getRuntimeClasspath() {
    StringTokenizer st = new StringTokenizer(properties.get(KEY_CLASSPATH), File.pathSeparator);
    List<File> dependencies = new ArrayList<>();
    while (st.hasMoreTokens()) {
      dependencies.add(new File(st.nextToken()));
    }
    return dependencies;
  }

}