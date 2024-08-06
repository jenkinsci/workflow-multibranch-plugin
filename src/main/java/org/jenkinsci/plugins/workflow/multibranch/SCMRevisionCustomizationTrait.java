package org.jenkinsci.plugins.workflow.multibranch;

import hudson.model.TaskListener;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

public abstract class SCMRevisionCustomizationTrait extends SCMSourceTrait {

    /** Customize the SCMRevision. */
    public abstract SCMRevision customize(WorkflowRun build, TaskListener listener);

    /** If multiple traits of this type are found, the highest precedence will be taken. */
    public abstract int getPrecedence();
}
