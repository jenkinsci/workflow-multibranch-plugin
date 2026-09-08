package org.jenkinsci.plugins.workflow.multibranch;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.User;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

public class ResolveScmStep2Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public WireMockRule wireMock = new WireMockRule(options().dynamicPort());

    @Test
    public void systemScopedCredentialIsNotUsable() throws Exception {
        addCredential(CredentialsScope.SYSTEM, "leak-git-cred", "systemUser", "systemPass");
        grantJobPermissions("developer");
        replyToGitWithBasicAuthChallenge();

        var run = runResolveScm("developer", "leak-git-cred");
        System.out.println(JenkinsRule.getLog(run));

        assertThat(credentialsSentToRemote(), not(hasItem("systemUser:systemPass")));
    }

    @Test
    public void systemScopedCredentialIsNotUsableEvenWhenBuildRunsAsSystem() throws Exception {
        addCredential(CredentialsScope.SYSTEM, "leak-git-cred", "systemUser", "systemPass");
        replyToGitWithBasicAuthChallenge();

        var run = runResolveScm(null, "leak-git-cred");
        System.out.println(JenkinsRule.getLog(run));

        assertThat(credentialsSentToRemote(), not(hasItem("systemUser:systemPass")));
    }

    @Test
    public void globalScopedCredentialIsUsable() throws Exception {
        addCredential(CredentialsScope.GLOBAL, "shared-git-cred", "globalUser", "globalPass");
        grantJobPermissions("trusted");
        grantUseItem("trusted");
        replyToGitWithBasicAuthChallenge();

        var run = runResolveScm("trusted", "shared-git-cred");
        System.out.println(JenkinsRule.getLog(run));

        assertThat(credentialsSentToRemote(), hasItem("globalUser:globalPass"));
    }

    private void addCredential(CredentialsScope scope, String id, String username, String password) throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new UsernamePasswordCredentialsImpl(scope, id, id, username, password));
        SystemCredentialsProvider.getInstance().save();
    }

    private void grantJobPermissions(String user) {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        var strategy = currentStrategy();
        strategy.grant(Jenkins.READ).everywhere().to(user);
        strategy.grant(Item.READ, Item.BUILD, Item.CONFIGURE).everywhere().to(user);
        strategy.grant(Computer.BUILD).everywhere().to(user);
        j.jenkins.setAuthorizationStrategy(strategy);
    }

    private void grantUseItem(String user) {
        currentStrategy().grant(CredentialsProvider.USE_ITEM).everywhere().to(user);
    }

    private MockAuthorizationStrategy currentStrategy() {
        if (j.jenkins.getAuthorizationStrategy() instanceof MockAuthorizationStrategy existing) {
            return existing;
        }
        return new MockAuthorizationStrategy();
    }

    private void replyToGitWithBasicAuthChallenge() {
        wireMock.stubFor(WireMock.any(anyUrl()).willReturn(aResponse()
                .withStatus(401)
                .withHeader("WWW-Authenticate", "Basic realm=\"Git\"")));
    }

    private WorkflowRun runResolveScm(String asUser, String credentialsId) throws Exception {
        var job = j.jenkins.createProject(WorkflowJob.class, "job-" + (asUser == null ? "system" : asUser));
        job.setDefinition(new CpsFlowDefinition("""
                node {
                  resolveScm source: gitSource(
                      remote: '%s/repo.git',
                      credentialsId: '%s'
                  ), targets: ['master'], ignoreErrors: true
                }
                """.formatted(wireMock.baseUrl(), credentialsId), true));
        if (asUser != null) {
            runBuildsAs(job, asUser);
        }
        return job.scheduleBuild2(0).get();
    }

    private void runBuildsAs(WorkflowJob job, String user) {
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators()
                .add(new MockQueueItemAuthenticator().authenticate(job.getFullName(), User.getById(user, true).impersonate2()));
    }

    private List<String> credentialsSentToRemote() {
        return wireMock.findAll(anyRequestedFor(anyUrl())).stream()
                .map(r -> r.getHeader("Authorization"))
                .filter(value -> value != null && value.startsWith("Basic "))
                .map(value -> new String(Base64.getDecoder()
                        .decode(value.substring("Basic ".length()).trim()), StandardCharsets.UTF_8))
                .collect(Collectors.toList());
    }
}
