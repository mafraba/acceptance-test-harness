package org.jenkinsci.test.acceptance.docker;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SystemUtils;
import org.jenkinsci.utils.process.CommandBuilder;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

/**
 * Container image, a template to launch virtual machines from.
 *
 * @author Kohsuke Kawaguchi
 */
public class DockerImage {

    public static final String DEFAULT_DOCKER_HOST = "127.0.0.1";
    public final String tag;
    static DockerHostResolver dockerHostResolver = new DockerHostResolver();

    public DockerImage(String tag) {
        this.tag = tag;
    }

    /**
     * Start container from this image.
     */
    public <T extends DockerContainer> Starter<T> start(Class<T> type) {
        return new Starter(type, this);
    }

    /**
     * @deprecated Use {@link Starter} instead.
     */
    @Deprecated
    public <T extends DockerContainer> T start(Class<T> type, CommandBuilder options, CommandBuilder cmd, int portOffset) throws InterruptedException, IOException {
        DockerFixture f = type.getAnnotation(DockerFixture.class);

        String addr = f.bindIp();
        if (!DockerFixture.DEFAULT_DOCKER_IP.equals(f.bindIp())) {
            // specifying an address will only work if the docker host is on localhost.
            if (InetAddress.getByName(getDockerHost()).isLoopbackAddress()) {
                return start(type).withPortOffset(portOffset).withIpAddress(f.bindIp()).withOptions(options).withArgs(cmd).start();
            }
            else {
                throw new AssertionError("Test would fail as docker requires local networks on a remote machine - Hint use `@WithDocker(localOnly=true)´ or do not specify a bindIp in " + type.getName());
            }
        }
        return start(type).withPortOffset(portOffset).withOptions(options).withArgs(cmd).start();
    }

    /**
     * @deprecated Use {@link Starter} instead.
     */
    @Deprecated
    public <T extends DockerContainer> T start(Class<T> type, CommandBuilder options, CommandBuilder cmd) throws InterruptedException, IOException {
        return start(type).withOptions(options).withArgs(cmd).start();
    }

    /**
     * @deprecated Use {@link Starter} instead.
     */
    @Deprecated
    public <T extends DockerContainer> T start(Class<T> type, int[] ports, CommandBuilder options, CommandBuilder cmd) throws InterruptedException, IOException {
        return start(type).withPorts(ports).withOptions(options).withArgs(cmd).start();
    }

    /**
     * @deprecated Use {@link Starter} instead.
     */
    @Deprecated
    public <T extends DockerContainer> T start(Class<T> type, int[] ports,int localPortOffset, String ipAddress, CommandBuilder options, CommandBuilder cmd) throws InterruptedException, IOException {
        return start(type).withPorts(ports).withPortOffset(localPortOffset).withIpAddress(ipAddress).withOptions(options).withArgs(cmd).start();
    }

    private <T extends DockerContainer> T start(Starter starter, Class<T> type) throws InterruptedException, IOException {
        CommandBuilder docker = Docker.cmd("run");
        docker.add("-d");
        for (int p : starter.ports) {
            docker.add("-p", starter.getPortMapping(p));
        }

        docker.add(starter.options);
        docker.add(tag);
        docker.add(starter.args);

        File logfile = starter.log;

        System.out.printf("Launching Docker container `%s`: logfile will be at %s\n", docker.toString(), logfile);

        Process p = docker.build()
                .redirectInput(new File(SystemUtils.IS_OS_WINDOWS ? "NUL": "/dev/null"))
                .redirectErrorStream(true)
                .start();


        String cid = waitForCid(docker, p);

        Process logProcess = Docker.cmd("logs")
                .add("-f")
                .add(cid)
                .build()
                .redirectInput(new File(SystemUtils.IS_OS_WINDOWS ? "NUL": "/dev/null"))
                .redirectErrorStream(true)
                .redirectOutput(logfile)
                .start();

        try {
            T t = type.newInstance();
            t.init(cid, logProcess, logfile);
            return t;
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    private String waitForCid(CommandBuilder docker, Process p) throws InterruptedException, IOException {

        String output = IOUtils.toString(p.getInputStream());

        if (p.waitFor() != 0) {
            throw new IOException("docker died unexpectedly with return code " + p.exitValue() + 
                                   " : " + docker + "\n" + output);
        }

        if (output != null && output.length() != 0) {
            return output.trim();
        }

        throw new IOException("docker didn't output a container id or have a non-zero exit status. Huh?: "+docker);
    }

    /**
     * Get the string representation of the docker host as set by DOCKER_HOST environment variable or the localhost address if it is not set (or is a socket).
     * @return an IP Address or hostname of the docker host 
     */
    public static String getDockerHost() {
        final String dockerHostEnvironmentVariable = dockerHostResolver.getDockerHostEnvironmentVariable();
        return dockerHostEnvironmentVariable != null ? getIp(dockerHostEnvironmentVariable) : DEFAULT_DOCKER_HOST;
    }

    private static String getIp(String uri) {
        final URI dockerHost = URI.create(uri);
        final String host = dockerHost.getHost();
        return host != null ? host : DEFAULT_DOCKER_HOST;
    }

    @Override
    public String toString() {
        return "DockerImage: "+tag;
    }

    static class DockerHostResolver {
        public String getDockerHostEnvironmentVariable() {
            return System.getenv("DOCKER_HOST");
        }
    }

    public static final class Starter<T extends DockerContainer> {
        private final DockerImage image;
        private final Class<T> type;

        private CommandBuilder options;
        private CommandBuilder args;
        private String ipAddress = getDockerHost();
        private Integer portOffset;
        private int[] ports;
        private File log;

        public Starter(Class<T> type, DockerImage image) {
            this.type = type;
            this.image = image;

            DockerFixture fixtureAnnotation = type.getAnnotation(DockerFixture.class);
            ports = fixtureAnnotation.ports();
        }

        public @Nonnull Starter<T> withPorts(int... ports) {
            this.ports = ports;
            return this;
        }

        public @Nonnull Starter<T> withPortOffset(Integer portOffset) {
            this.portOffset = portOffset;
            return this;
        }

        public @Nonnull Starter<T> withIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        // TODO do not abuse CommandBuilder
        public @Nonnull Starter<T> withOptions(CommandBuilder options) {
            this.options = options;
            return this;
        }

        // TODO do not abuse CommandBuilder
        public @Nonnull Starter<T> withArgs(CommandBuilder args) {
            this.args = args;
            return this;
        }

        public @Nonnull Starter<T> withLog(File log) {
            this.log = log;
            return this;
        }

        public @Nonnull T start() throws InterruptedException, IOException {
            return image.start(this, type);
        }

        private String getPortMapping(int port) {
            return portOffset == null
                    ? ipAddress + "::" + port
                    : ipAddress + ":" + (portOffset + port) + ":" + port
            ;
        }
    }
}
