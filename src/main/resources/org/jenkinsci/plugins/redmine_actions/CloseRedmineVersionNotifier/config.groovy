package org.jenkinsci.plugins.redmine_actions.CloseRedmineVersionNotifier

f=namespace(lib.FormTagLib)

f.entry(title:"Project", field:"projectKey") {
    f.select()
}
f.entry(title:"Version Format", field:"versionNameFormat") {
    f.textbox(default: "build-%d")
}
f.entry(title:"Change version status", field:"modifyTicketIssueStatusId") {
    f.select()
}
f.entry(title:"Update description", field:"updateDescription") {
    f.checkbox(default: true)
}
f.optionalBlock(title:"Set custom fields on version", field:"setCustomFields", default: false) {
    f.entry(title:"Custom Fields", field:"customFieldsToSet") {
        f.textarea()
    }
}
