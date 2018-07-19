# thrift-maven-plugin

thrift compiler with pre-compiled archive, only work with java.

## Usage

    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>1.5.0.Final</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>io.potter.thrift.tools</groupId>
                <artifactId>thrift-maven-plugin</artifactId>
                <version>1.0-SNAPSHOT</version>
                <configuration>
                    <!--<thriftExecutable>thrift</thriftExecutable>-->
                    <generator>java:private-members</generator>
                    <thriftSourceRoot>${project.basedir}/src/thrift/</thriftSourceRoot>
                    <thriftArtifact>io.potter.thrift:thriftc:0.11.0:exe:${os.detected.classifier}</thriftArtifact>
                </configuration>
                <executions>
                    <execution>
                        <id>thrift-sources</id>
                        <phase>generate-sources</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

## Execution Parameters

parameter | default | meaning
--- | --- | ---
thriftExecutable | thrift | The path that points to thrift, default value is thrift in PATH
thriftArtifact |  | remote artifact coordinate
generator | java:hashcode | This string is passed to the `--gen` option of the `thrift` parameter
thriftSourceRoot | ${basedir}/src/main/thrift | The source directories containing the sources to be compiled
outputDirectory | ${project.build.directory}/generated-sources/thrift | The directory into which the `.java` will be created
temporaryThriftFileDirectory | ${project.build.directory}/thrift-dependencies | Since `thrift` cannot access jars, thrift files in dependencies are extracted to this location and deleted on exit. This directory is always cleaned during execution.
hashDependentPaths | true | Set this to `false` to disable hashing of dependent jar paths. This plugin expands jars on the classpath looking for embedded .thrift files. Normally these paths are hashed (MD5) to avoid issues with long file names on windows. However if this property is set to `false` longer paths will be used.
localRepository | ${localRepository} | The path to the local maven `repository`
includes | \*\*/\*.thrift | searched for include directives
excludes |  | searched for exclude directives
checkStaleness | false | if set to `true`, plugin will do nothing if thrift files' modify time is earlier than java files in output directory
staleMillis | 0 | only work when `checkStaleness`==`true`, thrift files' modify time must be earlier than java files exceed at least `staleMillis`


## reference:

* [dtrott/maven-thrift-plugin](https://github.com/dtrott/maven-thrift-plugin)
* [ccascone/mvn-thrift-compiler](https://github.com/ccascone/mvn-thrift-compiler)
* [xolstice/protobuf-maven-plugin](https://github.com/xolstice/protobuf-maven-plugin)
* [guide-java-plugin-development](https://maven.apache.org/guides/plugin/guide-java-plugin-development.html)

## contact

mailto: zhfchdev@gmail.com
