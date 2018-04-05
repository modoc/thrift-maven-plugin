package io.potter.thrift.maven;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * tester for Thrift
 * Created by Fucheng on 2018/04/05.
 */
public class ThriftTest {

    private File testRootDir;
    private File idlDir;
    private File genJavaDir;
    private Thrift.Builder builder;

    @Before
    public void setup() throws Exception {
        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        testRootDir = new File(tmpDir, "thrift-test");

        if (testRootDir.exists()) {
            FileUtils.cleanDirectory(testRootDir);
        } else {
            Assert.assertTrue("Failed to create output directory for test: " + testRootDir.getPath(), testRootDir.mkdir());
        }

        File testResourceDir = new File("src/test/resources");
        Assert.assertTrue("Unable to find test resources", testRootDir.exists());

        idlDir = new File(testResourceDir, "idl");
        genJavaDir = new File(testRootDir, Thrift.GENERATED_JAVA);
        builder = new Thrift.Builder("thrift", testRootDir);
        builder
                .setGenerator("java")
                .addThriftPathElement(idlDir);
    }

    @Test
    public void testThriftCompile() throws Exception {
        executeThriftCompile();
    }

    @Test
    public void testThriftCompileWithGeneratorOption() throws Exception {
        builder.setGenerator("java:private-members,hashcode");
        executeThriftCompile();
    }

    private void executeThriftCompile() throws CommandLineException {
        final File thriftFile = new File(idlDir, "shared.thrift");

        builder.addThriftFile(thriftFile);

        final Thrift thrift = builder.build();

        Assert.assertTrue("File not found: shared.thrift", thriftFile.exists());
        Assert.assertFalse("gen-java directory should not exist", genJavaDir.exists());

        // execute the compile
        final int result = thrift.compile();
        Assert.assertEquals(0, result);

        Assert.assertFalse("gen-java directory was not removed", genJavaDir.exists());
        Assert.assertTrue("generated java code doesn't exist",
                new File(testRootDir, "shared/SharedService.java").exists());
    }

    @Test
    public void testThriftMultipleFileCompile() throws Exception {
        final File sharedThrift = new File(idlDir, "shared.thrift");
        final File tutorialThrift = new File(idlDir, "tutorial.thrift");

        builder.addThriftFile(sharedThrift);
        builder.addThriftFile(tutorialThrift);

        final Thrift thrift = builder.build();

        Assert.assertTrue("File not found: shared.thrift", sharedThrift.exists());
        Assert.assertFalse("gen-java directory should not exist", genJavaDir.exists());

        // execute the compile
        final int result = thrift.compile();
        Assert.assertEquals(0, result);

        Assert.assertFalse("gen-java directory was not removed", genJavaDir.exists());
        Assert.assertTrue("generated java code doesn't exist",
                new File(testRootDir, "shared/SharedService.java").exists());
        Assert.assertTrue("generated java code doesn't exist",
                new File(testRootDir, "tutorial/InvalidOperation.java").exists());
    }

    @Test
    public void testBadCompile() throws Exception {
        final File thriftFile = new File(testRootDir, "missing.thrift");
        builder.addThriftPathElement(testRootDir);

        // Hacking around checks in addThrift file.
        Assert.assertTrue(thriftFile.createNewFile());
        builder.addThriftFile(thriftFile);
        Assert.assertTrue(thriftFile.delete());

        final Thrift thrift = builder.build();

        Assert.assertTrue(!thriftFile.exists());
        Assert.assertFalse("gen-java directory should not exist", genJavaDir.exists());

        // execute the compile
        final int result = thrift.compile();
        Assert.assertEquals(1, result);
    }

    @Test
    public void testFileInPathPreCondition() throws Exception {
        final File thriftFile = new File(testRootDir, "missing.thrift");

        // Hacking around checks in addThrift file.
        Assert.assertTrue(thriftFile.createNewFile());
        try {
            builder.addThriftFile(thriftFile);
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
        }
    }

    @After
    public void cleanup() throws Exception {
        if (testRootDir.exists()) {
            FileUtils.cleanDirectory(testRootDir);
            Assert.assertTrue("Failed to delete output directory for test: " + testRootDir.getPath(), testRootDir.delete());
        }
    }

}
