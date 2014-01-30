# Jenkins Redmine Actions Plugin

## Description
Jenkins plugin for build steps to integrate with Redmine.

## Manually Installing
 1. Clone this repo.
 2. Build and install my forked [redmine-java-api](https://github.com/justnom/redmine-java-api) into your Maven local repository.
 3. Run ``mvn clean package``. 
 4. Go to Jenkins in a web browser. 
 5. Click on *"Manage Jenkins"*, select *"Manage Plugins"*. 
 6. Click on the *"Advanced"* tab then upload the file `target/redmine-actions.hpi` under the *"Upload Plugin"* section.


