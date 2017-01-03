package hudson.plugins.release.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.inject.Inject;

import hudson.AbortException;
import hudson.Extension;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import jenkins.YesNoMaybe;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;

/**
 * Created by e3cmea on 1/3/17.
 *
 * @author Alexey Merezhin
 */
public class ReleaseStep extends AbstractStepImpl {

    private String job;

    @DataBoundConstructor
    public ReleaseStep(String job) {
        this.job = job;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public static class Execution extends AbstractSynchronousStepExecution {
        @StepContextParameter private transient TaskListener listener;
        @StepContextParameter private transient Run<?,?> invokingRun;

        @Inject(optional=true) transient ReleaseStep step;

        @Override
        protected Void run() throws Exception {
            // StepContext context = getContext();
            if (step.getJob() == null) {
                throw new AbortException("Job name is not defined.");
            }
            final ParameterizedJobMixIn.ParameterizedJob project = Jenkins.getActiveInstance().getItem(step.getJob(), invokingRun.getParent(), ParameterizedJobMixIn.ParameterizedJob.class);
            if (project == null) {
                throw new AbortException("No parametrized job named " + step.getJob() + " found");
            }
            listener.getLogger().println("Releasing project: " + ModelHyperlinkNote.encodeTo(project));

            List<Action> actions = new ArrayList<Action>();
            actions.add(new CauseAction(new Cause.UpstreamCause(invokingRun)));
            QueueTaskFuture<?> f = new ParameterizedJobMixIn() {
                @Override protected Job asJob() {
                    return (Job) project;
                }
            }.scheduleBuild2(0, actions.toArray(new Action[]{}));
            if (f == null) {
                throw new AbortException("Failed to trigger build of " + project.getFullName());
            }

            return null;
        }
    }

    @Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "release";
        }

        @Override
        public String getDisplayName() {
            return "release";
        }
    }

}
