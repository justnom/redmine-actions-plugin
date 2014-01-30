package org.jenkinsci.plugins.redmine_actions;


import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Project;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.List;

/**
 * Stores the global Redmine configuration - the descriptor is only shown on the `Configure System` page.
 */
public class RedmineConfig extends AbstractDescribableImpl<RedmineConfig> {
    public static DescriptorImpl getGlobalDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RedmineConfig> {
        private String url;
        private String apiKey;

        public DescriptorImpl() {
            load();
        }

        public String getUrl() {
            return url;
        }

        public String getApiKey() {
            return apiKey;
        }

        /**
         * Get an authenticated instance of the Redmine manager.
         *
         * @return RedmineManager
         */
        public RedmineManager getRedmineManagerInstance() {
            return new RedmineManager(url, apiKey);
        }

        @Override
        public String getDisplayName() {
            return "Redmine Server Configuration";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            url = json.getString("url");
            apiKey = json.getString("apiKey");
            save();
            return true;
        }

        public FormValidation doValidate(@QueryParameter String url, @QueryParameter String apiKey) {
            RedmineManager redmineManager = new RedmineManager(url, apiKey);
            try {
                List<Project> projects = redmineManager.getProjects();
            } catch (RedmineException e) {
                return FormValidation.error("There was an error: %s", e.getMessage());
            }
            return FormValidation.ok("Everything is working correctly!");
        }
    }
}
