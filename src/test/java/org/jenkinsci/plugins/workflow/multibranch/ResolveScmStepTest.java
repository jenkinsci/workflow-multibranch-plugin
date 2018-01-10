/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.jenkinsci.plugins.workflow.multibranch;

import hudson.model.TopLevelItem;
import jenkins.scm.impl.mock.MockSCMController;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ResolveScmStepTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : j.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    public void given_existingHeadName_when_invoked_then_existingHeadNameReturned() throws Exception {
        MockSCMController c = MockSCMController.create();
        try {
            c.createRepository("repo");
            c.createBranch("repo", "foo");
            c.addFile("repo", "foo", "Add file", "new-file.txt", "content".getBytes());
            WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow");
            job.setDefinition(new CpsFlowDefinition("node {\n"
                    + "  def tests = resolveScm(source: [$class: 'jenkins.scm.impl.mock.MockSCMSource', controllerId:'"
                    + c.getId()
                    + "', repository:'repo', includeBranches:true, includeTags:true, includeChangeRequests:true], "
                    + "targets:['foo'])\n"
                    + "  checkout tests\n"
                    + "  if (!fileExists('new-file.txt')) { error 'wrong branch checked out' }\n"
                    + "}"));
            j.buildAndAssertSuccess(job);
        } finally {
            c.close();
        }
    }

    @Test
    public void given_nonExistingHeadName_when_invokedIgnoringErrors_then_nullReturned() throws Exception {
        MockSCMController c = MockSCMController.create();
        try {
            c.createRepository("repo");
            c.createBranch("repo", "foo");
            WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow");
            job.setDefinition(new CpsFlowDefinition("node {\n"
                    + "  def tests = resolveScm(source: [$class: 'jenkins.scm.impl.mock.MockSCMSource', controllerId:'"
                    + c.getId()
                    + "', repository:'repo', includeBranches:true, includeTags:true, includeChangeRequests:true], "
                    + "targets:['bar'], ignoreErrors: true)\n"
                    + "  if (tests != null) { error \"resolved as ${tests}\"}\n"
                    + "}"));
            j.buildAndAssertSuccess(job);
        } finally {
            c.close();
        }
    }

    @Test
    public void given_nonExistingHeadName_when_invoked_then_abortThrown() throws Exception {
        MockSCMController c = MockSCMController.create();
        try {
            c.createRepository("repo");
            c.createBranch("repo", "foo");
            WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow");
            job.setDefinition(new CpsFlowDefinition("node {\n"
                    + "  def ok = true\n"
                    + "  try {\n"
                    + "    def tests = resolveScm(source: [$class: 'jenkins.scm.impl.mock.MockSCMSource', "
                    + "controllerId:'"
                    + c.getId()
                    + "', repository:'repo', includeBranches:true, includeTags:true, includeChangeRequests:true], "
                    + "targets:['bar'])\n"
                    + "    ok = false\n"
                    + "  } catch (e) {}\n"
                    + "  if (!ok) { error 'abort not thrown' }\n"
                    + "}"));
            j.buildAndAssertSuccess(job);
        } finally {
            c.close();
        }
    }

    @Test
    public void given_nonExistingHeadName_when_invokedWithDefault_then_defaultReturned() throws Exception {
        MockSCMController c = MockSCMController.create();
        try {
            c.createRepository("repo");
            c.createBranch("repo", "manchu");
            c.addFile("repo", "manchu", "Add file", "new-file.txt", "content".getBytes());
            WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow");
            job.setDefinition(new CpsFlowDefinition("node {\n"
                    + "  def tests = resolveScm(source: [$class: 'jenkins.scm.impl.mock.MockSCMSource', controllerId:'"
                    + c.getId()
                    + "', repository:'repo', includeBranches:true, includeTags:true, includeChangeRequests:true], targets:['bar', 'manchu'])\n"
                    + "  checkout tests\n"
                    + "  if (!fileExists('new-file.txt')) { error 'wrong branch checked out' }\n"
                    + "}"));
            j.buildAndAssertSuccess(job);
        } finally {
            c.close();
        }
    }
}
