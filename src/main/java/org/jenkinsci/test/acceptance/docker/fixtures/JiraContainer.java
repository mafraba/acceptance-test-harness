package org.jenkinsci.test.acceptance.docker.fixtures;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.jira.JIRA;
import org.jenkinsci.test.acceptance.docker.DockerContainer;
import org.jenkinsci.test.acceptance.docker.DockerFixture;
import org.jenkinsci.test.acceptance.po.CapybaraPortingLayer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections.IteratorUtils;

import static org.jenkinsci.test.acceptance.po.PageObject.*;

/**
 * @author Kohsuke Kawaguchi
 */
@DockerFixture(id="jira",ports=2990)
public class JiraContainer extends DockerContainer {

    private JiraRestClient restClient;

    public URL getURL() throws MalformedURLException {
        return new URL("http://" + ipBound(2990) + ':' +port(2990)+"/jira/");
    }

    /**
     * Wait until JIRA becomes up and running.
     */
    public void waitForReady(CapybaraPortingLayer p) {
        p.waitFor().withMessage("Waiting for jira to come up")
                .withTimeout(1500, TimeUnit.SECONDS) // [INFO] jira started successfully in 1064s
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        try {
                            String s = IOUtils.toString(getURL().openStream());
                            return s.contains("System Dashboard");
                        } catch (SocketException e) {
                            return null;
                        }
                    }
        });
    }

    /**
     * Creates a new issue in JIRA
     */
    public void createIssue(String key, String summary) throws IOException {
        connect();
        final IssueRestClient issueClient = restClient.getIssueClient();
        final IssueInput newIssue = new IssueInputBuilder(key, 1L, summary).build();
        issueClient.createIssue(newIssue).claim();
    }

    public void createIssue(String key) throws IOException {
        createIssue(key, createRandomName());
    }

    private void connect() throws IOException {
        if (restClient==null) {
            restClient = JIRA.connect(getURL(), "admin", "admin");
        }
    }

    public List<Comment> getComments(String ticket) throws IOException {
        final IssueRestClient issueClient = restClient.getIssueClient();
        final Issue issue = issueClient.getIssue(ticket).claim();
        final Iterable<Comment> comments = issue.getComments();
        return IteratorUtils.toList(comments.iterator());
    }

}
