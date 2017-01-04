package hudson.plugins.release.pipeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import hudson.model.ParametersAction;
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
    private static final Logger LOGGER = Logger.getLogger(ReleaseStep.class.getName());

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
            if (step.getJob() == null) {
                throw new AbortException("Job name is not defined.");
            }

            final AbstractProject project = Jenkins.getActiveInstance().getItem(step.getJob(), invokingRun.getParent(), AbstractProject.class);
            if (project == null) {
                throw new AbortException("No parametrized job named " + step.getJob() + " found");
            }
            listener.getLogger().println("Releasing project: " + ModelHyperlinkNote.encodeTo(project));


            List<Action> actions = new ArrayList<>();

            StepContext context = getContext();
            actions.add(new ReleaseTriggerAction(context));
            LOGGER.log(Level.FINER, "scheduling a release of {0} from {1}", new Object[] {project, context});

/*
            List<ParameterValue> parameters = step.getParameters();
            if (parameters != null) {
                parameters = completeDefaultParameters(parameters, (Job) project);
                actions.add(new ParametersAction(parameters));
            }
*/
            actions.add(new SafeParametersAction(getDefaultParametersValues(project)));

            actions.add(new ReleaseWrapper.ReleaseBuildBadgeAction());


            QueueTaskFuture<?> task = project.scheduleBuild2(0, new Cause.UpstreamCause(invokingRun), actions);
            if (task == null) {
                throw new AbortException("Failed to trigger build of " + project.getFullName());
            }

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
