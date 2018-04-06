package io.potter.thrift.maven;

import com.google.common.collect.ImmutableList;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.List;

/**
 * This mojo executes the {@code thrift} compiler for generating java sources
 * from thrift definitions. It also searches dependency artifacts for
 * thrift files and includes them in the thriftPath so that they can be
 * referenced. Finally, it adds the thrift files to the project as resources so
 * that they are included in the final artifact.
 *
 * Created by Fucheng on 2018/04/05.
 */
@Mojo(
        name = "compile",
        requiresDependencyResolution = ResolutionScope.COMPILE,
        defaultPhase = LifecyclePhase.GENERATE_SOURCES
)
public final class ThriftCompileMojo extends AbstractThriftMojo {

    /**
     * The source directories containing the sources to be compiled.
     */
    @Parameter(defaultValue = "${basedir}/src/main/thrift", required = true)
    private File thriftSourceRoot;

    /**
     * This is the directory into which the {@code .java} will be created.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/thrift", required = true)
    private File outputDirectory;

    @Override
    protected List<Artifact> getDependencyArtifacts() {
        // TODO(gak): maven-project needs generics
        @SuppressWarnings("unchecked")
        List<Artifact> compileArtifacts = project.getCompileArtifacts();
        return compileArtifacts;
    }

    @Override
    protected File getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    protected File getThriftSourceRoot() {
        return thriftSourceRoot;
    }

    @Override
    protected void attachFiles() {
        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        projectHelper.addResource(project, thriftSourceRoot.getAbsolutePath(),
                ImmutableList.of("**/*.thrift"), ImmutableList.of());
    }

}
