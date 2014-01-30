package org.jenkinsci.plugins.redmine_actions.RedmineConfig

f=namespace(lib.FormTagLib)

f.section(title:"Redmine Server Configuration") {
    f.block {
        f.entry(title:"URL", field:"url") {
            f.textbox()
        }
        f.entry(title:"API Key", field:"apiKey") {
            f.textbox()
        }
        f.validateButton(method:"validate", with:"url,apiKey", title:"Test Settings")
    }
}