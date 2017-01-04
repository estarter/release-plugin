package hudson.plugins.release.pipeline;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import hudson.model.FreeStyleProject;

/**
 * Created by e3cmea on 1/4/17.
 *
 * @author Alexey Merezhin
 */
public class ReleaseStepTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule j = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule();


    @Test
    public void releaseFreeStyleProject() throws Exception {
        FreeStyleProject ds = j.createFreeStyleProject("ds");
        WorkflowJob us = j.jenkins.createProject(WorkflowJob.class, "us");
        us.setDefinition(new CpsFlowDefinition("release 'ds'\n echo \"release's done\"", true));

        // WARNING: Failed to instantiate optional component org.jenkinsci.plugins.workflow.steps.scm.SubversionStep$DescriptorImpl; skipping
        // Stacktrace: org.jenkinsci.plugins.workflow.steps.MissingContextVariableException: Required context class hudson.Launcher is missing

        j.assertLogContains("release's done", j.buildAndAssertSuccess(us));
        // TODO JENKINS-28673 assert no warnings, as in StartupTest.noWarnings
        // (but first need to deal with `WARNING: Failed to instantiate optional component org.jenkinsci.plugins.workflow.steps.scm.SubversionStep$DescriptorImpl; skipping`)
        ds.getBuildByNumber(1).delete();
    }

}