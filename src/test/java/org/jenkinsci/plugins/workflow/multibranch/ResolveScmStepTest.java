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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ResolveScmStepTest {

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    @BeforeEach
    void setUp() throws Exception {
        for (TopLevelItem i : j.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    void given_existingHeadName_when_invoked_then_existingHeadNameReturned() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("repo");
            c.createBranch("repo", "foo");
            c.addFile("repo", "foo", "Add file", "new-file.txt", "content".getBytes());
            WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow");
            job.setDefinition(new CpsFlowDefinition("node {\n"
                    + "  def tests = resolveScm source: mockScm(controllerId:'"
                    + c.getId()
                    + "', repository:'repo', traits: [discoverBranches()]), "
                    + "targets:['foo']\n"
                    + "  checkout tests\n"
                    + "  if (!fileExists('new-file.txt')) { error 'wrong branch checked out' }\n"
                    + "}", true));
            j.buildAndAssertSuccess(job);
        }
    }

    @Test
    void given_nonExistingHeadName_when_invokedIgnoringErrors_then_nullReturned() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("repo");
            c.createBranch("repo", "foo");
            WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow");
            job.setDefinition(new CpsFlowDefinition("node {\n"
                    + "  def tests = resolveScm source: mockScm(controllerId:'"
                    + c.getId()
                    + "', repository:'repo', traits: [discoverBranches()]), "
                    + "targets:['bar'], ignoreErrors: true\n"
                    + "  if (tests != null) { error \"resolved as ${tests}\"}\n"
                    + "}", true));
            j.buildAndAssertSuccess(job);
        }
    }

    @Test
    void given_nonExistingHeadName_when_invoked_then_abortThrown() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("repo");
            c.createBranch("repo", "foo");
            WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow");
            job.setDefinition(new CpsFlowDefinition("node {\n"
                    + "  def ok = true\n"
                    + "  try {\n"
                    + "  def tests = resolveScm source: mockScm(controllerId:'"
                    + c.getId()
                    + "', repository:'repo', traits: [discoverBranches()]), "
                    + "targets:['bar']\n"
                    + "    ok = false\n"
                    + "  } catch (e) {}\n"
                    + "  if (!ok) { error 'abort not thrown' }\n"
                    + "}", true));
            j.buildAndAssertSuccess(job);
        }
    }

    @Test
    void given_nonExistingHeadName_when_invokedWithDefault_then_defaultReturned() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("repo");
            c.createBranch("repo", "manchu");
            c.addFile("repo", "manchu", "Add file", "new-file.txt", "content".getBytes());
            WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow");
            job.setDefinition(new CpsFlowDefinition("node {\n"
                    + "  def tests = resolveScm source: mockScm(controllerId:'"
                    + c.getId()
                    + "', repository:'repo', traits: [discoverBranches()]), "
                    + "targets:['bar', 'manchu']\n"
                    + "  checkout tests\n"
                    + "  if (!fileExists('new-file.txt')) { error 'wrong branch checked out' }\n"
                    + "}", true));
            j.buildAndAssertSuccess(job);
        }
    }
}
