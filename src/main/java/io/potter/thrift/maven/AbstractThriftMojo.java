package io.potter.thrift.maven;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.io.RawInputStreamFacade;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * <p>Abstract Mojo implementation.</p>
 *
 * <p>This class is extended by {@link ThriftCompileMojo} and
 * {@link ThriftTestCompileMojo} in order to override the specific configuration for
 * compiling the main or test classes respectively.</p>
 *
 * @author Gregory Kick
 * @author David Trott
 * @author Brice Figureau
 * @author zhfchdev@gmail.com
 *
 * Created by Fucheng on 2018/04/05.
 */
public abstract class AbstractThriftMojo extends AbstractMojo {

    private static final String THRIFT_FILE_SUFFIX = ".thrift";

    private static final String DEFAULT_INCLUDES = "**/*" + THRIFT_FILE_SUFFIX;

    /**
     * The current Maven project.
     *
     * @parameter default-value="${project}"
     * @readonly
     * @required
     */
    MavenProject project;

    /**
     * A helper used to add resources to the project.
     *
     * @component
     * @required
     */
    MavenProjectHelper projectHelper;

    /**
     * The current Maven Session Object.
     *
     * @@parameter default-value="${session}"
     * @readonly
     */
    protected MavenSession session;

    /**
     * A factory for Maven artifact definitions.
     *
     * @parameter
     * @readonly
     * @required
     */
    private ArtifactFactory artifactFactory;

    /**
     * A component that implements resolution of Maven artifacts from repositories.
     *
     * @parameter
     * @readonly
     * @required
     */
    private ArtifactResolver artifactResolver;

    /**
     * A component that handles resolution of Maven artifacts.
     *
     * @parameter
     * @readonly
     * @required
     */
    private RepositorySystem repositorySystem;

    /**
     * A component that handles resolution errors.
     *
     * @parameter
     * @readonly
     * @required
     */
    private ResolutionErrorHandler resolutionErrorHandler;

    /**
     * This is the path to the {@code thrift} executable. By default it will search the {@code $PATH}.
     *
     * @parameter default-value=""
     */
    private String thriftExecutable;

    /**
     * work when thriftExecutable is not set
     *
     * @parameter
     */
    private String thriftArtifact;

    /**
     * This string is passed to the {@code --gen} option of the {@code thrift} parameter. By default
     * it will generate Java output. The main reason for this option is to be able to add options
     * to the Java generator - if you generate something else, you're on your own.
     *
     * @parameter default-value="java:hashcode"
     */
    private String generator;

    /**
     * @parameter
     */
    private File[] additionalThriftPathElements = new File[]{};

    /**
     * Since {@code thrift} cannot access jars, thrift files in dependencies are extracted to this location
     * and deleted on exit. This directory is always cleaned during execution.
     *
     * @parameter expression="${project.build.directory}/thrift-dependencies"
     * @required
     */
    private File temporaryThriftFileDirectory;

    /**
     * This is the path to the local maven {@code repository}.
     *
     * @parameter default-value="${localRepository}"
     * @required
     */
    private ArtifactRepository localRepository;

    /**
     * Remote repositories for artifact resolution.
     *
     * @parameter default-value="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    private List<ArtifactRepository> remoteRepositories;

    /**
     * A directory where native launchers for java protoc plugins will be generated.
     *
     * @parameter default-value=""${project.build.directory}/thrift-plugins"
     */
    private File thriftPluginDirectory;

    /**
     * Set this to {@code false} to disable hashing of dependent jar paths.
     * <p/>
     * This plugin expands jars on the classpath looking for embedded .thrift files.
     * Normally these paths are hashed (MD5) to avoid issues with long file names on windows.
     * However if this property is set to {@code false} longer paths will be used.
     *
     * @parameter default-value="true"
     * @required
     */
    private boolean hashDependentPaths;

    /**
     * @parameter
     */
    private Set<String> includes = ImmutableSet.of(DEFAULT_INCLUDES);

    /**
     * @parameter
     */
    private Set<String> excludes = ImmutableSet.of();

    /**
     * @parameter
     */
    private long staleMillis = 0;

    /**
     * @parameter
     */
    private boolean checkStaleness = false;

    /**
     * Executes the mojo.
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        checkParameters();
        final File thriftSourceRoot = getThriftSourceRoot();
        if (thriftSourceRoot.exists()) {
            try {
                ImmutableSet<File> thriftFiles = findThriftFilesInDirectory(thriftSourceRoot);
                final File outputDirectory = getOutputDirectory();
                ImmutableSet<File> outputFiles = findGeneratedFilesInDirectory(getOutputDirectory());

                if (thriftFiles.isEmpty()) {
                    getLog().info("No thrift files to compile.");
                } else if (checkStaleness && ((lastModified(thriftFiles) + staleMillis) < lastModified(outputFiles))) {
                    getLog().info("Skipping compilation because target directory newer than sources.");
                    attachFiles();
                } else {
                    ImmutableSet<File> derivedThriftPathElements =
                            makeThriftPathFromJars(temporaryThriftFileDirectory, getDependencyArtifactFiles());
                    Preconditions.checkArgument(outputDirectory.mkdirs(), "create output directory fail");

                    // Quick fix to fix issues with two mvn installs in a row (ie no clean)
                    FileUtils.cleanDirectory(outputDirectory);

                    if (thriftExecutable == null && thriftArtifact != null) {
                        final Artifact artifact = createDependencyArtifact(thriftArtifact);
                        final File file = resolveBinaryArtifact(artifact);
                        thriftExecutable = file.getAbsolutePath();
                    }
                    if (thriftExecutable == null) {
                        // Try to fall back to 'protoc' in $PATH
                        getLog().warn("No 'thriftExecutable' parameter is configured, using the default: 'thrift'");
                        thriftExecutable = "thrift";
                    }

                    Thrift thrift = new Thrift.Builder(thriftExecutable, outputDirectory)
                            .setGenerator(generator)
                            .addThriftPathElement(thriftSourceRoot)
                            .addThriftPathElements(derivedThriftPathElements)
                            .addThriftPathElements(Arrays.asList(additionalThriftPathElements))
                            .addThriftFiles(thriftFiles)
                            .build();
                    final int exitStatus = thrift.compile();
                    if (exitStatus != 0) {
                        getLog().error("thrift failed output: " + thrift.getOutput());
                        getLog().error("thrift failed error: " + thrift.getError());
                        throw new MojoFailureException(
                                "thrift did not exit cleanly. Review output for more information.");
                    }
                    attachFiles();
                }
            } catch (IOException e) {
                throw new MojoFailureException("An IO error occured", e);
            } catch (IllegalArgumentException e) {
                throw new MojoFailureException("thrift failed to execute because: " + e.getMessage(), e);
            } catch (CommandLineException e) {
                throw new MojoExecutionException("An error occurred while invoking thrift.", e);
            }
        } else {
            getLog().info(String.format("%s does not exist. Review the configuration or consider disabling the plugin.",
                    thriftSourceRoot));
        }
    }

    private ImmutableSet<File> findGeneratedFilesInDirectory(File directory) throws IOException {
        if (directory == null || !directory.isDirectory())
            return ImmutableSet.of();

        List<File> javaFilesInDirectory = FileUtils.getFiles(directory, "**/*.java", null);
        return ImmutableSet.copyOf(javaFilesInDirectory);
    }

    private long lastModified(ImmutableSet<File> files) {
        long result = 0;
        for (File file : files) {
            if (file.lastModified() > result)
                result = file.lastModified();
        }
        return result;
    }

    private void checkParameters() {
        Preconditions.checkNotNull(project, "project");
        Preconditions.checkNotNull(projectHelper, "projectHelper");
        Preconditions.checkNotNull(thriftExecutable, "thriftExecutable");
        Preconditions.checkNotNull(generator, "generator");
        final File thriftSourceRoot = getThriftSourceRoot();
        Preconditions.checkNotNull(thriftSourceRoot);
        Preconditions.checkArgument(!thriftSourceRoot.isFile(), "thriftSourceRoot is a file, not a diretory");
        Preconditions.checkNotNull(temporaryThriftFileDirectory, "temporaryThriftFileDirectory");
        Preconditions.checkState(!temporaryThriftFileDirectory.isFile(), "temporaryThriftFileDirectory is a file, not a directory");
        final File outputDirectory = getOutputDirectory();
        Preconditions.checkNotNull(outputDirectory);
        Preconditions.checkState(!outputDirectory.isFile(), "the outputDirectory is a file, not a directory");
    }

    protected abstract File getThriftSourceRoot();

    protected abstract List<Artifact> getDependencyArtifacts();

    protected abstract File getOutputDirectory();

    protected abstract void attachFiles();

    /**
     * Gets the {@link File} for each dependency artifact.
     *
     * @return A set of all dependency artifacts.
     */
    private ImmutableSet<File> getDependencyArtifactFiles() {
        Set<File> dependencyArtifactFiles = Sets.newHashSet();
        for (Artifact artifact : getDependencyArtifacts()) {
            dependencyArtifactFiles.add(artifact.getFile());
        }
        return ImmutableSet.copyOf(dependencyArtifactFiles);
    }

    private ImmutableSet<File> makeThriftPathFromJars(File temporaryThriftFileDirectory, Iterable<File> classpathElementFiles)
            throws IOException, MojoExecutionException {
        Preconditions.checkNotNull(classpathElementFiles, "classpathElementFiles");
        // clean the temporary directory to ensure that stale files aren't used
        if (temporaryThriftFileDirectory.exists()) {
            FileUtils.cleanDirectory(temporaryThriftFileDirectory);
        }
        Set<File> thriftDirectories = Sets.newHashSet();
        for (File classpathElementFile : classpathElementFiles) {
            // for some reason under IAM, we receive poms as dependent files
            // I am excluding .xml rather than including .jar as there may be other extensions in use (sar, har, zip)
            if (classpathElementFile.isFile() && classpathElementFile.canRead() &&
                    !classpathElementFile.getName().endsWith(".xml")) {

                // create the jar file. the constructor validates.
                JarFile classpathJar;
                try {
                    classpathJar = new JarFile(classpathElementFile);
                } catch (IOException e) {
                    throw new IllegalArgumentException(String.format(
                            "%s was not a readable artifact", classpathElementFile));
                }
                for (JarEntry jarEntry : Collections.list(classpathJar.entries())) {
                    final String jarEntryName = jarEntry.getName();
                    if (jarEntry.getName().endsWith(THRIFT_FILE_SUFFIX)) {
                        final File uncompressedCopy =
                                new File(new File(temporaryThriftFileDirectory,
                                        truncatePath(classpathJar.getName())), jarEntryName);
                        uncompressedCopy.getParentFile().mkdirs();
                        FileUtils.copyStreamToFile(new RawInputStreamFacade(classpathJar
                                .getInputStream(jarEntry)), uncompressedCopy);
                        thriftDirectories.add(uncompressedCopy.getParentFile());
                    }
                }
            } else if (classpathElementFile.isDirectory()) {
                File[] thriftFiles = classpathElementFile.listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.endsWith(THRIFT_FILE_SUFFIX);
                    }
                });

                if (thriftFiles.length > 0) {
                    thriftDirectories.add(classpathElementFile);
                }
            }
        }
        return ImmutableSet.copyOf(thriftDirectories);
    }

    private ImmutableSet<File> findThriftFilesInDirectory(File directory) throws IOException {
        Preconditions.checkNotNull(directory);
        Preconditions.checkArgument(directory.isDirectory(), "%s is not a directory", directory);

        final Joiner joiner = Joiner.on(',');

        List<File> thriftFilesInDirectory = FileUtils.getFiles(directory, joiner.join(includes), joiner.join(excludes));
        return ImmutableSet.copyOf(thriftFilesInDirectory);
    }

    ImmutableSet<File> findThriftFilesInDirectories(Iterable<File> directories) throws IOException {
        Preconditions.checkNotNull(directories);
        Set<File> thriftFiles = Sets.newHashSet();
        for (File directory : directories) {
            thriftFiles.addAll(findThriftFilesInDirectory(directory));
        }
        return ImmutableSet.copyOf(thriftFiles);
    }

    /**
     * Truncates the path of jar files so that they are relative to the local repository.
     *
     * @param jarPath the full path of a jar file.
     * @return the truncated path relative to the local repository or root of the drive.
     */
    String truncatePath(final String jarPath) throws MojoExecutionException {

        if (hashDependentPaths) {
            try {
                return toHexString(MessageDigest.getInstance("MD5").digest(jarPath.getBytes()));
            } catch (NoSuchAlgorithmException e) {
                throw new MojoExecutionException("Failed to expand dependent jar", e);
            }
        }

        String repository = localRepository.getBasedir().replace('\\', '/');
        if (!repository.endsWith("/")) {
            repository += "/";
        }

        String path = jarPath.replace('\\', '/');
        int repositoryIndex = path.indexOf(repository);
        if (repositoryIndex != -1) {
            path = path.substring(repositoryIndex + repository.length());
        }

        // By now the path should be good, but do a final check to fix windows machines.
        int colonIndex = path.indexOf(':');
        if (colonIndex != -1) {
            // 2 = :\ in C:\
            path = path.substring(colonIndex + 2);
        }

        return path;
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static String toHexString(byte[] byteArray) {
        final StringBuilder hexString = new StringBuilder(2 * byteArray.length);
        for (final byte b : byteArray) {
            hexString.append(HEX_CHARS[(b & 0xF0) >> 4]).append(HEX_CHARS[b & 0x0F]);
        }
        return hexString.toString();
    }

    private File resolveBinaryArtifact(final Artifact artifact) throws MojoExecutionException {
        final ArtifactResolutionResult result;
        try {
            final ArtifactResolutionRequest request = new ArtifactResolutionRequest()
                    .setArtifact(project.getArtifact())
                    .setResolveRoot(false)
                    .setResolveTransitively(false)
                    .setArtifactDependencies(Collections.singleton(artifact))
                    .setManagedVersionMap(Collections.<String, Artifact>emptyMap())
                    .setLocalRepository(localRepository)
                    .setRemoteRepositories(remoteRepositories)
                    .setOffline(session.isOffline())
                    .setForceUpdate(session.getRequest().isUpdateSnapshots())
                    .setServers(session.getRequest().getServers())
                    .setMirrors(session.getRequest().getMirrors())
                    .setProxies(session.getRequest().getProxies());

            result = repositorySystem.resolve(request);

            resolutionErrorHandler.throwErrors(request, result);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        Set<Artifact> artifacts = result.getArtifacts();

        if (artifacts == null || artifacts.isEmpty()) {
            throw new MojoExecutionException("Unable to resolve plugin artifact");
        }

        Artifact resolvedBinaryArtifact = artifacts.iterator().next();
        if (getLog().isDebugEnabled()) {
            getLog().debug("Resolved artifact: " + resolvedBinaryArtifact);
        }

        // Copy the file to the project build directory and make it executable
        File sourceFile = resolvedBinaryArtifact.getFile();
        String sourceFileName = sourceFile.getName();
        String targetFileName;
        if (Os.isFamily(Os.FAMILY_WINDOWS) && !sourceFileName.endsWith(".exe")) {
            targetFileName = sourceFileName + ".exe";
        } else {
            targetFileName = sourceFileName;
        }
        final File targetFile = new File(thriftPluginDirectory, targetFileName);
        if (targetFile.exists()) {
            // The file must have already been copied in a prior plugin execution/invocation
            getLog().debug("Executable file already exists: " + targetFile.getAbsolutePath());
            return targetFile;
        }
        try {
            FileUtils.forceMkdir(thriftPluginDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to create directory " + thriftPluginDirectory, e);
        }
        try {
            FileUtils.copyFile(sourceFile, targetFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to copy the file to " + thriftPluginDirectory, e);
        }
        if (!Os.isFamily(Os.FAMILY_WINDOWS)) {
            targetFile.setExecutable(true);
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Executable file: " + targetFile.getAbsolutePath());
        }
        return targetFile;
    }

        /**
         * Creates a dependency artifact from a specification in
         * {@code groupId:artifactId:version[:type[:classifier]]} format.
         *
         * @param artifactSpec artifact specification.
         * @return artifact object instance.
         * @throws MojoExecutionException if artifact specification cannot be parsed.
         */
    private Artifact createDependencyArtifact(String artifactSpec) throws MojoExecutionException {
        final String[] parts = artifactSpec.split(":");
        if (parts.length < 3 || parts.length > 5) {
            throw new MojoExecutionException(
                    "Invalid artifact specification format"
                            + ", expected: groupId:artifactId:version[:type[:classifier]]"
                            + ", actual: " + artifactSpec);
        }
        final String type = parts.length >= 4 ? parts[3] : "exe";
        final String classifier = parts.length == 5 ? parts[4] : null;
        return createDependencyArtifact(parts[0], parts[1], parts[2], type, classifier);
    }

    private Artifact createDependencyArtifact(String groupId, String artifactId, String version,
                                              String type, String classifier) throws MojoExecutionException {
        VersionRange versionSpec;
        try {
            versionSpec = VersionRange.createFromVersionSpec(version);
        } catch (final InvalidVersionSpecificationException e) {
            throw new MojoExecutionException("Invalid version specification", e);
        }
        return artifactFactory.createDependencyArtifact(
                groupId,
                artifactId,
                versionSpec,
                type,
                classifier,
                Artifact.SCOPE_RUNTIME);
    }

}
