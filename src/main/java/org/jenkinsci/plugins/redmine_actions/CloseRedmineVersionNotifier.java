package org.jenkinsci.plugins.redmine_actions;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.*;
import hudson.*;
import hudson.tasks.*;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


public class CloseRedmineVersionNotifier extends Notifier {
    private final String projectKey;
    private final String versionNameFormat;
    private final boolean closeVersion;
    private final int modifyTicketIssueStatusId;
    private final boolean updateDescription;
    private final boolean setCustomFields;
    private final String customFieldsToSet;

    // Build-time class properties
    private transient int projectId;
    private transient RedmineManager redmineManager;
    private transient PrintStream logger;

    @DataBoundConstructor
    public CloseRedmineVersionNotifier(String projectKey, String versionNameFormat, boolean closeVersion,
                                       String modifyTicketIssueStatusId, boolean updateDescription,
                                       CustomFieldsToSet setCustomFields) {
        this.projectKey = projectKey;
        this.versionNameFormat = versionNameFormat;
        this.closeVersion = closeVersion;
        this.modifyTicketIssueStatusId = Integer.parseInt(modifyTicketIssueStatusId);
        this.updateDescription = updateDescription;
        this.setCustomFields = (setCustomFields != null);
        this.customFieldsToSet = (setCustomFields == null ? "" : setCustomFields.customFieldsToSet);
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getVersionNameFormat() {
        return versionNameFormat;
    }

    public boolean isCloseVersion() {
        return closeVersion;
    }

    public int getModifyTicketIssueStatusId() {
        return modifyTicketIssueStatusId;
    }

    public boolean isUpdateDescription() {
        return updateDescription;
    }

    public boolean isSetCustomFields() {
        return setCustomFields;
    }

    public String getCustomFieldsToSet() {
        return customFieldsToSet;
    }

    public RedmineManager getRedmineManager() {
        if (redmineManager == null) {
            redmineManager = RedmineConfig.getGlobalDescriptor().getRedmineManagerInstance();
        }
        return redmineManager;
    }

    private void writeLineToLog(String format, Object ... args) {
        logger.printf("[close-redmine-version] " + format, args);
        logger.println();
    }

    private Version findVersionByName(String versionName) throws RedmineException {
        List<Version> versions = getRedmineManager().getVersions(projectId);
        for (Version v : versions) {
            if (v.getName().equals(versionName)) {
                return v;
            }
        }
        return null;
    }

    private List<Issue> findIssuesByVersion(Integer versionId) throws RedmineException {
        // Create a filter to filter for only the issues that are relevant to the current version
        HashMap<String, String> searchParameters = new HashMap<String, String>();
        searchParameters.put("project_id", Integer.toString(projectId));
        searchParameters.put("status_id", "*");
        searchParameters.put("fixed_version_id", versionId.toString());
        return getRedmineManager().getIssues(searchParameters);
    }

    private void updateCustomFieldsFromString(List<CustomField> customFields, String udpateText)
            throws RedmineException {
        updateCustomFieldsFromString(customFields, udpateText, null);
    }

    private CustomFieldDefinition getCustomFieldDefinitionByName(List<CustomFieldDefinition> customFieldDefinitions,
                                                                 String name) {
        for (CustomFieldDefinition cfd : customFieldDefinitions) {
            if (cfd.getName().equals(name)) {
                return cfd;
            }
        }
        return null;
    }

    /**
     * Update a custom fields list given a string containing line separated key value pairs.
     * Any environment variables contained in the values will be expanded - provided an environment
     * is given.
     */
    private void updateCustomFieldsFromString(List<CustomField> customFields, String udpateText,
                                                           EnvVars envs) throws RedmineException {
        if (customFields.size() > 0) {

            try {
                // Parse the `updateText` so it can be iterated over
                Properties p = new Properties();
                p.load(new StringReader(udpateText));

                if (p.size() > 0) {
                    // Get the custom fields available for all models
                    List<CustomFieldDefinition> customFieldDefinitions = getRedmineManager().getCustomFieldDefinitions();

                    // Iterate over the `customFields` and update them
                    for (CustomField f : customFields) {
                        String newValue = p.getProperty(f.getName());
                        if (newValue != null) { // Found a custom field to update
                            newValue = envs.expand(newValue);
                            // Get the definition for the custom field
                            CustomFieldDefinition def = getCustomFieldDefinitionByName(customFieldDefinitions, f.getName());
                            // Check the custom field type (currently only supporting `date` OR a string value)
                            if (def.getFieldFormat().equals("date")) {
                                String date = conformToRedmineDate(newValue);
                                if (date == null)
                                    writeLineToLog("Could not conform date to YYYY-MM-DD: \"%s\"", newValue);
                                else
                                    newValue = date;
                            }
                            f.setValue(newValue);
                        }
                    }
                } else
                    writeLineToLog("No custom properties to set!");
            } catch(IOException e) {
                writeLineToLog("Could not parse the custom properties to add - with error: \"%s\"", e.getMessage());
            }
        }
    }

    /**
     * Attempt to conform an unknown date time string to the ISO8601 format, as used in
     * Redmine `date` custom fields. Only accept the `YYYY-MM-DD` sub-format of ISO8601.
     *
     * @param dateTime The date time format to conform
     * @return String The correctly formatted date, or null if it doesn't conform
     */
    private String conformToRedmineDate(String dateTime) {
        String dateToConform = dateTime;
        // Remove the time fields appended to the Jenkins environment variable `BUILD_ID`
        if (dateToConform.length() > 10)
            dateToConform = dateToConform.substring(0, 10);
        try {
            Date date = new SimpleDateFormat("YYYY-MM-DD", Locale.ENGLISH).parse(dateToConform);
        } catch (ParseException e) {
            return null;
        }
        return dateToConform;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws AbortException, InterruptedException, IOException {
        RedmineManager mgr = getRedmineManager();
        try {
            // Fill in build-time properties
            this.logger = listener.getLogger();
            this.projectId = mgr.getProjectByKey(projectKey).getId();
            // Perform functions hitting the Redmine API  - so any exceptions can be caught and handled here
            this.action(build, launcher, listener);
        } catch (RedmineException e) {
            throw new AbortException("Error communicating with Redmine: " + e.getMessage());
        }
        return true;
    }

    /**
     * Wrapper for any actions involving the Redmine instance.
     *
     * @see this.perform
     * @throws RedmineException
     * @throws AbortException
     * @throws InterruptedException
     * @throws IOException
     */
    private void action(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws RedmineException, AbortException, InterruptedException, IOException {
        RedmineManager mgr = getRedmineManager();
        writeLineToLog("Current User: %s", mgr.getCurrentUser().toString());
        try {
            // Expand any environment variables within the version name string
            EnvVars envs = build.getEnvironment(listener);
            // Format the version name and look it up in Redmine
            String versionName = String.format(envs.expand(versionNameFormat), build.number);
            Version currentVersion = findVersionByName(versionName);

            // Check that the current version actually exists
            if (currentVersion != null) {

                // Modify the issues statuses associated to the version - but only if the version is still open
                if (modifyTicketIssueStatusId != -1 && currentVersion.getStatus().equals("open")) {
                    List<Issue> versionedIssues = findIssuesByVersion(currentVersion.getId());
                    if (versionedIssues.size() > 0) {
                        writeLineToLog("Found %d issues in \"%s\"", versionedIssues.size(), versionName);

                        // Close each of the issues
                        for (Issue i : versionedIssues) {
                            i.setStatusId(modifyTicketIssueStatusId);
                            mgr.update(i);
                        }
                    } else
                        writeLineToLog("No issues found in \"%s\"", versionName);
                }

                // Close the version and update its description
                currentVersion.setStatus("closed");
                if (updateDescription) {
                    String desc = currentVersion.getDescription();
                    // Check for a description header
                    if (!desc.startsWith("Jenkins job:"))
                        desc = "Jenkins job \"" + build.getProject().getDisplayName() + "\" builds: ";
                    else
                        desc += " // ";
                    // Append the build date/number onto the description
                    desc += String.format("#%d at %s", build.getNumber(), build.getTime().toString());
                    currentVersion.setDescription(desc);
                    writeLineToLog("Version \"%s\" set to closed and description updated", versionName);
                }

                if (setCustomFields)
                    updateCustomFieldsFromString(currentVersion.getCustomFields(), customFieldsToSet, envs);

                mgr.update(currentVersion);
            } else
                writeLineToLog("No version found for \"%s\"", versionName);
        } catch (IllegalFormatException e) {
            throw new AbortException("Version Format is invalid: " + e.getMessage());
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService()
    {
        // No external synchronization is performed on this build step
        return BuildStepMonitor.NONE;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public ListBoxModel doFillProjectKeyItems() {
            ListBoxModel l = new ListBoxModel();
            RedmineManager mgr = RedmineConfig.getGlobalDescriptor().getRedmineManagerInstance();
            try {
                List<Project> projects = mgr.getProjects();
                for (Project p : projects) {
                    l.add(p.getName(), p.getIdentifier());
                }
            } catch(RedmineException e) {
                // TODO: Alert the user that something failed
            }
            return l;
        }

        public ListBoxModel doFillModifyTicketIssueStatusIdItems() {
            ListBoxModel l = new ListBoxModel();
            l.add("Do Nothing", "-1");
            RedmineManager mgr = RedmineConfig.getGlobalDescriptor().getRedmineManagerInstance();
            try {
                List<IssueStatus> statuses = mgr.getStatuses();
                for (IssueStatus s : statuses) {
                    l.add(s.getName(), s.getId().toString());
                }
            } catch(RedmineException e) {
                // TODO: Alert the user that something failed
            }
            return l;
        }

        public FormValidation doCheckVersionNameFormat(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) != null && value.length() >= 2) {
                // Check that we have at least one "%d"
                if (value.contains("%d") || value.contains("$")) {
                    // Give the format string a whirl
                    try {
                        String exampleVersionName = String.format(value, 1);
                        return FormValidation.ok("It works goood! Example: " + exampleVersionName);
                    } catch (IllegalFormatException e) {
                        return FormValidation.error("Formatting the string failed with: \"" + e.getMessage() + "\"");
                    }
                } else
                    return FormValidation.error("No \"%d\" format placeholder found OR any environment variables!");
            } else
                return FormValidation.error("Empty version name format OR too short!");
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        public String getDisplayName() {
            return "Close Redmine version";
        }
    }

    public static class CustomFieldsToSet {
        private final String customFieldsToSet;

        @DataBoundConstructor
        public CustomFieldsToSet(String customFieldsToSet) {
            this.customFieldsToSet = customFieldsToSet;
        }

        public String getCustomFieldsToSet() {
            return customFieldsToSet;
        }
    }
}

