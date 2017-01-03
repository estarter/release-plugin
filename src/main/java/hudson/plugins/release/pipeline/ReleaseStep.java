package hudson.plugins.release.pipeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
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
import hudson.Launcher;
import hudson.console.ModelHyperlinkNote;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Environment;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.release.ReleaseWrapper;
import hudson.plugins.release.SafeParametersAction;
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

    public static class Execution extends AbstractStepExecutionImpl {
        @StepContextParameter private transient Run<?,?> invokingRun;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient TaskListener listener;

        @Inject(optional=true) transient ReleaseStep step;

        private List<ParameterValue> getDefaultParametersValues(AbstractProject project) {
            ParametersDefinitionProperty paramDefProp = (ParametersDefinitionProperty) project.getProperty(ParametersDefinitionProperty.class);
            ArrayList<ParameterValue> defValues = new ArrayList<ParameterValue>();

            /*
             * This check is made ONLY if someone will call this method even if isParametrized() is false.
             */
            if(paramDefProp == null)
                return defValues;

            /* Scan for all parameter with an associated default values */
            for(ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions()) {
                ParameterValue defaultValue = paramDefinition.getDefaultParameterValue();

                if(defaultValue != null)
                    defValues.add(defaultValue);
            }

            return defValues;
        }

        @Override
        public boolean start() throws Exception {
            // StepContext context = getContext();
            if (step.getJob() == null) {
                throw new AbortException("Job name is not defined.");
            }

            // hudson.maven.MavenModuleSet
            final AbstractProject project = Jenkins.getActiveInstance().getItem(step.getJob(), invokingRun.getParent(), AbstractProject.class);
            if (project == null) {
                throw new AbortException("No parametrized job named " + step.getJob() + " found");
            }
            listener.getLogger().println("Releasing project: " + ModelHyperlinkNote.encodeTo(project));

            listener.getLogger().println("Project: " + project.getClass().getName());



            ReleaseWrapper wrapper = new ReleaseWrapper();
/*
            Collection<? extends Action> projectActions = wrapper.getProjectActions(project);
            Iterator<? extends Action> it = projectActions.iterator();
            while (it.hasNext()) {
                Action action = it.next();
                listener.getLogger().println("action : " + action.getClass().getName());
            }
*/

            // Environment env = wrapper.setUp((AbstractBuild) invokingRun.getNextBuild(), launcher, (BuildListener) listener);

            List<ParameterValue> paramValues = getDefaultParametersValues(project);
            project.scheduleBuild(0, new Cause.UserIdCause(),
                    new ReleaseWrapper.ReleaseBuildBadgeAction(),
                    new SafeParametersAction(paramValues));




/*
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
*/

            return false;
        }

        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {
            getContext().onFailure(cause);
        }

        private static final long serialVersionUID = 1L;
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
            return "Trigger release for the job";
        }
    }


}
