package org.jenkinsci.plugins.workflow.multibranch;

import hudson.Extension;
import hudson.model.TaskListener;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/** Sample {@link SCMRevisionCustomizationTrait} that has a fixed revision to build. Git only in this example. */
public class FixedSCMRevisionCustomizationTrait extends SCMRevisionCustomizationTrait {

    private final SCMSource scmSource;
    private final SCMRevision revision;

    FixedSCMRevisionCustomizationTrait(SCMSource scmSource, SCMRevision revision) {
        this.scmSource = scmSource;
        this.revision = revision;
    }

    @Override
    public SCMRevision customize(WorkflowRun build, TaskListener listener) {
        build.addAction(new SCMRevisionAction(scmSource, revision));
        return revision;
    }

    @Override
    public int getPrecedence() {
        return 0;
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitSCMSourceContext.class;
        }

        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitSCMSource.class;
        }

        @Override
        public Class<? extends SCMBuilder> getBuilderClass() {
            return GitSCMBuilder.class;
        }
    }
}
